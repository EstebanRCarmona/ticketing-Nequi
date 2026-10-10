package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.QueueService;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmOrderUseCaseTest {

    private static final String ORDER_ID = "ord-1";
    private static final String USER_ID = "usr-1";
    private static final String TRACE_ID = "trace-1";

    @Mock private TicketOrderRepository ticketOrderRepository;
    @Mock private QueueService queueService;

    private ConfirmOrderUseCase confirmOrderUseCase;

    @BeforeEach
    void setUp() {
        confirmOrderUseCase = new ConfirmOrderUseCase(ticketOrderRepository, queueService);
    }

    @Test
    void shouldConfirmReservedOrder() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId(USER_ID).status(TicketStatus.RESERVED)
                .expiresAt(Instant.now().plusSeconds(300L)).version(1L).build();
        TicketOrder confirmed = order.toBuilder().status(TicketStatus.PENDING_CONFIRMATION).version(2L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));
        when(ticketOrderRepository.confirmOrderTransaction(any(), anyString(), anyString())).thenReturn(Mono.just(confirmed));
        when(queueService.sendFinalizeOrderMessage(anyString(), anyString())).thenReturn(Mono.empty());
        when(queueService.sendExpireOrderMessage(anyString(), anyLong(), anyLong())).thenReturn(Mono.empty());

        StepVerifier.create(confirmOrderUseCase.execute(ORDER_ID, USER_ID, false, "tok_visa", TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.PENDING_CONFIRMATION)
                .verifyComplete();
    }

    @Test
    void shouldReturnOrderIfAlreadyPendingConfirmation() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId(USER_ID).status(TicketStatus.PENDING_CONFIRMATION).version(2L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));
        when(queueService.sendFinalizeOrderMessage(anyString(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(confirmOrderUseCase.execute(ORDER_ID, USER_ID, false, null, TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.PENDING_CONFIRMATION)
                .verifyComplete();
    }

    @Test
    void shouldReturnOrderIfAlreadySold() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId(USER_ID).status(TicketStatus.SOLD).version(3L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(confirmOrderUseCase.execute(ORDER_ID, USER_ID, false, null, TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.SOLD)
                .verifyComplete();
    }

    @Test
    void shouldFailWhenOrderExpired() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId(USER_ID).status(TicketStatus.RESERVED)
                .expiresAt(Instant.now().minusSeconds(10L)).version(1L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(confirmOrderUseCase.execute(ORDER_ID, USER_ID, false, "tok", TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_ORDER_EXPIRED)
                .verify();
    }

    @Test
    void shouldFailWhenOrderNotFound() {
        when(ticketOrderRepository.findById("ord-404")).thenReturn(Mono.empty());

        StepVerifier.create(confirmOrderUseCase.execute("ord-404", USER_ID, false, "tok", TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_ORDER_NOT_FOUND)
                .verify();
    }

    @Test
    void shouldFailWhenUserDoesNotOwnOrder() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId("usr-other").status(TicketStatus.RESERVED).version(1L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(confirmOrderUseCase.execute(ORDER_ID, USER_ID, false, "tok", TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_ORDER_NOT_FOUND)
                .verify();
    }

    @Test
    void shouldFailWhenStatusIsCancelled() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId(USER_ID).status(TicketStatus.CANCELLED).version(2L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(confirmOrderUseCase.execute(ORDER_ID, USER_ID, false, "tok", TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_INVALID_STATE_TRANSITION)
                .verify();
    }

    @Test
    void shouldFailWhenStatusIsExpired() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId(USER_ID).status(TicketStatus.EXPIRED).version(2L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(confirmOrderUseCase.execute(ORDER_ID, USER_ID, false, "tok", TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_INVALID_STATE_TRANSITION)
                .verify();
    }

    @Test
    void shouldFailWhenStatusIsComplimentary() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId(USER_ID).status(TicketStatus.COMPLIMENTARY).version(1L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(confirmOrderUseCase.execute(ORDER_ID, USER_ID, false, "tok", TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_INVALID_STATE_TRANSITION)
                .verify();
    }
}
