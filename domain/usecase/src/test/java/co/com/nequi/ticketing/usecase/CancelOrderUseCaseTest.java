package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.enums.ReleaseReason;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
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
class CancelOrderUseCaseTest {

    private static final String ORDER_ID = "ord-1";
    private static final String USER_ID = "usr-1";
    private static final String TRACE_ID = "trace-1";

    @Mock private TicketOrderRepository ticketOrderRepository;

    private CancelOrderUseCase cancelOrderUseCase;

    @BeforeEach
    void setUp() {
        cancelOrderUseCase = new CancelOrderUseCase(ticketOrderRepository);
    }

    @Test
    void shouldCancelReservedOrder() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId(USER_ID).status(TicketStatus.RESERVED).version(1L).build();
        TicketOrder cancelled = order.toBuilder().status(TicketStatus.CANCELLED)
                .releaseReason(ReleaseReason.CANCELLED_BY_USER).version(2L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));
        when(ticketOrderRepository.cancelOrderTransaction(any(), anyString(), anyString())).thenReturn(Mono.just(cancelled));

        StepVerifier.create(cancelOrderUseCase.execute(ORDER_ID, USER_ID, false, "user request", TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.CANCELLED)
                .verifyComplete();
    }

    @Test
    void shouldReturnIdempotentlyIfAlreadyCancelled() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId(USER_ID).status(TicketStatus.CANCELLED)
                .releaseReason(ReleaseReason.CANCELLED_BY_USER).version(2L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(cancelOrderUseCase.execute(ORDER_ID, USER_ID, false, null, TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.CANCELLED)
                .verifyComplete();
    }

    @Test
    void shouldFailWhenOrderIsSold() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId(USER_ID).status(TicketStatus.SOLD).version(3L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(cancelOrderUseCase.execute(ORDER_ID, USER_ID, false, null, TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_INVALID_STATE_TRANSITION)
                .verify();
    }

    @Test
    void shouldFailWhenOrderBelongsToDifferentUser() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId("usr-other").status(TicketStatus.RESERVED).version(1L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(cancelOrderUseCase.execute(ORDER_ID, USER_ID, false, null, TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_ORDER_NOT_FOUND)
                .verify();
    }

    @Test
    void adminShouldCancelAnyOrder() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).userId("usr-other").status(TicketStatus.RESERVED).version(1L).build();
        TicketOrder cancelled = order.toBuilder().status(TicketStatus.CANCELLED).version(2L).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));
        when(ticketOrderRepository.cancelOrderTransaction(any(), anyString(), anyString())).thenReturn(Mono.just(cancelled));

        StepVerifier.create(cancelOrderUseCase.execute(ORDER_ID, "admin-usr", true, "admin cancel", TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.CANCELLED)
                .verifyComplete();
    }

    @Test
    void shouldFailWhenOrderNotFound() {
        when(ticketOrderRepository.findById("ord-404")).thenReturn(Mono.empty());

        StepVerifier.create(cancelOrderUseCase.execute("ord-404", USER_ID, false, null, TRACE_ID))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_ORDER_NOT_FOUND)
                .verify();
    }
}
