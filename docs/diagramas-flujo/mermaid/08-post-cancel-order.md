```mermaid
graph LR
    classDef start_end fill:#000,stroke:#000,color:#fff,stroke-width:2px;
    classDef process fill:#fff,stroke:#000,stroke-width:1px;
    classDef decision fill:#fff,stroke:#000,stroke-width:1px;
    classDef success fill:#d4edda,stroke:#28a745,stroke-width:1px,color:#155724;
    classDef error fill:#f8d7da,stroke:#dc3545,stroke-width:1px,color:#721c24;

    subgraph Header ["POST /api/v1/orders/{orderId}/cancel"]
        direction LR
    end

    Start(( )) --> BuscarOrden["DynamoDB GetItem<br/>orders por orderId"]

    BuscarOrden --> ExisteOrden{Existe<br/>orden?}
    ExisteOrden -- No --> R404[RESPONSE 404<br/>BUS005 - Order Not Found]

    ExisteOrden -- Yes --> ValidaAcceso{isAdmin o<br/>userId coincide?}
    ValidaAcceso -- No --> R404b[RESPONSE 404<br/>BUS005 - Order Not Found]

    ValidaAcceso -- Yes --> EvaluaEstado{Estado<br/>actual?}

    EvaluaEstado -- CANCELLED --> SuccessNode(( ))

    EvaluaEstado -- PENDING_CONFIRMATION / SOLD /<br/>EXPIRED / COMPLIMENTARY --> R409[RESPONSE 409<br/>BUS004 - Invalid State Transition]

    EvaluaEstado -- RESERVED --> Transaccion["DynamoDB TransactWriteItems<br/>UPDATE orden → CANCELLED, version+1 [version = expected]<br/>UPDATE inventory reserved -= q, available += q<br/>PUT historial [attribute_not_exists]"]

    Transaccion --> Exitosa{Exitosa?}
    Exitosa -- No --> EsConflicto{ConditionalCheck<br/>Failed?}
    EsConflicto -- Yes --> R409con[RESPONSE 409<br/>BUS007 - Concurrency Conflict]
    EsConflicto -- No --> R500[RESPONSE 500<br/>ERR001 - DynamoDB Error]

    Exitosa -- Yes --> SuccessNode
    SuccessNode --> R200

    subgraph RESPONSES ["RESPUESTAS"]
        R200["RESPONSE (200)<br/>{<br/>  'statusCode': '0',<br/>  'description': 'SUCCESS',<br/>  'data': {<br/>    'orderId': '...',<br/>    'status': 'CANCELLED',<br/>    'releaseReason': 'CANCELLED_BY_USER'<br/>  }<br/>}"]
        R_ERR["RESPONSE (ERROR)<br/>{<br/>  'statusCode': 'XXX',<br/>  'description': '...'<br/>}"]
    end

    R404 --> EndNode(( ))
    R404b --> EndNode
    R409 --> EndNode
    R409con --> EndNode
    R500 --> EndNode

    class Start,SuccessNode,EndNode start_end;
    class BuscarOrden,Transaccion process;
    class ExisteOrden,ValidaAcceso,EvaluaEstado,Exitosa,EsConflicto decision;
    class R200 success;
    class R404,R404b,R409,R409con,R500,R_ERR error;
```
