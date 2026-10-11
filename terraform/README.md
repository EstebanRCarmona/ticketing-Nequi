# Terraform — App Ticketing Nequi

Infraestructura AWS para el microservicio `app-ticketing-nequi`, organizada en 3 módulos independientes desplegables por separado.

---

## Estructura

```
terraform/
├── dynamo/          # 5 tablas DynamoDB
│   ├── env/dev/
│   ├── env/qa/
│   └── env/pdn/
├── sqs/             # 2 colas + 2 DLQs
│   ├── env/dev/
│   ├── env/qa/
│   └── env/pdn/
└── api/             # ECR, ECS, ALB, API Gateway, IAM, CloudWatch
    ├── env/dev/
    ├── env/qa/
    └── env/pdn/
```

---

## Recursos creados

### `dynamo/`
| Recurso | Nombre |
|---|---|
| Tabla events | `ticketing-{env}-events` |
| Tabla inventory | `ticketing-{env}-inventory` |
| Tabla orders + GSI deadline | `ticketing-{env}-orders` |
| Tabla order-history | `ticketing-{env}-order-history` |
| Tabla idempotency (TTL) | `ticketing-{env}-idempotency` |

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
| ECS Service (Fargate) | `ticketing-{env}-service` |
| ALB | `ticketing-{env}-alb` |
| API Gateway REST | `ticketing-{env}-api` |
| VPC Link | `ticketing-{env}-vpc-link` |
| IAM Task Role | `ticketing-{env}-ecs-task-role` |
| IAM Execution Role | `ticketing-{env}-ecs-execution-role` |
| CloudWatch Log Group | `/ecs/ticketing-{env}` |
| Auto Scaling (CPU + Memory + Requests/Target) | — |

---

## Orden de despliegue

Los módulos deben desplegarse en este orden porque `api` depende de los ARNs y nombres de `dynamo` y `sqs`:

```
1. dynamo
2. sqs
3. api
```

---

## Comandos

```bash
# Ejemplo para dev — módulo dynamo
cd terraform/dynamo
terraform init
terraform plan -var-file=env/dev/terraform-dev.tfvars
terraform apply -var-file=env/dev/terraform-dev.tfvars

# Módulo sqs
cd terraform/sqs
terraform init
terraform plan -var-file=env/dev/terraform-dev.tfvars
terraform apply -var-file=env/dev/terraform-dev.tfvars

# Módulo api (actualizar tfvars con ARNs reales de dynamo y sqs)
cd terraform/api
terraform init
terraform plan -var-file=env/dev/terraform-dev.tfvars
terraform apply -var-file=env/dev/terraform-dev.tfvars
```

---

## Notas

- Los valores `vpc_id`, `subnet_ids` y `aws_account_id` en los `.tfvars` son placeholders — reemplazar con los valores reales de la cuenta AWS.
- Los ARNs de DynamoDB y SQS en `api/env/*/terraform-*.tfvars` deben actualizarse con los outputs reales del módulo `dynamo` y `sqs` tras su despliegue.
- En `pdn` el `force_delete_ecr = false` protege el repositorio de borrados accidentales.
- El módulo `api` despliega el ALB como interno (`internal = true`) en subnets privadas, conectado de manera segura a API Gateway mediante VPC Link para evitar que el balanceador pueda ser puenteado/esquivado desde el exterior.

