package co.com.nequi.ticketing.model.gateways;

import co.com.nequi.ticketing.model.Inventory;
import reactor.core.publisher.Mono;

public interface InventoryRepository {
    Mono<Inventory> findByEventIdConsistent(String eventId);
}
