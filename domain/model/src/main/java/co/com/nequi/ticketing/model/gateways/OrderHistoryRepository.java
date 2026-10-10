package co.com.nequi.ticketing.model.gateways;

import co.com.nequi.ticketing.model.OrderHistory;
import reactor.core.publisher.Flux;

public interface OrderHistoryRepository {
    Flux<OrderHistory> findByOrderId(String orderId);
}
