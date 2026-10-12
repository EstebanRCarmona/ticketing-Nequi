package co.com.nequi.ticketing.model.gateways;

import co.com.nequi.ticketing.model.Seat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SeatRepository {
    Mono<Void> createSeats(String eventId, int capacity);
    Flux<Seat> findAvailableSeats(String eventId);
    Flux<Seat> findSeats(String eventId, List<String> seatIds);
}