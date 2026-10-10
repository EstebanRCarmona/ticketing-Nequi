package co.com.nequi.ticketing.model.gateways;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.Inventory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventRepository {
    Mono<Event> createWithInventory(Event event, Inventory inventory);
    Mono<Event> findById(String id);
    Flux<Event> findAllPaginated(int limit, String cursor);
}
