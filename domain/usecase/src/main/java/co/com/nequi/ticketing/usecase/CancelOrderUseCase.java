package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CancelOrderUseCase {

    private final TicketOrderRepository ticketOrderRepository;

    public Mono<TicketOrder> execute(String orderId, String userId, boolean isAdmin, String reason, String traceId) {
        return ticketOrderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)))
                .filter(order -> isAdmin || order.getUserId().equals(userId))
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)))
                .flatMap(order -> isAlreadyCancelled(order)
                        ? Mono.just(order)
                        : cancelIfReserved(order, reason, traceId));
    }

    private boolean isAlreadyCancelled(TicketOrder order) {
        return TicketStatus.CANCELLED.equals(order.getStatus());
    }

    private Mono<TicketOrder> cancelIfReserved(TicketOrder order, String reason, String traceId) {
        return Mono.just(order)
                .filter(o -> TicketStatus.RESERVED.equals(o.getStatus()))
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_INVALID_STATE_TRANSITION)))
                .flatMap(o -> ticketOrderRepository.cancelOrderTransaction(o, reason, traceId));
    }
}
