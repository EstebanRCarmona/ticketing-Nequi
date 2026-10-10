package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.Inventory;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetEventUseCaseTest {

    private static final String EVENT_ID = "evt-1";

    @Mock private EventRepository eventRepository;
    @Mock private InventoryRepository inventoryRepository;

    private GetEventUseCase getEventUseCase;

    @BeforeEach
    void setUp() {
        getEventUseCase = new GetEventUseCase(eventRepository, inventoryRepository);
    }

    @Test
    void shouldGetEventDetailSuccessfully() {
        Event event = Event.builder().id(EVENT_ID).name("Concert").build();
        Inventory inventory = Inventory.builder().eventId(EVENT_ID).available(100).totalCapacity(100).build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.just(event));
        when(inventoryRepository.findByEventIdConsistent(EVENT_ID)).thenReturn(Mono.just(inventory));

        StepVerifier.create(getEventUseCase.getEventDetail(EVENT_ID))
                .expectNextMatches(detail -> detail.getEvent().getId().equals(EVENT_ID) && detail.getInventory().getAvailable() == 100)
                .verifyComplete();
    }

    @Test
    void shouldFailWhenEventNotFound() {
        when(eventRepository.findById("evt-404")).thenReturn(Mono.empty());

        StepVerifier.create(getEventUseCase.getEventDetail("evt-404"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void shouldFailWhenInventoryNotFound() {
        Event event = Event.builder().id(EVENT_ID).name("Concert").build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.just(event));
        when(inventoryRepository.findByEventIdConsistent(EVENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(getEventUseCase.getEventDetail(EVENT_ID))
                .expectError()
                .verify();
    }

    @Test
    void shouldReturnEmptyListWhenNoEvents() {
        when(eventRepository.findAllPaginated(anyInt(), isNull())).thenReturn(Flux.empty());

        StepVerifier.create(getEventUseCase.getAllPaginated(10, null))
                .verifyComplete();
    }

    @Test
    void shouldReturnPaginatedEvents() {
        Event event = Event.builder().id(EVENT_ID).name("Concert").build();

        when(eventRepository.findAllPaginated(anyInt(), isNull())).thenReturn(Flux.just(event));

        StepVerifier.create(getEventUseCase.getAllPaginated(10, null))
                .expectNextMatches(e -> e.getId().equals(EVENT_ID))
                .verifyComplete();
    }
}
