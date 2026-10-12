package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.Seat;
import co.com.nequi.ticketing.model.enums.SeatStatus;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.IdempotencyRepository;
import co.com.nequi.ticketing.model.gateways.QueueService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    private static final String EVENT_ID = "evt-1";
    private static final String USER_ID = "usr-1";
    private static final String IDEMPOTENCY_KEY = "key-123";
    private static final String REQUEST_HASH = "hash123";
    private static final String TRACE_ID = "trace-1";
    private static final String ORDER_ID = "ord-1";
        private static final List<String> SEAT_IDS = List.of("S-00001", "S-00002");

    @Mock private EventRepository eventRepository;
    @Mock private TicketOrderRepository ticketOrderRepository;
    @Mock private IdempotencyRepository idempotencyRepository;
    @Mock private QueueService queueService;
        @Mock private SeatRepository seatRepository;

    private CreateOrderUseCase createOrderUseCase;

    @BeforeEach
    void setUp() {
        createOrderUseCase = new CreateOrderUseCase(eventRepository, ticketOrderRepository, idempotencyRepository, queueService, seatRepository);
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        Event event = Event.builder().id(EVENT_ID).ticketPrice(BigDecimal.valueOf(100.0)).currency("COP").build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.just(event));
        when(seatRepository.findSeats(EVENT_ID, SEAT_IDS)).thenReturn(availableSeats(SEAT_IDS));
        when(ticketOrderRepository.createOrderTransaction(any(TicketOrder.class), any(OrderHistory.class), any(IdempotencyRecord.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(queueService.sendExpireOrderMessage(anyString(), anyLong(), anyLong())).thenReturn(Mono.empty());

        StepVerifier.create(createOrderUseCase.execute(EVENT_ID, USER_ID, SEAT_IDS, IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectNextMatches(order -> order.getStatus() == TicketStatus.RESERVED && order.getQuantity() == 2 && order.getSeatIds().equals(SEAT_IDS))
                .verifyComplete();
    }

    @Test
    void shouldCreateOrderWithDefaultCurrencyWhenEventHasNone() {
        Event event = Event.builder().id(EVENT_ID).ticketPrice(BigDecimal.valueOf(100.0)).currency(null).build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.just(event));
        when(seatRepository.findSeats(EVENT_ID, List.of("S-00001"))).thenReturn(availableSeats(List.of("S-00001")));
        when(ticketOrderRepository.createOrderTransaction(any(), any(), any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(queueService.sendExpireOrderMessage(anyString(), anyLong(), anyLong())).thenReturn(Mono.empty());

        StepVerifier.create(createOrderUseCase.execute(EVENT_ID, USER_ID, List.of("S-00001"), IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectNextMatches(order -> "COP".equals(order.getCurrency()))
                .verifyComplete();
    }

    @Test
    void shouldReturnExistingOrderOnIdempotentRequest() {
        TicketOrder existing = TicketOrder.builder().id(ORDER_ID).status(TicketStatus.RESERVED).build();
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.builder().orderId(ORDER_ID).requestHash(REQUEST_HASH).build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.just(idempotencyRecord));
        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(existing));

        StepVerifier.create(createOrderUseCase.execute(EVENT_ID, USER_ID, SEAT_IDS, IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectNextMatches(order -> order.getId().equals(ORDER_ID))
                .verifyComplete();
    }

    @Test
    void shouldFailOnIdempotencyHashMismatch() {
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.builder().orderId(ORDER_ID).requestHash("different-hash").build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.just(idempotencyRecord));

        StepVerifier.create(createOrderUseCase.execute(EVENT_ID, USER_ID, SEAT_IDS, IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.VAL_IDEMPOTENCY_MISMATCH)
                .verify();
    }

    @Test
    void shouldFailWhenEventNotFound() {
        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(createOrderUseCase.execute(EVENT_ID, USER_ID, SEAT_IDS, IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_EVENT_NOT_FOUND)
                .verify();
    }

    @Test
    void shouldRejectSeatThatWasTakenAfterAvailabilityRead() {
        Event event = Event.builder().id(EVENT_ID).ticketPrice(BigDecimal.valueOf(100)).build();
        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.just(event));
        when(seatRepository.findSeats(EVENT_ID, List.of("S-00001"))).thenReturn(Flux.just(Seat.builder()
                .eventId(EVENT_ID).seatId("S-00001").status(SeatStatus.RESERVED).build()));

        StepVerifier.create(createOrderUseCase.execute(EVENT_ID, USER_ID, List.of("S-00001"), IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getGeneralMessage() == GeneralMessage.BUS_SEAT_UNAVAILABLE)
                .verify();
        verify(ticketOrderRepository, never()).createOrderTransaction(any(), any(), any());
    }

    @Test
    void shouldRejectDuplicateSeatIds() {
        StepVerifier.create(createOrderUseCase.execute(EVENT_ID, USER_ID,
                        List.of("S-00001", "S-00001"), IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getGeneralMessage() == GeneralMessage.VAL_INVALID_SEAT_SELECTION)
                .verify();
    }

    @Test
    void shouldCompleteEvenWhenSqsFails() {
        Event event = Event.builder().id(EVENT_ID).ticketPrice(BigDecimal.valueOf(50.0)).currency("COP").build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.just(event));
        when(seatRepository.findSeats(EVENT_ID, List.of("S-00001"))).thenReturn(availableSeats(List.of("S-00001")));
        when(ticketOrderRepository.createOrderTransaction(any(), any(), any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(queueService.sendExpireOrderMessage(anyString(), anyLong(), anyLong()))
                .thenReturn(Mono.error(new RuntimeException("SQS unavailable")));

        StepVerifier.create(createOrderUseCase.execute(EVENT_ID, USER_ID, List.of("S-00001"), IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectNextMatches(order -> order.getStatus() == TicketStatus.RESERVED)
                .verifyComplete();
    }

        private Flux<Seat> availableSeats(List<String> seatIds) {
                return Flux.fromIterable(seatIds).map(seatId -> Seat.builder()
                                .eventId(EVENT_ID).seatId(seatId).status(SeatStatus.AVAILABLE).build());
        }
}
