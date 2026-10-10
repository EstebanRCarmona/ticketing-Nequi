package co.com.nequi.ticketing.model.gateways;

import co.com.nequi.ticketing.model.IdempotencyRecord;
import reactor.core.publisher.Mono;

public interface IdempotencyRepository {
    Mono<IdempotencyRecord> findByKey(String key);
}
