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
public class InventoryEntity {
    private String eventId;
    private Integer available;
    private Integer reserved;
    private Integer pendingConfirmation;
    private Integer sold;
    private Integer complimentary;
    private Integer totalCapacity;
    private String updatedAt;

    @DynamoDbPartitionKey
    public String getEventId() {
        return eventId;
    }
}
