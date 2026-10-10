package co.com.nequi.ticketing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import co.com.nequi.ticketing.model.enums.EventCategory;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    private String id;
    private String name;
    private String eventDate;
    private String venue;
    private EventCategory category;
    private BigDecimal ticketPrice;
    private String currency;
    private Integer totalCapacity;
    private String listingKey;
    private Instant createdAt;
    private Instant updatedAt;
}
