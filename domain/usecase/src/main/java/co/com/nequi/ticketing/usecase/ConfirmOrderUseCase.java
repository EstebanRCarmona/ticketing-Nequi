package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.usecase.utils.UseCaseConstants;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.QueueService;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RequiredArgsConstructor
public class ConfirmOrderUseCase {

    private final TicketOrderRepository ticketOrderRepository;
    private final QueueService queueService;

    public Mono<TicketOrder> execute(String orderId, String userId, boolean isAdmin, String paymentReference, String traceId) {
        return ticketOrderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)))
                .filter(order -> isAdmin || order.getUserId().equals(userId))
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)))
                .flatMap(order -> dispatchByStatus(order, orderId, paymentReference, traceId));
    }

    private Mono<TicketOrder> dispatchByStatus(TicketOrder order, String orderId, String paymentReference, String traceId) {
        return switch (order.getStatus()) {
            case PENDING_CONFIRMATION -> queueService.sendFinalizeOrderMessage(orderId, traceId).thenReturn(order);
            case SOLD -> Mono.just(order);
            case RESERVED -> confirmReserved(order, orderId, paymentReference, traceId);
            case CANCELLED, EXPIRED, COMPLIMENTARY -> Mono.error(new BusinessException(GeneralMessage.BUS_INVALID_STATE_TRANSITION));
        };
    }

    private Mono<TicketOrder> confirmReserved(TicketOrder order, String orderId, String paymentReference, String traceId) {
        return Mono.just(order)
                .filter(o -> o.getExpiresAt() == null || o.getExpiresAt().isAfter(Instant.now()))
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_EXPIRED)))
                .flatMap(o -> ticketOrderRepository.confirmOrderTransaction(o, paymentReference, traceId))
                .flatMap(confirmed -> queueService.sendFinalizeOrderMessage(orderId, traceId)
                        .then(queueService.sendExpireOrderMessage(orderId, confirmed.getExpiresAt() != null ? confirmed.getExpiresAt().getEpochSecond() : 0L, UseCaseConstants.CONFIRM_TIMEOUT_SECONDS).onErrorResume(e -> Mono.empty()))
                        .thenReturn(confirmed));
    }
}
