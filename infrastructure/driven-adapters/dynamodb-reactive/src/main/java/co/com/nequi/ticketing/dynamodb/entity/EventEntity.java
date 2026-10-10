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
public class EventEntity {
    private String eventId;
    private String name;
    private String venue;
    private String category;
    private String eventDate;
    private Integer totalCapacity;
    private BigDecimal ticketPrice;
    private String currency;
    private String listingKey;
    private String createdAt;
    private String updatedAt;

    @DynamoDbPartitionKey
    public String getEventId() {
        return eventId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "gsi-events-by-date")
    public String getListingKey() {
        return listingKey;
    }

    @DynamoDbSecondarySortKey(indexNames = "gsi-events-by-date")
    public String getEventDate() {
        return eventDate;
    }
}
