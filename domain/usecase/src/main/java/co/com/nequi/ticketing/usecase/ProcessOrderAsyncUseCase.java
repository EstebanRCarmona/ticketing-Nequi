package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.usecase.utils.UseCaseConstants;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.gateways.PaymentGateway;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RequiredArgsConstructor
public class ProcessOrderAsyncUseCase {

    private final TicketOrderRepository ticketOrderRepository;
    private final PaymentGateway paymentGateway;

    public Mono<Void> execute(String orderId, String traceId, String sqsMessageId) {
        return ticketOrderRepository.findById(orderId)
                .filter(order -> TicketStatus.PENDING_CONFIRMATION.equals(order.getStatus()))
                .flatMap(order -> isNearExpiry(order)
                        ? ticketOrderRepository.expireOrderTransaction(order, traceId).then()
                        : processPayment(order, traceId, sqsMessageId))
                .then();
    }

    private boolean isNearExpiry(TicketOrder order) {
        return order.getExpiresAt() != null &&
                order.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond() < UseCaseConstants.SAFETY_MARGIN_SECONDS;
    }

    private Mono<Void> processPayment(TicketOrder order, String traceId, String sqsMessageId) {
        return paymentGateway.charge(order.getId(), order.getPaymentReference())
                .flatMap(approved -> Boolean.TRUE.equals(approved)
                        ? ticketOrderRepository.finalizeOrderSaleTransaction(order, traceId, sqsMessageId).then()
                        : ticketOrderRepository.rejectOrderPaymentTransaction(order, UseCaseConstants.FAILURE_CODE_PAYMENT_REJECTED, UseCaseConstants.FAILURE_DESC_PAYMENT_REJECTED, traceId).then());
    }
}
