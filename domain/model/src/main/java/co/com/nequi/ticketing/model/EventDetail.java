package co.com.nequi.ticketing.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDetail {
    private Event event;
    private Inventory inventory;
    private List<Seat> availableSeats;
}
