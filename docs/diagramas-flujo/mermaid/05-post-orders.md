```mermaid
graph LR
    classDef start_end fill:#000,stroke:#000,color:#fff,stroke-width:2px;
    classDef process fill:#fff,stroke:#000,stroke-width:1px;
    classDef decision fill:#fff,stroke:#000,stroke-width:1px;
    classDef success fill:#d4edda,stroke:#28a745,stroke-width:1px,color:#155724;
    classDef error fill:#f8d7da,stroke:#dc3545,stroke-width:1px,color:#721c24;

    subgraph Header ["POST /api/v1/orders"]
        direction LR
    end

    Start(( )) --> Payload[Payload +<br/>Idempotency-Key<br/>X-User-Id]

    Payload --> ValidaKey{Idempotency-Key<br/>presente?}
    ValidaKey -- No --> R400[RESPONSE 400<br/>VAL003 - Key Required]

    ValidaKey -- Yes --> ValidaPayload{Payload<br/>válido?}
    ValidaPayload -- No --> R400b[RESPONSE 400<br/>VAL001 - Invalid Input]

    ValidaPayload -- Yes --> BuscarIdempotencia["DynamoDB GetItem<br/>idempotency por<br/>userId#POST_ORDERS#key"]

    BuscarIdempotencia --> ExisteIdempotencia{Existe<br/>registro?}

    ExisteIdempotencia -- Yes --> ValidaHash{requestHash<br/>coincide?}
    ValidaHash -- No --> R422[RESPONSE 422<br/>VAL004 - Idempotency Mismatch]
    ValidaHash -- Yes --> BuscarOrdenExistente["DynamoDB GetItem<br/>orden existente"]
    BuscarOrdenExistente --> SuccessNode(( ))

    ExisteIdempotencia -- No --> BuscarEvento["DynamoDB GetItem<br/>events por eventId"]
    BuscarEvento --> ExisteEvento{Existe<br/>evento?}
    ExisteEvento -- No --> R404[RESPONSE 404<br/>BUS001 - Event Not Found]

    ExisteEvento -- Yes --> GenID["Generar orderId UUID<br/>expiryShard S#0..S#3<br/>expiresAt = now + 600s<br/>Construir orden RESERVED"]

    GenID --> Transaccion["DynamoDB TransactWriteItems<br/>PUT idempotency [attribute_not_exists]<br/>UPDATE inventory available -= q, reserved += q [available >= q]<br/>PUT orden [attribute_not_exists]<br/>PUT historial [attribute_not_exists]"]

    Transaccion --> Exitosa{Exitosa?}
    Exitosa -- No --> ValidaConflicto{Tipo<br/>de error?}
    ValidaConflicto -- Inventario insuficiente --> R409inv[RESPONSE 409<br/>BUS002 - Insufficient Inventory]
    ValidaConflicto -- Concurrencia --> Retry{Reintentos<br/>agotados?}
    Retry -- No --> Transaccion
    Retry -- Yes --> R409con[RESPONSE 409<br/>BUS007 - Concurrency Conflict]
    ValidaConflicto -- Otro --> R500[RESPONSE 500<br/>ERR001 - DynamoDB Error]

    Exitosa -- Yes --> EnviarSQS["SQS sendExpireOrderMessage<br/>delay = 600s<br/>fire-and-forget"]
    EnviarSQS --> SuccessNode
    SuccessNode --> R201

    subgraph RESPONSES ["RESPUESTAS"]
        R201["RESPONSE (201)<br/>{<br/>  'statusCode': '0',<br/>  'description': 'Resource created successfully',<br/>  'data': {<br/>    'orderId': '...',<br/>    'eventId': '...',<br/>    'orderType': 'PURCHASE',<br/>    'quantity': 0,<br/>    'status': 'RESERVED',<br/>    'totalAmount': 0.0,<br/>    'currency': 'COP',<br/>    'expiresAt': '...'<br/>  }<br/>}"]
        R_ERR["RESPONSE (ERROR)<br/>{<br/>  'statusCode': 'XXX',<br/>  'description': '...'<br/>}"]
    end

    R400 --> EndNode(( ))
    R400b --> EndNode
    R404 --> EndNode
    R409inv --> EndNode
    R409con --> EndNode
    R422 --> EndNode
    R500 --> EndNode

    class Start,SuccessNode,EndNode start_end;
    class Payload,BuscarIdempotencia,BuscarOrdenExistente,BuscarEvento,GenID,Transaccion,EnviarSQS process;
    class ValidaKey,ValidaPayload,ExisteIdempotencia,ValidaHash,ExisteEvento,Exitosa,ValidaConflicto,Retry decision;
    class R201 success;
    class R400,R400b,R404,R409inv,R409con,R422,R500,R_ERR error;
```
