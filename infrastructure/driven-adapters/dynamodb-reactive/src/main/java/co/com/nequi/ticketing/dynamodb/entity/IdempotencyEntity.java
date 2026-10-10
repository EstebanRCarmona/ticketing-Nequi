package co.com.nequi.ticketing.dynamodb.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class IdempotencyEntity {
    private String idempotencyKey;
    private String requestHash;
    private String orderId;
    private Long ttl;

    @DynamoDbPartitionKey
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
