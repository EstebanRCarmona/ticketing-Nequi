package co.com.nequi.ticketing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {
    private String eventId;
    private Integer available;
    private Integer reserved;
    private Integer pendingConfirmation;
    private Integer sold;
    private Integer complimentary;
    private Integer totalCapacity;
    private Instant updatedAt;
}
