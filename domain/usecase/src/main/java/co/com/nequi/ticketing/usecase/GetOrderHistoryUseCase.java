package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.OrderHistoryRepository;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetOrderHistoryUseCase {

    private final TicketOrderRepository ticketOrderRepository;
    private final OrderHistoryRepository orderHistoryRepository;

    public Flux<OrderHistory> execute(String orderId, String userId, boolean isAdmin) {
        return ticketOrderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)))
                .filter(order -> isAdmin || order.getUserId().equals(userId))
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)))
                .flatMapMany(order -> orderHistoryRepository.findByOrderId(orderId));
    }
}
