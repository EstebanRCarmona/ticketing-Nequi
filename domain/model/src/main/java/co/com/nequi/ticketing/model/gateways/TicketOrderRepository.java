package co.com.nequi.ticketing.model.gateways;

import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.TicketOrder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TicketOrderRepository {
    Mono<TicketOrder> createOrderTransaction(TicketOrder order, OrderHistory history, IdempotencyRecord idempotency);
    Mono<TicketOrder> createComplimentaryTransaction(TicketOrder order, OrderHistory history, IdempotencyRecord idempotency);
    Mono<TicketOrder> findById(String id);
    Mono<TicketOrder> confirmOrderTransaction(TicketOrder order, String paymentRef, String traceId);
    Mono<TicketOrder> cancelOrderTransaction(TicketOrder order, String reason, String traceId);
    Mono<TicketOrder> finalizeOrderSaleTransaction(TicketOrder order, String traceId, String sqsMessageId);
    Mono<TicketOrder> rejectOrderPaymentTransaction(TicketOrder order, String failureCode, String failureDesc, String traceId);
    Mono<TicketOrder> expireOrderTransaction(TicketOrder order, String traceId);
    Flux<TicketOrder> findExpiredOrdersByShard(String shard, long nowEpoch);
}
