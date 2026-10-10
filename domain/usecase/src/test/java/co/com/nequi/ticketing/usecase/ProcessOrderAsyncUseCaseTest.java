package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.gateways.PaymentGateway;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessOrderAsyncUseCaseTest {

    private static final String ORDER_ID = "ord-1";
    private static final String TRACE_ID = "trace-1";
    private static final String MSG_ID = "msg-1";
    private static final String PAYMENT_REF = "tok_visa";

    @Mock private TicketOrderRepository ticketOrderRepository;
    @Mock private PaymentGateway paymentGateway;

    private ProcessOrderAsyncUseCase processOrderAsyncUseCase;

    @BeforeEach
    void setUp() {
        processOrderAsyncUseCase = new ProcessOrderAsyncUseCase(ticketOrderRepository, paymentGateway);
    }

    @Test
    void shouldFinalizeOrderWhenPaymentApproved() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).status(TicketStatus.PENDING_CONFIRMATION)
                .expiresAt(Instant.now().plusSeconds(200L)).paymentReference(PAYMENT_REF).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));
        when(paymentGateway.charge(ORDER_ID, PAYMENT_REF)).thenReturn(Mono.just(true));
        when(ticketOrderRepository.finalizeOrderSaleTransaction(any(), anyString(), anyString()))
                .thenReturn(Mono.just(order.toBuilder().status(TicketStatus.SOLD).build()));

        StepVerifier.create(processOrderAsyncUseCase.execute(ORDER_ID, TRACE_ID, MSG_ID))
                .verifyComplete();
    }

    @Test
    void shouldRejectOrderWhenPaymentDeclined() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).status(TicketStatus.PENDING_CONFIRMATION)
                .expiresAt(Instant.now().plusSeconds(200L)).paymentReference("REJECT_PAYMENT").build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));
        when(paymentGateway.charge(ORDER_ID, "REJECT_PAYMENT")).thenReturn(Mono.just(false));
        when(ticketOrderRepository.rejectOrderPaymentTransaction(any(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(order.toBuilder().status(TicketStatus.CANCELLED).build()));

        StepVerifier.create(processOrderAsyncUseCase.execute(ORDER_ID, TRACE_ID, MSG_ID))
                .verifyComplete();
    }

    @Test
    void shouldExpireOrderWhenNearDeadline() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).status(TicketStatus.PENDING_CONFIRMATION)
                .expiresAt(Instant.now().plusSeconds(2L)).paymentReference(PAYMENT_REF).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));
        when(ticketOrderRepository.expireOrderTransaction(any(), anyString()))
                .thenReturn(Mono.just(order.toBuilder().status(TicketStatus.EXPIRED).build()));

        StepVerifier.create(processOrderAsyncUseCase.execute(ORDER_ID, TRACE_ID, MSG_ID))
                .verifyComplete();
    }

    @Test
    void shouldSkipOrderNotInPendingConfirmation() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).status(TicketStatus.SOLD).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(processOrderAsyncUseCase.execute(ORDER_ID, TRACE_ID, MSG_ID))
                .verifyComplete();
    }

    @Test
    void shouldSkipWhenOrderNotFound() {
        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(processOrderAsyncUseCase.execute(ORDER_ID, TRACE_ID, MSG_ID))
                .verifyComplete();
    }
}
