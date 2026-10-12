package co.com.nequi.ticketing.model;

import co.com.nequi.ticketing.model.enums.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Seat {
    private String eventId;
    private String seatId;
    private SeatStatus status;
    private String orderId;
    private Instant updatedAt;
}