package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.EventDetail;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.InventoryRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetEventUseCase {

    private final EventRepository eventRepository;
    private final InventoryRepository inventoryRepository;

    public Mono<EventDetail> getEventDetail(String eventId) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_EVENT_NOT_FOUND)))
                .flatMap(event -> inventoryRepository.findByEventIdConsistent(eventId)
                        .switchIfEmpty(Mono.error(new TechnicalException(GeneralMessage.TECH_INTERNAL_ERROR, new IllegalStateException("Event inventory missing"))))
                        .map(inventory -> EventDetail.builder()
                                .event(event)
                                .inventory(inventory)
                                .build()));
    }

    public Flux<Event> getAllPaginated(int limit, String cursor) {
        return eventRepository.findAllPaginated(limit, cursor);
    }
}
