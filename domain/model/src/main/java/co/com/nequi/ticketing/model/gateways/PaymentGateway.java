package co.com.nequi.ticketing.model.gateways;

import reactor.core.publisher.Mono;

public interface PaymentGateway {
    Mono<Boolean> charge(String orderId, String paymentRef);
}
