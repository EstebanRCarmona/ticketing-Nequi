package co.com.nequi.ticketing.dynamodb.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class SeatEntity {
    private String seatKey;
    private String eventId;
    private String seatId;
    private String status;
    private String statusKey;
    private String orderId;
    private String updatedAt;

    @DynamoDbPartitionKey
    public String getSeatKey() {
        return seatKey;
    }

    @DynamoDbSortKey
    @DynamoDbSecondarySortKey(indexNames = "gsi-seats-by-status")
    public String getSeatId() {
        return seatId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "gsi-seats-by-status")
    public String getStatusKey() {
        return statusKey;
    }
}