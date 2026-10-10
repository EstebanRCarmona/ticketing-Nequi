package co.com.nequi.ticketing.model.gateways;

import reactor.core.publisher.Mono;

public interface QueueService {
    Mono<Void> sendFinalizeOrderMessage(String orderId, String traceId);
    Mono<Void> sendExpireOrderMessage(String orderId, long deadlineAt, long delaySeconds);
}
