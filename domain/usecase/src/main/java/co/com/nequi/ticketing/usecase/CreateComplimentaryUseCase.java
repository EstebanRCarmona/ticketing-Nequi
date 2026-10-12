package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.usecase.utils.UseCaseConstants;
import co.com.nequi.ticketing.model.enums.ActorType;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.enums.OrderType;
import co.com.nequi.ticketing.model.enums.SeatStatus;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.IdempotencyRepository;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import co.com.nequi.ticketing.model.gateways.SeatRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.HashSet;
import java.util.Optional;

@RequiredArgsConstructor
public class CreateComplimentaryUseCase {

        public record ComplimentaryCommand(String eventId, String adminUserId, List<String> seatIds,
                                                                           String recipient, String reason) {
        }

    private final EventRepository eventRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final SeatRepository seatRepository;

        public Mono<TicketOrder> execute(ComplimentaryCommand command, String idempotencyKey, String requestHash, String traceId) {
                return Optional.ofNullable(command)
                        .map(nonNullCommand -> executeWithCommand(nonNullCommand, idempotencyKey, requestHash, traceId))
                        .orElseGet(() -> Mono.error(new BusinessException(GeneralMessage.VAL_INVALID_INPUT)));
        }

    private Mono<TicketOrder> executeWithCommand(ComplimentaryCommand command, String idempotencyKey,
                                                 String requestHash, String traceId) {
        return Optional.ofNullable(command.seatIds())
                .filter(CreateComplimentaryUseCase::isValidSeatSelection)
                .map(seatIds -> {
                    String fullIdempotencyKey = command.adminUserId()
                            + UseCaseConstants.IDEMPOTENCY_SCOPE_COMPLIMENTARY + idempotencyKey;

                    return idempotencyRepository.findByKey(fullIdempotencyKey)
                            .flatMap(existingRecord -> existingRecord.getRequestHash().equals(requestHash)
                                    ? ticketOrderRepository.findById(existingRecord.getOrderId())
                                    : Mono.error(new BusinessException(GeneralMessage.VAL_IDEMPOTENCY_MISMATCH)))
                            .switchIfEmpty(Mono.defer(() -> createComplimentaryOrder(command, List.copyOf(seatIds),
                                    fullIdempotencyKey, requestHash, traceId)));
                })
                .orElseGet(() -> Mono.error(new BusinessException(GeneralMessage.VAL_INVALID_SEAT_SELECTION)));
    }

    private static boolean isValidSeatSelection(List<String> seatIds) {
        return !seatIds.isEmpty() && seatIds.size() <= 10
                && seatIds.stream().noneMatch(id -> id == null || id.isBlank())
                && new HashSet<>(seatIds).size() == seatIds.size();
    }

    private Mono<TicketOrder> createComplimentaryOrder(ComplimentaryCommand command, List<String> seatIds,
                                                       String fullIdempotencyKey, String requestHash, String traceId) {
        return eventRepository.findById(command.eventId())
                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_EVENT_NOT_FOUND)))
                .flatMap(event -> seatRepository.findSeats(command.eventId(), seatIds).collectList()
                        .flatMap(seats -> Mono.just(seats)
                        .filter(availableSeats -> availableSeats.size() == seatIds.size()
                                && availableSeats.stream().allMatch(seat -> seat.getStatus() == SeatStatus.AVAILABLE))
                        .flatMap(availableSeats -> {
                            Instant now = Instant.now();
                            String orderId = UUID.randomUUID().toString();

                            TicketOrder order = TicketOrder.builder()
                                    .id(orderId)
                                    .eventId(command.eventId())
                                    .userId(command.adminUserId())
                                    .orderType(OrderType.COMPLIMENTARY)
                                    .quantity(seatIds.size())
                                    .seatIds(seatIds)
                                    .unitPrice(BigDecimal.ZERO)
                                    .totalAmount(BigDecimal.ZERO)
                                    .currency(event.getCurrency() != null ? event.getCurrency() : UseCaseConstants.DEFAULT_CURRENCY)
                                    .status(TicketStatus.COMPLIMENTARY)
                                    .recipient(command.recipient())
                                    .complimentaryReason(command.reason())
                                    .traceId(traceId)
                                    .version(UseCaseConstants.INITIAL_VERSION)
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .build();

                            OrderHistory history = OrderHistory.builder()
                                    .orderId(orderId)
                                    .sequence(UseCaseConstants.INITIAL_SEQUENCE)
                                    .fromStatus(null)
                                    .toStatus(TicketStatus.COMPLIMENTARY)
                                    .reasonCode(UseCaseConstants.REASON_COMPLIMENTARY_ISSUED)
                                    .actor(ActorType.ADMIN)
                                    .quantity(seatIds.size())
                                    .seatIds(seatIds)
                                    .traceId(traceId)
                                    .occurredAt(now)
                                    .build();

                            IdempotencyRecord idempotencyRecord = IdempotencyRecord.builder()
                                    .idempotencyKey(fullIdempotencyKey)
                                    .requestHash(requestHash)
                                    .orderId(orderId)
                                    .ttl(now.getEpochSecond() + UseCaseConstants.IDEMPOTENCY_TTL_SECONDS)
                                    .build();

                            return ticketOrderRepository.createComplimentaryTransaction(order, history, idempotencyRecord);
                                                })
                                                .switchIfEmpty(Mono.error(new BusinessException(GeneralMessage.BUS_SEAT_UNAVAILABLE)))));
    }
}
