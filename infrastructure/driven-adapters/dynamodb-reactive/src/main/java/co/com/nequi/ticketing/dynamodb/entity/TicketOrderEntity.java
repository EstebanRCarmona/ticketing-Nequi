package co.com.nequi.ticketing.dynamodb.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class TicketOrderEntity {
    private String orderId;
    private String eventId;
    private String userId;
    private String orderType;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    private Long deadlineAt;
    private String expiryShard;
    private String releaseReason;
    private String failureCode;
    private String failureDescription;
    private String paymentReference;
    private String recipient;
    private String complimentaryReason;
    private String traceId;
    private Long version;
    private String createdAt;
    private String updatedAt;

    @DynamoDbPartitionKey
    public String getOrderId() {
        return orderId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "gsi-orders-deadline")
    public String getExpiryShard() {
        return expiryShard;
    }

    @DynamoDbSecondarySortKey(indexNames = "gsi-orders-deadline")
    public Long getDeadlineAt() {
        return deadlineAt;
    }
}
