package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.IdempotencyRepository;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateComplimentaryUseCaseTest {

    private static final String EVENT_ID = "evt-1";
    private static final String ADMIN_ID = "admin-1";
    private static final String IDEMPOTENCY_KEY = "comp-key-1";
    private static final String REQUEST_HASH = "hash-comp";
    private static final String TRACE_ID = "trace-1";
    private static final String ORDER_ID = "ord-1";

    @Mock private EventRepository eventRepository;
    @Mock private TicketOrderRepository ticketOrderRepository;
    @Mock private IdempotencyRepository idempotencyRepository;

    private CreateComplimentaryUseCase createComplimentaryUseCase;

    @BeforeEach
    void setUp() {
        createComplimentaryUseCase = new CreateComplimentaryUseCase(eventRepository, ticketOrderRepository, idempotencyRepository);
    }

    @Test
    void shouldCreateComplimentaryOrderSuccessfully() {
        Event event = Event.builder().id(EVENT_ID).currency("COP").build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.just(event));
        when(ticketOrderRepository.createComplimentaryTransaction(any(TicketOrder.class), any(OrderHistory.class), any(IdempotencyRecord.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(createComplimentaryUseCase.execute(EVENT_ID, ADMIN_ID, 5, "Prensa VIP", "Promoción", IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.COMPLIMENTARY && o.getQuantity() == 5)
                .verifyComplete();
    }

    @Test
    void shouldCreateComplimentaryWithDefaultCurrencyWhenEventHasNone() {
        Event event = Event.builder().id(EVENT_ID).currency(null).build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.just(event));
        when(ticketOrderRepository.createComplimentaryTransaction(any(), any(), any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(createComplimentaryUseCase.execute(EVENT_ID, ADMIN_ID, 1, "VIP", "Promo", IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectNextMatches(o -> "COP".equals(o.getCurrency()))
                .verifyComplete();
    }

    @Test
    void shouldReturnExistingOrderOnIdempotentRequest() {
        TicketOrder existing = TicketOrder.builder().id(ORDER_ID).status(TicketStatus.COMPLIMENTARY).build();
        IdempotencyRecord record = IdempotencyRecord.builder().orderId(ORDER_ID).requestHash(REQUEST_HASH).build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.just(record));
        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(existing));

        StepVerifier.create(createComplimentaryUseCase.execute(EVENT_ID, ADMIN_ID, 5, "VIP", "Promo", IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectNextMatches(o -> o.getId().equals(ORDER_ID))
                .verifyComplete();
    }

    @Test
    void shouldFailOnIdempotencyHashMismatch() {
        IdempotencyRecord record = IdempotencyRecord.builder().orderId(ORDER_ID).requestHash("different-hash").build();

        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.just(record));

        StepVerifier.create(createComplimentaryUseCase.execute(EVENT_ID, ADMIN_ID, 5, "VIP", "Promo", IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.VAL_IDEMPOTENCY_MISMATCH)
                .verify();
    }

    @Test
    void shouldFailWhenEventNotFound() {
        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById(EVENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(createComplimentaryUseCase.execute(EVENT_ID, ADMIN_ID, 5, "VIP", "Promo", IDEMPOTENCY_KEY, REQUEST_HASH, TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_EVENT_NOT_FOUND)
                .verify();
    }
}
