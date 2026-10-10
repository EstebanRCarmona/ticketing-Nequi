package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.Inventory;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class CreateEventUseCase {

    private final EventRepository eventRepository;

    public Mono<Event> execute(Event event) {
        return Mono.just(event)
                .filter(e -> e.getTotalCapacity() != null && e.getTotalCapacity() > 0)
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.VAL_INVALID_QUANTITY)))
                .flatMap(e -> {
                    Instant now = Instant.now();
                    String eventId = UUID.randomUUID().toString();

                    Event newEvent = e.toBuilder()
                            .id(eventId)
                            .currency(e.getCurrency() != null ? e.getCurrency() : "COP")
                            .listingKey("EVENT")
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    Inventory newInventory = Inventory.builder()
                            .eventId(eventId)
                            .available(e.getTotalCapacity())
                            .reserved(0)
                            .pendingConfirmation(0)
                            .sold(0)
                            .complimentary(0)
                            .totalCapacity(e.getTotalCapacity())
                            .updatedAt(now)
                            .build();

                    return eventRepository.createWithInventory(newEvent, newInventory);
                });
    }
}
