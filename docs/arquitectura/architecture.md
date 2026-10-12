# Architecture

## AWS deployment

```mermaid
flowchart LR
    Client["Clients / Postman"] -->|HTTPS| WAF["AWS WAFv2 Regional\nRate rule: per-IP requests"]
    WAF -->|associated with REST stage| APIGW["API Gateway REST\nRegional stage"]
    APIGW -->|HTTP_PROXY + VPC Link| NLB["Internal Network Load Balancer\nNLB listener :80"]

    subgraph VPC["Application VPC - private subnets in 2 AZs"]
        NLB -->|TCP target group\ntarget type: alb| ALB["Internal Application Load Balancer\nHTTP listener :80"]
        ALB -->|HTTP :8080\nhealth check /actuator/health| ECS["ECS Fargate Service\nJava 25 / Spring Boot 4 / WebFlux"]
        ECS --- SG["Security groups\nALB ingress from VPC\nECS ingress from ALB only"]
    end

    CI["Developer / CI build"] -->|push image| ECR["Amazon ECR"]
    ECR -->|image pull\nTask Execution Role| ECS
    ECS -->|Task Role: read/write\nTransactWriteItems, Query| DDB["DynamoDB"]
    DDB --- EVENTS["events"]
    DDB --- INVENTORY["inventory counters"]
    DDB --- SEATS["seats\nseatKey + seatId\nstatus GSI"]
    DDB --- ORDERS["orders\nexpiry GSI"]
    DDB --- HISTORY["order-history"]
    DDB --- IDEM["idempotency\nTTL"]

    ECS -->|publish / long poll / delete on success| PROCESS["SQS order-processing queue"]
    PROCESS -->|receive| ECS
    PROCESS -. max receives .-> PROCESSDLQ["Processing DLQ"]
    ECS -->|delayed expiration message| EXPIRY["SQS order-expiration queue"]
    EXPIRY -->|receive| ECS
    EXPIRY -. max receives .-> EXPIRYDLQ["Expiration DLQ"]

    ECS -->|container logs| CWLOGS["CloudWatch Logs"]
    CWLOGS --> CW["CloudWatch metrics and alarms"]
    CW -->|CPU / memory / ALB requests| SCALE["Application Auto Scaling"]
    SCALE --> ECS
```

**Request path:** WAF protects the API Gateway stage; the REST VPC Link targets the NLB, which forwards to the internal ALB and ECS tasks. The NLB-to-ALB target attachment is supported by AWS. WAF's rate limit is keyed by source IP and does not replace application authentication, per-user controls, or idempotency.

**Purchase path:** the WebFlux service first reserves selected seats synchronously with a conditional DynamoDB transaction. It publishes expiration messages to SQS; confirmation publishes payment-finalization messages. The SQS consumer updates order, seat, inventory, and audit records transactionally, then acknowledges successful messages. A scheduled sweeper provides a second expiration path.

**IAM:** the ECS Task Role grants the application access to DynamoDB and SQS. The ECS Task Execution Role grants image-pull and log-delivery permissions. The API and load balancers are defined in Terraform; the CI/build system that pushes application images is not included in the current repository.

## Local development and infrastructure emulation

```mermaid
flowchart LR
    Client["curl / Postman"] -->|HTTP :8080| APP["Ticketing WebFlux application"]
    APP -->|AWS SDK endpoints| LOCALSTACK["LocalStack :4566\nDynamoDB tables + SQS queues\ncreated by init-aws.sh"]
    IT["Gradle integrationTest"] -->|Testcontainers| TC["Ephemeral LocalStack container"]

    TF["Terraform CLI"] -->|AWS provider endpoint overrides\nhttp://localhost:4566| FLOCI["Floci emulator"]
    FLOCI -->|emulated control plane| INFRA["DynamoDB / SQS / API Gateway /\nVPC / ELBv2 / ECS / ECR / WAFv2"]
    FLOCI -. on-demand UI .-> UI["http://localhost:4566/_floci/ui"]
    FLOCI -. ECR backing registry .-> REGISTRY["registry:2 sidecar"]
    FLOCI -. ECS mock mode .-> MOCK["Control-plane resources only"]
```

