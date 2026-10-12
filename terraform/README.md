# Terraform — App Ticketing Nequi

Infraestructura AWS para `app-ticketing-nequi`, organizada en tres módulos. Los mismos módulos pueden validarse localmente con Floci; sus directorios `env/*` contienen tfvars de sandbox, no credenciales ni configuración de producción.

---

## Estructura

```
terraform/
├── dynamo/          # 6 tablas DynamoDB
│   └── env/{dev,qa,pdn}/terraform-*.tfvars
├── sqs/             # 2 colas + 2 DLQs
│   └── env/{dev,qa,pdn}/terraform-*.tfvars
└── api/             # ECR, ECS, ALB, NLB, API Gateway, WAF, IAM, CloudWatch
    └── env/{dev,qa,pdn}/terraform-*.tfvars
```

---

## Recursos creados

### `dynamo/`

| Recurso | Nombre |
|---|---|
| Tabla events | `ticketing-{env}-events` |
| Tabla inventory | `ticketing-{env}-inventory` |
| Tabla seats | `ticketing-{env}-seats` (GSI `gsi-seats-by-status`) |
| Tabla orders | `ticketing-{env}-orders` (GSI `gsi-orders-deadline`) |
| Tabla order-history | `ticketing-{env}-order-history` |
| Tabla idempotency | `ticketing-{env}-idempotency` (TTL) |

### `sqs/`

| Recurso | Nombre |
|---|---|
| Cola procesamiento | `ticketing-{env}-order-processing-queue` |
| DLQ procesamiento | `ticketing-{env}-order-processing-dlq` |
| Cola expiración | `ticketing-{env}-order-expiration-queue` |
| DLQ expiración | `ticketing-{env}-order-expiration-dlq` |

### `api/`

| Recurso | Nombre |
|---|---|
| ECR | `ticketing-{env}-ecr` |
| ECS Cluster | `ticketing-{env}-cluster` |
| ECS Task Definition | `ticketing-{env}-task` |
| ECS Service | `ticketing-{env}-service` |
| ALB interno | `ticketing-{env}-alb` (ECS targets y escalado por requests) |
| NLB interno | `ticketing-{env}-api-nlb` (target del REST VPC Link, reenvía al ALB) |
| API Gateway REST | `ticketing-{env}-api` |
| VPC Link | `ticketing-{env}-vpc-link` |
| WAFv2 Web ACL regional | `ticketing-{env}-api-rate-limit` |
| IAM task/execution roles | `ticketing-{env}-ecs-*` |
| CloudWatch Log Group | `/ecs/ticketing-{env}` |
| Auto Scaling | CPU, memoria y ALB requests/target |

El VPC Link REST requiere un NLB; el ALB se conserva para ECS y el escalado por solicitudes. WAF aplica un límite de 1,000 requests por IP cada cinco minutos por defecto. En AWS bloquea tráfico; Floci no inspecciona requests y solo valida la configuración del Web ACL. Ajusta `waf_rate_limit` según el tráfico esperado.

El módulo API puede crear una VPC sandbox con `create_vpc = true`. En AWS usa una VPC existente con subnets adecuadas y establece `create_vpc = false`. En `pdn`, `force_delete_ecr = false` protege el repositorio.

---

## Floci local

Floci emula DynamoDB, SQS, API Gateway REST, EC2/VPC, IAM, ECR, ECS, ELBv2, Auto Scaling, CloudWatch y WAFv2 en un endpoint AWS-compatible. WAFv2 en Floci persiste y valida Web ACLs y asociaciones, pero **no inspecciona tráfico ni bloquea requests**; la protección efectiva rate-based se valida solo en AWS. ECS queda en mock para probar el plano de control sin iniciar la aplicación. Floci no emula un ALB registrado como target de NLB, por lo que el attachment se omite en modo Floci; el NLB→ALB real se conserva al desplegar en AWS. ECR puede iniciar un registry Docker lateral, así que Docker Desktop debe estar iniciado.

Desde la raíz del repositorio:

```powershell
docker compose -f docker-compose.floci.yml up -d
```

La consola visual se abre en [http://localhost:4566/_floci/ui](http://localhost:4566/_floci/ui). El compose configura `FLOCI_HOSTNAME=floci` para que la consola lateral resuelva el core dentro de la red Docker; la CLI Terraform del host continúa usando `http://localhost:4566`.

Aplica los módulos en orden, usando estados locales independientes:

```powershell
Push-Location terraform/dynamo
terraform init
terraform plan '-var-file=env/dev/terraform-dev.tfvars'
terraform apply '-var-file=env/dev/terraform-dev.tfvars'
Pop-Location

Push-Location terraform/sqs
terraform init
terraform plan '-var-file=env/dev/terraform-dev.tfvars'
terraform apply '-var-file=env/dev/terraform-dev.tfvars'
Pop-Location

Push-Location terraform/api
terraform init
terraform plan '-var-file=env/dev/terraform-dev.tfvars'
terraform apply '-var-file=env/dev/terraform-dev.tfvars'
Pop-Location
```

Elimina los recursos locales en orden inverso, con los mismos tfvars, y luego detén Floci:

```powershell
Push-Location terraform/api; terraform destroy '-var-file=env/dev/terraform-dev.tfvars'; Pop-Location
Push-Location terraform/sqs; terraform destroy '-var-file=env/dev/terraform-dev.tfvars'; Pop-Location
Push-Location terraform/dynamo; terraform destroy '-var-file=env/dev/terraform-dev.tfvars'; Pop-Location
docker compose -f docker-compose.floci.yml down
```

Los archivos `qa` y `pdn` también apuntan deliberadamente a Floci, usan la cuenta ficticia `000000000000` y crean redes sandbox. **No son configuraciones de despliegue AWS.** Para AWS, establece `floci_endpoint = null`, usa el mecanismo normal de credenciales, reemplaza account ID, VPC/subnets, nombres/ARNs de DynamoDB y SQS, y deja `create_vpc = false`.

## Despliegue AWS

Los tfvars incluidos (`env/dev`, `env/qa`, `env/pdn`) apuntan a Floci y usan la cuenta ficticia `000000000000`; **no los uses contra AWS**. Crea tfvars específicos para AWS con `floci_endpoint = null`, credenciales mediante el provider chain, account ID real, VPC/subnets reales y los nombres/ARNs reales de DynamoDB/SQS para API. Para usar una VPC existente, configura `create_vpc = false`.

