package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.enums.EventCategory;
import co.com.nequi.ticketing.model.Inventory;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreateEventUseCaseTest {

    @Mock private EventRepository eventRepository;
        @Mock private SeatRepository seatRepository;

    private CreateEventUseCase createEventUseCase;

    @BeforeEach
    void setUp() {
        createEventUseCase = new CreateEventUseCase(eventRepository, seatRepository);
    }

    @Test
    void shouldCreateEventSuccessfully() {
        Event inputEvent = Event.builder()
                .name("Rock Concert 2026")
                .eventDate("2026-12-01T20:00:00Z")
                .venue("Stadium")
                .category(EventCategory.CONCERT)
                .totalCapacity(100)
                .ticketPrice(BigDecimal.valueOf(50.0))
                .build();

        when(eventRepository.createWithInventory(any(Event.class), any(Inventory.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(seatRepository.createSeats(anyString(), anyInt())).thenReturn(Mono.empty());
        when(eventRepository.activateEvent(anyString())).thenAnswer(invocation -> Mono.just(
                Event.builder().id(invocation.getArgument(0)).listingKey("EVENT").build()));

        StepVerifier.create(createEventUseCase.execute(inputEvent))
                .expectNextMatches(saved -> saved.getId() != null && saved.getListingKey().equals("EVENT"))
                .verifyComplete();
        verify(seatRepository).createSeats(anyString(), org.mockito.ArgumentMatchers.eq(100));
    }

    @Test
    void shouldFailWhenCapacityIsZero() {
        Event inputEvent = Event.builder().name("Invalid Event").totalCapacity(0).build();

        StepVerifier.create(createEventUseCase.execute(inputEvent))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.VAL_INVALID_QUANTITY)
                .verify();
    }

    @Test
    void shouldFailWhenCapacityIsNull() {
        Event inputEvent = Event.builder().name("Invalid Event").totalCapacity(null).build();

        StepVerifier.create(createEventUseCase.execute(inputEvent))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.VAL_INVALID_QUANTITY)
                .verify();
    }

    @Test
    void shouldSetDefaultCurrencyWhenNotProvided() {
        Event inputEvent = Event.builder()
                .name("Concert").totalCapacity(50)
                .ticketPrice(BigDecimal.valueOf(100.0)).build();

        when(eventRepository.createWithInventory(any(Event.class), any(Inventory.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(seatRepository.createSeats(anyString(), anyInt())).thenReturn(Mono.empty());
        when(eventRepository.activateEvent(anyString())).thenAnswer(invocation -> Mono.just(
                Event.builder().id(invocation.getArgument(0)).currency("COP").build()));

        StepVerifier.create(createEventUseCase.execute(inputEvent))
                .expectNextMatches(saved -> "COP".equals(saved.getCurrency()))
                .verifyComplete();
    }
}
