# Documentación Técnica — App Ticketing Nequi

## 1. Planteamiento del Problema y Visión General de la Solución

El sistema legado fallaba bajo tres condiciones específicas: compras duplicadas, sobreventas y timeouts bajo alta concurrencia. Cada una mapea directamente a una decisión de diseño:

| Problema | Solución |
|---|---|
| Compras duplicadas | Header `Idempotency-Key` + escritura condicional en DynamoDB sobre la tabla de idempotencia |
| Sobreventa | `TransactWriteItems` con condición `available >= :q` — atómico, sin condición de carrera |
| Timeouts bajo carga | Pipeline no bloqueante con WebFlux, trabajo pesado delegado a SQS, circuit breakers por endpoint |

---

## 2. Decisiones de Arquitectura

### 2.1 Clean Architecture (scaffold Bancolombia)

El proyecto sigue una separación estricta de capas:

- `domain/model` — entidades Java puras, enums, interfaces de gateway. Sin dependencias de framework.
- `domain/usecase` — lógica de negocio. Solo depende de `model`. Sin Spring, sin AWS SDK.
- `infrastructure/driven-adapters` — implementaciones DynamoDB y SQS de las interfaces de gateway.
- `infrastructure/entry-points` — handlers HTTP, filtros, mappers. Depende de los casos de uso vía interfaces.
- `applications/app-service` — configuración Spring Boot, beans `@Bean`, `application.yaml`.

**Por qué importa:** Los casos de uso son testeables con Mockito puro — sin contexto Spring, sin LocalStack, sin red. Esta es la propiedad más importante para pruebas rápidas y confiables.

### 2.2 Reserva Síncrona + Finalización Asíncrona (trade-off RF3)

El requerimiento indica "encolar inmediatamente y dejar que el consumidor valide disponibilidad". Se adoptó un enfoque diferente: **reservar síncronamente, finalizar asíncronamente**.

- `POST /orders` → reserva inventario atómicamente en DynamoDB, retorna `RESERVED` (HTTP 201) de inmediato.
- `POST /orders/{id}/confirm` → transiciona a `PENDING_CONFIRMATION`, encola `FINALIZE_ORDER` en SQS (HTTP 202).
- Consumidor SQS → cobra el pago, transiciona a `SOLD` o `CANCELLED`.

**Por qué:** La reserva síncrona le da al cliente retroalimentación inmediata sobre disponibilidad (409 si no hay stock). La finalización asíncrona maneja el trabajo pesado (pago) sin bloquear el hilo HTTP. Es un trade-off deliberado: máquina de estados ligeramente más compleja, pero mucho mejor experiencia de usuario al confirmar disponibilidad al instante.

**Justificación:** El requerimiento indica "el consumidor valida disponibilidad y actualiza inventario". En esta implementación, el consumidor actualiza el estado de la orden y el inventario ya quedó reservado en la fase síncrona. El trabajo del consumidor es el procesamiento del pago, que corresponde al paso más costoso del flujo.

### 2.3 Máquina de Estados de la Orden

Los estados son conscientes de su terminalidad y semánticamente correctos:

```
RESERVED → PENDING_CONFIRMATION → SOLD        (camino feliz)
RESERVED → CANCELLED                           (cancelación del usuario)
RESERVED → EXPIRED                             (plazo excedido)
PENDING_CONFIRMATION → SOLD                    (pago aprobado)
PENDING_CONFIRMATION → CANCELLED               (pago rechazado)
PENDING_CONFIRMATION → EXPIRED                 (guardia de plazo cercano)
```

`SOLD`, `COMPLIMENTARY`, `CANCELLED`, `EXPIRED` son **terminales** — sin transiciones de salida. Esto es forzado por la expresión `switch` en `ConfirmOrderUseCase`, que es exhaustiva sobre todos los valores de `TicketStatus` (forzado por el compilador, sin `default` fallthrough).

**Por qué `CANCELLED` y `EXPIRED` en lugar de `AVAILABLE`:** Una orden es una entidad de negocio con su propio ciclo de vida. Reutilizar `AVAILABLE` (un concepto de inventario) como estado de orden era semánticamente incorrecto y habría confundido los reportes, la auditoría y la lógica de la máquina de estados.

---

## 3. Concurrencia y Consistencia


### 3.1 Bloqueo Optimista — Dos Niveles

**Nivel 1 — Inventario (creación):**
```
UPDATE inventory
SET available = available - :q, reserved = reserved + :q
CONDITION: available >= :q
```
Si dos solicitudes concurrentes intentan reservar los últimos 2 tickets simultáneamente, solo una tiene éxito. La otra recibe `ConditionalCheckFailed` → mapeado a `BUS_INSUFFICIENT_INVENTORY` (HTTP 409).

**Nivel 2 — Transiciones de estado de la orden:**
```
UPDATE orders
SET status = :newStatus, version = :newVersion
CONDITION: version = :expectedVersion
```
Cada transición de estado verifica la `version` actual. Si dos procesos intentan transicionar la misma orden simultáneamente (ej., consumidor SQS + scheduler de expiración), solo uno gana. El perdedor recibe `ConditionalCheckFailed` → mapeado a `BUS_CONCURRENCY_CONFLICT` (HTTP 409) o ignorado silenciosamente para expiración (idempotente).

### 3.2 Reintento con Backoff Exponencial + Jitter

`CreateOrderUseCase` usa `reactor.util.retry.Retry.backoff()`:
- Máximo 3 reintentos
- Delay base: 50ms, máximo: 500ms
- Jitter: 50% (previene thundering herd — todos los clientes reintentando no golpean DynamoDB al mismo instante)
- Solo reintenta `BUS_CONCURRENCY_CONFLICT` — los errores de negocio y técnicos no se reintentan

### 3.3 Idempotencia

La clave de idempotencia está acotada por usuario y operación: `userId#POST_ORDERS#<client-key>`. Esto previene:
- Reintentos de red que crean órdenes duplicadas
- Ataques de replay maliciosos con la misma clave pero diferente cuerpo (retorna `VAL_IDEMPOTENCY_MISMATCH`, HTTP 422)

El registro de idempotencia tiene un TTL de 24 horas (atributo TTL de DynamoDB), por lo que expira automáticamente sin limpieza manual.

### 3.4 Expiración — Doble Seguro

Dos mecanismos independientes garantizan que las reservas expiren:

1. **Mensaje SQS diferido** — enviado al momento de la reserva con `delaySeconds = 600`. Se dispara una vez, con precisión.
2. **Scheduler sweeper** — corre cada 60s, escanea 4 shards GSI (`S#0..S#3`) buscando órdenes donde `deadlineAt <= now`. Captura lo que el mensaje SQS haya perdido (ej., mensaje perdido, DLQ, reinicio del servicio).

El diseño de 4 shards evita una partición caliente en el GSI. Cada shard maneja ~25% de las órdenes por expirar.

**Guardia de mensajes obsoletos:** `expireOrderById` verifica que el `deadlineAt` en el mensaje coincida con el `deadlineAt` actual de la orden. Si la orden ya fue renovada (ej., confirm extendió el plazo), el mensaje se descarta silenciosamente.

---

## 4. Asincronía y Procesamiento Orientado a Eventos

### 4.1 Pipeline No Bloqueante

Cada operación es una cadena `Mono<T>` o `Flux<T>`. Sin `.block()` en ningún lugar. Los hilos del event loop de Netty nunca se bloquean — todo el I/O (DynamoDB, SQS) usa clientes SDK asíncronos (`DynamoDbAsyncClient`, `SqsAsyncClient`) envueltos en `Mono.fromFuture()`.

### 4.2 Semántica At-Least-Once de SQS

El consumidor (`SQSListenerAdapter`) solo elimina un mensaje **después** del procesamiento exitoso:

```java
processOrderAsyncUseCase.execute(orderId, traceId, sqsMessageId)
    .flatMap(ignored -> sqsAsyncClient.deleteMessage(...))  // eliminar SOLO en éxito
    .onErrorResume(e -> {
        log.error("..., will not delete for retry", e);
        return Mono.empty();  // el mensaje permanece en la cola → SQS reintenta
    })
```

Si el procesamiento falla, el mensaje vuelve a ser visible después de `VisibilityTimeout = 30s`. Tras 3 fallos (`maxReceiveCount = 3`), se mueve al DLQ para inspección manual.

### 4.3 Dos Colas SQS, Dos Responsabilidades

| Cola | Propósito | Consumidor |
|---|---|---|
| `order-processing-queue` | Finalizar pago → SOLD o CANCELLED | `ProcessOrderAsyncUseCase` |
| `order-expiration-queue` | Expirar reserva tras el plazo | `ReleaseExpiredReservationsUseCase` |

Separación de responsabilidades: los fallos de pago no afectan el procesamiento de expiración y viceversa.

---

## 5. Resiliencia y Escalabilidad

### 5.1 Circuit Breakers — Uno por Endpoint

Cada uno de los 9 endpoints tiene su propia instancia `@CircuitBreaker`, configurada vía variables de entorno:

```yaml
resilience4j.circuitbreaker.instances.createOrderCB:
  slidingWindowSize: ${CB_SLIDING_WINDOW_SIZE:10}
  failureRateThreshold: ${CB_FAILURE_RATE_THRESHOLD:50}
  recordExceptions: [TechnicalException]   # solo fallos de infra abren el CB
  ignoreExceptions: [BusinessException]    # 404, 409 no cuentan como fallos
```

Tres sobrecargas de fallback por handler:
- `CallNotPermittedException` → 503 (circuito abierto)
- `TechnicalException` → propaga el código de error específico
- `Exception` → 500 (inesperado)

### 5.2 Headers de Seguridad

Cada respuesta incluye headers HTTP reforzados vía `SecurityHeadersConfig` (WebFilter):
- `Content-Security-Policy`, `Strict-Transport-Security`, `X-Content-Type-Options`, `Cache-Control: no-store`, `Referrer-Policy`
- Header `Server` eliminado para evitar fingerprinting

### 5.3 Escalabilidad Horizontal

El diseño es stateless — cualquier número de réplicas puede correr simultáneamente:
- Las escrituras condicionales de DynamoDB previenen doble procesamiento independientemente del número de réplicas
- El `VisibilityTimeout` de SQS previene que dos consumidores procesen el mismo mensaje
- El scheduler de expiración usa escrituras condicionales, por lo que sweepers concurrentes son seguros (solo uno gana por orden)
- Las claves de idempotencia se almacenan en DynamoDB, no en memoria

### 5.4 Diseño de Tablas DynamoDB

| Tabla | Partition Key | Sort Key | GSI |
|---|---|---|---|
| `ticketing-events` | `eventId` | — | `listingKey` + `eventDate` (paginación) |
| `ticketing-inventory` | `eventId` | — | — |
| `ticketing-orders` | `orderId` | — | `expiryShard` + `deadlineAt` (barrido de expiración) |
| `ticketing-order-history` | `orderId` | `sequence` | — |
| `ticketing-idempotency` | `idempotencyKey` | — | — |

El `expiryShard` distribuye las órdenes por expirar en 4 particiones para evitar una clave caliente en el GSI.

---

## 6. Seguridad

### 6.1 Credenciales y Secretos

- Las credenciales AWS se inyectan vía variables de entorno (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`), nunca hardcodeadas.
- En producción, serían reemplazadas por roles IAM (rol de tarea ECS) — sin credenciales estáticas necesarias.
- `DynamoDBConfig` y `SQSConfig` usan `StaticCredentialsProvider` solo para desarrollo local. En producción, `DefaultCredentialsProvider` toma el rol IAM automáticamente.

### 6.2 Idempotencia como Protección contra Abuso

El mecanismo de `Idempotency-Key` previene:
- **Envíos duplicados** — misma clave + mismo cuerpo → retorna respuesta cacheada
- **Ataques de replay** — misma clave + diferente cuerpo → `VAL_IDEMPOTENCY_MISMATCH` (HTTP 422)
- **Creación de órdenes por fuerza bruta** — cada clave está acotada a `userId`, por lo que una clave del usuario A no puede ser reproducida por el usuario B

### 6.3 Autorización

El acceso basado en roles se aplica a nivel de handler vía `TraceAndSecurityFilter`:
- `X-Role: ADMIN` requerido para `POST /events` y `POST /events/{id}/complimentary`
- `X-User-Id` acota el acceso a órdenes — los usuarios solo pueden ver/cancelar sus propias órdenes
- Bypass de admin: `isAdmin = true` permite acceso entre usuarios para operaciones de soporte

En producción, `X-Role` y `X-User-Id` se extraerían de un JWT validado, no como headers de confianza plana.

### 6.4 Límites de Cantidad

`@Min(1)` en `PurchaseOrderRequestDTO.quantity` previene cantidades cero o negativas. Un máximo por orden (ej., 10 tickets) se agregaría como regla de negocio en `CreateOrderUseCase` para producción.

---

## 7. Principios SOLID y Patrones de Diseño

### Responsabilidad Única
Cada clase tiene una sola razón para cambiar:
- `CreateOrderUseCase` — solo orquesta la creación de órdenes
- `DynamoDBTicketOrderRepositoryAdapter` — solo maneja la persistencia en DynamoDB
- `ResponseHelper` — solo construye respuestas HTTP
- `ValidationHelper` — solo valida DTOs

### Abierto/Cerrado
Los nuevos casos de uso se agregan sin modificar los existentes. Las interfaces de gateway (`EventRepository`, `TicketOrderRepository`, etc.) son puntos de extensión — nuevos adaptadores pueden conectarse sin tocar la lógica de dominio.

### Sustitución de Liskov
Todos los adaptadores implementan interfaces de gateway de dominio. `MockPaymentGatewayAdapter` es sustituible por un adaptador de pago real en pruebas.

### Segregación de Interfaces
Los gateways son de grano fino: `EventRepository`, `InventoryRepository`, `TicketOrderRepository`, `OrderHistoryRepository`, `IdempotencyRepository` son interfaces separadas. Los casos de uso solo dependen de lo que necesitan.

### Inversión de Dependencias
Los casos de uso dependen de abstracciones (interfaces en `domain/model/gateways`), no de DynamoDB ni SQS. `UseCaseConfig` conecta los adaptadores concretos al inicio.

### Patrones Aplicados

| Patrón | Dónde | Por qué |
|---|---|---|
| Gateway / Repository | `domain/model/gateways` | Desacopla el dominio de la infraestructura |
| Factory Method | Métodos `@Bean` de `UseCaseConfig` | Conexión explícita, testeable |
| Strategy | Expresión switch de `ConfirmOrderUseCase` | Despacha por estado de orden sin if/else |
| Template Method | `ResponseHelper.buildErrorResponse(Throwable)` | Pipeline unificado de manejo de errores |
| Utility Class | `ResponseHelper`, `Constants` | Helpers sin estado, `@UtilityClass` previene instanciación |
| Circuit Breaker | `@CircuitBreaker` por handler | Resilience4j, una instancia por endpoint |
| Idempotencia | `CreateOrderUseCase`, `CreateComplimentaryUseCase` | Previene procesamiento duplicado |
| Bloqueo Optimista | `DynamoDBTicketOrderRepositoryAdapter` | Escrituras condicionales basadas en versión |
| Shard / Scatter-Gather | `ReleaseExpiredReservationsUseCase` | Barrido GSI de 4 shards evita partición caliente |

---

## 8. Características de Java 25

### Records
Se utilizan ampliamente en la capa de entrada (`infrastructure/entry-points/reactive-web`) para todos los Data Transfer Objects (DTOs), tanto de entrada (Requests) como de salida (Responses):
- **Requests**: `PurchaseOrderRequestDTO`, `EventRequestDTO`, `ConfirmOrderRequestDTO`, `CancelOrderRequestDTO`, `ComplimentaryRequestDTO`.
- **Responses / Estructuras auxiliares**: `PurchaseOrderResponseDTO`, `EventResponseDTO`, `OrderHistoryResponseDTO`, `AvailabilityDTO`, `OrderFailureDTO`.

**Ventajas de su uso:**
- **Inmutabilidad y semántica clara:** Los DTOs modelan portadores de datos inmutables y de solo lectura. Al usar records, el compilador genera automáticamente constructores canónicos, métodos de acceso transparentes, `equals()`, `hashCode()` y `toString()`, eliminando código repetitivo y evitando efectos secundarios.
- **Interoperabilidad:** Se combinan eficazmente con `@Builder` (Lombok) y anotaciones de validación (`@NotNull`, `@Min`, `@NotBlank`) o serialización Jackson sin sobrecarga de boilerplate.

Por otro lado, **no se usan para las entidades de dominio**, ya que estas requieren mutabilidad controlada mediante `@Builder(toBuilder = true)` de Lombok para actualizaciones inmutables fluidas en el pipeline reactivo (lo cual requeriría métodos `with*` manuales y verbosos en records).

### Expresiones Switch y Pattern Matching
**Expresiones Switch (Switch Expressions):**
Usadas en `ConfirmOrderUseCase.dispatchByStatus()` para despachar el flujo según el estado actual de la orden:

```java
return switch (order.getStatus()) {
    case PENDING_CONFIRMATION -> queueService.sendFinalizeOrderMessage(orderId, traceId).thenReturn(order);
    case SOLD                 -> Mono.just(order);
    case RESERVED             -> confirmReserved(order, orderId, paymentReference, traceId);
    case CANCELLED, EXPIRED, COMPLIMENTARY -> Mono.error(new BusinessException(BUS_INVALID_STATE_TRANSITION));
};
```

El switch actúa como una expresión que retorna un valor directamente y es **exhaustivo**: el compilador verifica que todos los valores del enum `TicketStatus` sean cubiertos. Si en el futuro se añade un nuevo estado al enum sin actualizar esta expresión, se producirá un error en tiempo de compilación, eliminando branches olvidados o necesidad de cláusulas `default`.

**Pattern Matching para `instanceof`:**
Usado en `CreateOrderUseCase.retryWhen()` para validar excepciones de conflicto concurrente:
```java
.filter(e -> e instanceof BusinessException be && be.getGeneralMessage() == GeneralMessage.BUS_CONCURRENCY_CONFLICT)
```
El pattern matching para `instanceof` enlaza la variable de patrón `be` de forma segura y directa, eliminando la necesidad de casts explícitos redundantes.

### Hilos Virtuales
**No usados — justificado:**

La aplicación usa Spring WebFlux (Project Reactor + Netty). Los hilos virtuales están diseñados para I/O bloqueante en hilos de plataforma (modelo Spring MVC). En una aplicación WebFlux:
- Todo el I/O ya es no bloqueante vía clientes SDK asíncronos
- El event loop de Netty nunca se bloquea
- Los hilos virtuales agregarían overhead sin beneficio — resuelven el mismo problema que WebFlux ya resuelve, pero a nivel de hilo en lugar del pipeline reactivo

Los hilos virtuales serían la elección correcta si esta fuera una aplicación Spring MVC (bloqueante) que necesitara escalar concurrencia de I/O sin la complejidad de la programación reactiva.

---

## 9. Limitaciones y Mejoras para Producción

### Limitaciones Actuales

| Limitación | Impacto | Solución en Producción |
|---|---|---|
| `MockPaymentGatewayAdapter` | Sin procesamiento de pago real | Reemplazar con adaptador de pasarela de pago real (Stripe, PSE, etc.) |
| `X-Role` / `X-User-Id` como headers planos | Sin autenticación real | Validar JWT en `TraceAndSecurityFilter`, extraer claims |
| Sin rate limiting | Susceptible a abuso por ráfagas | Agregar rate limiter de `spring-cloud-gateway` o AWS WAF |
| DynamoDB en región única | Sin geo-redundancia | DynamoDB Global Tables para multi-región |
| Scheduler corre en cada réplica | Barridos duplicados (seguros pero ineficientes) | Usar DynamoDB Streams + Lambda para expiración orientada a eventos |
| Sin trazabilidad distribuida | Difícil correlacionar logs entre servicios | Agregar OpenTelemetry + AWS X-Ray |
| `cors.allowed-origins` hardcodeado en compose | No específico por entorno | Inyectar vía variable de entorno por entorno |

### Qué Cambiaría en Producción

**1. Expiración orientada a eventos con DynamoDB Streams**
Reemplazar el scheduler de polling con un consumidor de DynamoDB Streams que reaccione a las eliminaciones por TTL. Sin overhead de polling, reacción instantánea, sin procesamiento duplicado entre réplicas.

**2. Patrón Outbox para confiabilidad de SQS**
Actualmente, si la app se cae después de escribir en DynamoDB pero antes de enviar a SQS, el mensaje se pierde. Una tabla outbox (escrita en la misma transacción) + un proceso relay separado garantiza entrega *at-least-once*, que combinada con el procesamiento idempotente del consumidor asegura consistencia eventual robusta.


**3. Contadores de inventario particionados para eventos calientes**
Para eventos con 100k+ compradores concurrentes, un solo ítem DynamoDB para inventario se convierte en una clave caliente. Solución: particionar el inventario en N shards (ej., 10 ítems cada uno con `totalCapacity/10`), reservar de un shard aleatorio, agregar para lecturas.

**4. AWS Secrets Manager para credenciales**
Reemplazar variables de entorno estáticas con `SecretsManagerGateway` para credenciales de base de datos, claves de pasarela de pago y claves de firma JWT. Rotación sin redespliegue.

**5. ECS Fargate + ALB + Auto Scaling Dinámico**
Configurar políticas de auto-escalado horizontal (Target Tracking / Step Scaling) basadas tanto en la tasa de peticiones y throughput en el ALB (`RequestCountPerTarget`, latencia p95) como en la profundidad de la cola SQS (`ApproximateNumberOfMessagesVisible`). Cuando el tráfico de peticiones concurrentes o la cola de procesamiento crece, el clúster escala automáticamente nuevas tareas de Fargate, reduciendo las réplicas cuando la carga desciende. Esto garantiza alta disponibilidad, elasticidad y control de costos ante picos de demanda masivos.


**6. Alertas en DLQ**
Agregar alarmas CloudWatch en `ApproximateNumberOfMessagesVisible > 0` del DLQ para disparar investigación inmediata cuando los mensajes fallan en procesamiento.

**7. Logging estructurado con correlación**
Reemplazar `LogAppenderHelper` con logging JSON estructurado (logback + logstash encoder) que incluya `traceId`, `orderId`, `userId`, `eventId` como campos indexados para consultas en CloudWatch Insights.
