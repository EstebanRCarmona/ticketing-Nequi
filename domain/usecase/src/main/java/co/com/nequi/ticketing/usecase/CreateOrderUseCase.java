package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.usecase.utils.UseCaseConstants;
import co.com.nequi.ticketing.model.enums.ActorType;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.enums.OrderType;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.IdempotencyRepository;
import co.com.nequi.ticketing.model.gateways.QueueService;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final EventRepository eventRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final QueueService queueService;

    public Mono<TicketOrder> execute(String eventId, String userId, int quantity, String idempotencyKey, String requestHash, String traceId) {
        String fullIdempotencyKey = userId + UseCaseConstants.IDEMPOTENCY_SCOPE_ORDERS + idempotencyKey;

        return idempotencyRepository.findByKey(fullIdempotencyKey)
                .flatMap(record -> record.getRequestHash().equals(requestHash)
                        ? ticketOrderRepository.findById(record.getOrderId())
                        : Mono.error(new BusinessException(GeneralMessage.VAL_IDEMPOTENCY_MISMATCH)))
                .switchIfEmpty(Mono.defer(() -> createNewOrder(eventId, userId, quantity, fullIdempotencyKey, requestHash, traceId)));
    }

    private Mono<TicketOrder> createNewOrder(String eventId, String userId, int quantity, String fullIdempotencyKey, String requestHash, String traceId) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_EVENT_NOT_FOUND)))
                .flatMap(event -> {
                    Instant now = Instant.now();
                    long deadlineEpoch = now.getEpochSecond() + UseCaseConstants.RESERVATION_TTL_SECONDS;
                    String orderId = UUID.randomUUID().toString();
                    String shard = UseCaseConstants.SHARD_PREFIX + ThreadLocalRandom.current().nextInt(UseCaseConstants.EXPIRY_SHARD_COUNT);
                    BigDecimal unitPrice = event.getTicketPrice() != null ? event.getTicketPrice() : BigDecimal.ZERO;
                    BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

                    TicketOrder newOrder = TicketOrder.builder()
                            .id(orderId)
                            .eventId(eventId)
                            .userId(userId)
                            .orderType(OrderType.PURCHASE)
                            .quantity(quantity)
                            .unitPrice(unitPrice)
                            .totalAmount(totalAmount)
                            .currency(event.getCurrency() != null ? event.getCurrency() : UseCaseConstants.DEFAULT_CURRENCY)
                            .status(TicketStatus.RESERVED)
                            .expiresAt(Instant.ofEpochSecond(deadlineEpoch))
                            .expiryShard(shard)
                            .traceId(traceId)
                            .version(UseCaseConstants.INITIAL_VERSION)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    OrderHistory history = OrderHistory.builder()
                            .orderId(orderId)
                            .sequence(UseCaseConstants.INITIAL_SEQUENCE)
                            .fromStatus(null)
                            .toStatus(TicketStatus.RESERVED)
                            .reasonCode(UseCaseConstants.REASON_ORDER_CREATED)
                            .actor(ActorType.API)
                            .quantity(quantity)
                            .traceId(traceId)
                            .occurredAt(now)
                            .build();

                    IdempotencyRecord idempotencyRecord = IdempotencyRecord.builder()
                            .idempotencyKey(fullIdempotencyKey)
                            .requestHash(requestHash)
                            .orderId(orderId)
                            .ttl(now.getEpochSecond() + UseCaseConstants.IDEMPOTENCY_TTL_SECONDS)
                            .build();

                    return ticketOrderRepository.createOrderTransaction(newOrder, history, idempotencyRecord)
                            .retryWhen(Retry.backoff(UseCaseConstants.MAX_RETRIES, Duration.ofMillis(UseCaseConstants.RETRY_MIN_BACKOFF_MS))
                                    .maxBackoff(Duration.ofMillis(UseCaseConstants.RETRY_MAX_BACKOFF_MS))
                                    .jitter(UseCaseConstants.RETRY_JITTER)
                                    .filter(e -> e instanceof BusinessException be && be.getGeneralMessage() == GeneralMessage.BUS_CONCURRENCY_CONFLICT)
                                    .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                            .flatMap(savedOrder -> queueService.sendExpireOrderMessage(orderId, deadlineEpoch, UseCaseConstants.RESERVATION_TTL_SECONDS)
                                    .onErrorResume(e -> Mono.empty())
                                    .thenReturn(savedOrder));
                });
    }
}
