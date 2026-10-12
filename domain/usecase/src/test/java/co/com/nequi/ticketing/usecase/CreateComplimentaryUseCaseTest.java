package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.Seat;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.enums.SeatStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.IdempotencyRepository;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import co.com.nequi.ticketing.model.gateways.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class CreateComplimentaryUseCaseTest {

    private static final String EVENT_ID = "evt-1";
    private static final String ADMIN_ID = "admin-1";
    private static final String IDEMPOTENCY_KEY = "comp-key-1";
    private static final String REQUEST_HASH = "hash-comp";
    private static final String TRACE_ID = "trace-1";
    private static final String ORDER_ID = "ord-1";
    private static final List<String> SEAT_IDS = List.of("S-00001", "S-00002", "S-00003", "S-00004", "S-00005");

    @Mock private EventRepository eventRepository;
    @Mock private TicketOrderRepository ticketOrderRepository;
    @Mock private IdempotencyRepository idempotencyRepository;
        @Mock private SeatRepository seatRepository;

    private CreateComplimentaryUseCase createComplimentaryUseCase;

    @BeforeEach
    void setUp() {
        createComplimentaryUseCase = new CreateComplimentaryUseCase(eventRepository, ticketOrderRepository, idempotencyRepository, seatRepository);
    }

    @Test
    void shouldCreateComplimentaryOrderSuccessfully() {
        Event event = Event.builder().id(EVENT_ID).currency("COP").build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.just(event));
        when(seatRepository.findSeats(EVENT_ID, SEAT_IDS)).thenReturn(availableSeats(SEAT_IDS));
        when(ticketOrderRepository.createComplimentaryTransaction(any(TicketOrder.class), any(OrderHistory.class), any(IdempotencyRecord.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(createComplimentaryUseCase.execute(command(SEAT_IDS, "Prensa VIP", "Promoción"), IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.COMPLIMENTARY && o.getQuantity() == 5)
                .verifyComplete();
    }

    @Test
    void shouldCreateComplimentaryWithDefaultCurrencyWhenEventHasNone() {
        Event event = Event.builder().id(EVENT_ID).currency(null).build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.just(event));
        when(seatRepository.findSeats(EVENT_ID, List.of("S-00001"))).thenReturn(availableSeats(List.of("S-00001")));
        when(ticketOrderRepository.createComplimentaryTransaction(any(), any(), any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(createComplimentaryUseCase.execute(command(List.of("S-00001"), "VIP", "Promo"), IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectNextMatches(o -> "COP".equals(o.getCurrency()))
                .verifyComplete();
    }

    @Test
    void shouldReturnExistingOrderOnIdempotentRequest() {
        TicketOrder existing = TicketOrder.builder().id(ORDER_ID).status(TicketStatus.COMPLIMENTARY).build();
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.builder().orderId(ORDER_ID).requestHash(REQUEST_HASH).build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.just(idempotencyRecord));
        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(existing));

        StepVerifier.create(createComplimentaryUseCase.execute(command(SEAT_IDS, "VIP", "Promo"), IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectNextMatches(o -> o.getId().equals(ORDER_ID))
                .verifyComplete();
    }

    @Test
    void shouldFailOnIdempotencyHashMismatch() {
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.builder().orderId(ORDER_ID).requestHash("different-hash").build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.just(idempotencyRecord));

        StepVerifier.create(createComplimentaryUseCase.execute(command(SEAT_IDS, "VIP", "Promo"), IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.VAL_IDEMPOTENCY_MISMATCH)
                .verify();
    }

    @Test
    void shouldFailWhenEventNotFound() {
        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(createComplimentaryUseCase.execute(command(SEAT_IDS, "VIP", "Promo"), IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_EVENT_NOT_FOUND)
                .verify();
    }

    @Test
    void shouldFailWhenCommandIsNull() {
        StepVerifier.create(createComplimentaryUseCase.execute(null, IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.VAL_INVALID_INPUT)
                .verify();
    }

    @Test
    void shouldFailWhenSeatSelectionIsInvalid() {
        StepVerifier.create(createComplimentaryUseCase.execute(command(List.of("S-00001", "S-00001"), "VIP", "Promo"),
                        IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.VAL_INVALID_SEAT_SELECTION)
                .verify();
    }

    @Test
    void shouldFailWhenASeatIsUnavailable() {
        List<String> seatIds = List.of("S-00001");
        Seat unavailableSeat = Seat.builder().eventId(EVENT_ID).seatId(seatIds.get(0)).status(SeatStatus.RESERVED).build();
        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.just(Event.builder().id(EVENT_ID).build()));
        when(seatRepository.findSeats(EVENT_ID, seatIds)).thenReturn(Flux.just(unavailableSeat));

        StepVerifier.create(createComplimentaryUseCase.execute(command(seatIds, "VIP", "Promo"),
                        IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_SEAT_UNAVAILABLE)
                .verify();
    }

        private Flux<Seat> availableSeats(List<String> seatIds) {
                return Flux.fromIterable(seatIds).map(seatId -> Seat.builder()
                                .eventId(EVENT_ID).seatId(seatId).status(SeatStatus.AVAILABLE).build());
        }

        private CreateComplimentaryUseCase.ComplimentaryCommand command(List<String> seatIds, String recipient, String reason) {
                return new CreateComplimentaryUseCase.ComplimentaryCommand(EVENT_ID, ADMIN_ID, seatIds, recipient, reason);
        }
}
