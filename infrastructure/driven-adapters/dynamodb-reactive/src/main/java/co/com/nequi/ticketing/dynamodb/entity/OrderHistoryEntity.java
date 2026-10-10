package co.com.nequi.ticketing.dynamodb.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class OrderHistoryEntity {
    private String orderId;
    private Long sequence;
    private String fromStatus;
    private String toStatus;
    private String reasonCode;
    private String actor;
    private Integer quantity;
    private String traceId;
    private String sqsMessageId;
    private String occurredAt;

    @DynamoDbPartitionKey
    public String getOrderId() {
        return orderId;
    }

    @DynamoDbSortKey
    public Long getSequence() {
        return sequence;
    }
}
