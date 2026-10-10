package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.usecase.utils.UseCaseConstants;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;

@RequiredArgsConstructor
public class ReleaseExpiredReservationsUseCase {

    private static final Set<TicketStatus> EXPIRABLE_STATUSES = Set.of(TicketStatus.RESERVED, TicketStatus.PENDING_CONFIRMATION);

    private final TicketOrderRepository ticketOrderRepository;

    public Flux<TicketOrder> execute() {
        return executeSweeper();
    }

    public Flux<TicketOrder> executeSweeper() {
        long nowEpoch = Instant.now().getEpochSecond();
        return Flux.range(0, UseCaseConstants.EXPIRY_SHARD_COUNT)
                .map(shardIdx -> UseCaseConstants.SHARD_PREFIX + shardIdx)
                .flatMap(shard -> ticketOrderRepository.findExpiredOrdersByShard(shard, nowEpoch))
                .flatMap(this::expireSingleOrder);
    }

    public Mono<Void> expireOrderById(String orderId, long messageDeadlineAt, String traceId) {
        return ticketOrderRepository.findById(orderId)
                .filter(order -> EXPIRABLE_STATUSES.contains(order.getStatus()))
                .filter(order -> order.getExpiresAt() == null || order.getExpiresAt().getEpochSecond() == messageDeadlineAt)
                .flatMap(this::expireSingleOrder)
                .then();
    }

    private Mono<TicketOrder> expireSingleOrder(TicketOrder order) {
        return ticketOrderRepository.expireOrderTransaction(order, UseCaseConstants.REASON_SYSTEM_EXPIRER);
    }
}
