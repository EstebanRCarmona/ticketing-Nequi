package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.IdempotencyEntity;
import co.com.nequi.ticketing.dynamodb.entity.OrderHistoryEntity;
import co.com.nequi.ticketing.dynamodb.entity.TicketOrderEntity;
import co.com.nequi.ticketing.dynamodb.mapper.IdempotencyEntityMapper;
import co.com.nequi.ticketing.dynamodb.mapper.OrderHistoryEntityMapper;
import co.com.nequi.ticketing.dynamodb.mapper.TicketOrderEntityMapper;
import co.com.nequi.ticketing.dynamodb.mapper.SeatEntityMapper;
import co.com.nequi.ticketing.model.enums.ActorType;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.enums.ReleaseReason;
import co.com.nequi.ticketing.model.enums.SeatStatus;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Instant;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DynamoDBTicketOrderRepositoryAdapter implements TicketOrderRepository {

    private static final String ATTRIBUTE_EVENT_ID = "eventId";
    private static final long CONFIRM_TIMEOUT_SECONDS = 300L;
    private static final String REASON_PAYMENT_INITIATED = "PAYMENT_INITIATED";
    private static final String REASON_PAYMENT_APPROVED = "PAYMENT_APPROVED";
    private static final String REASON_PAYMENT_REJECTED = "PAYMENT_REJECTED";
    private static final String REASON_RESERVATION_EXPIRED = "RESERVATION_EXPIRED";
    private static final String INVENTORY_EXPR_RESERVE = "SET available = available - :q, reserved = reserved + :q, updatedAt = :now";
    private static final String INVENTORY_EXPR_COMPLIMENTARY = "SET available = available - :q, complimentary = complimentary + :q, updatedAt = :now";
    private static final String INVENTORY_EXPR_CONFIRM = "SET reserved = reserved - :q, pendingConfirmation = pendingConfirmation + :q, updatedAt = :now";
    private static final String INVENTORY_EXPR_RELEASE_RESERVED = "SET reserved = reserved - :q, available = available + :q, updatedAt = :now";
    private static final String INVENTORY_EXPR_SOLD = "SET pendingConfirmation = pendingConfirmation - :q, sold = sold + :q, updatedAt = :now";
    private static final String INVENTORY_EXPR_RELEASE_PENDING = "SET pendingConfirmation = pendingConfirmation - :q, available = available + :q, updatedAt = :now";
    private static final String INVENTORY_EXPR_NOOP = "SET updatedAt = :now";

    private final String ordersTableName;
    private final String inventoryTableName;
    private final String seatsTableName;

    private final DynamoDbAsyncClient asyncClient;
    private final DynamoDbAsyncTable<TicketOrderEntity> ordersTable;
    private final DynamoDbAsyncTable<IdempotencyEntity> idempotencyTable;
    private final DynamoDbAsyncTable<OrderHistoryEntity> historyTable;
    private final TicketOrderEntityMapper orderMapper;
    private final OrderHistoryEntityMapper historyMapper;
    private final IdempotencyEntityMapper idempotencyMapper;

    public DynamoDBTicketOrderRepositoryAdapter(DynamoDbAsyncClient asyncClient,
                                                DynamoDbEnhancedAsyncClient enhancedAsyncClient,
                                                TicketOrderEntityMapper orderMapper,
                                                OrderHistoryEntityMapper historyMapper,
                                                IdempotencyEntityMapper idempotencyMapper,
                                                @Value("${aws.dynamodb.table-orders:ticketing-orders}") String ordersTableName,
                                                @Value("${aws.dynamodb.table-inventory:ticketing-inventory}") String inventoryTableName,
                                                @Value("${aws.dynamodb.table-order-history:ticketing-order-history}") String historyTableName,
                                                @Value("${aws.dynamodb.table-idempotency:ticketing-idempotency}") String idempotencyTableName,
                                                @Value("${aws.dynamodb.table-seats:ticketing-seats}") String seatsTableName) {
        this.asyncClient = asyncClient;
        this.ordersTable = enhancedAsyncClient.table(ordersTableName, TableSchema.fromBean(TicketOrderEntity.class));
        this.idempotencyTable = enhancedAsyncClient.table(idempotencyTableName, TableSchema.fromBean(IdempotencyEntity.class));
        this.historyTable = enhancedAsyncClient.table(historyTableName, TableSchema.fromBean(OrderHistoryEntity.class));
        this.orderMapper = orderMapper;
        this.historyMapper = historyMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.ordersTableName = ordersTableName;
        this.inventoryTableName = inventoryTableName;
        this.seatsTableName = seatsTableName;
    }

    @Override
    public Mono<TicketOrder> createOrderTransaction(TicketOrder order, OrderHistory history, IdempotencyRecord idempotency) {
        return buildCreateTransaction(
                order, history, idempotency,
                INVENTORY_EXPR_RESERVE,
                SeatStatus.RESERVED
        );
    }

    @Override
    public Mono<TicketOrder> createComplimentaryTransaction(TicketOrder order, OrderHistory history, IdempotencyRecord idempotency) {
        return buildCreateTransaction(
                order, history, idempotency,
                INVENTORY_EXPR_COMPLIMENTARY,
                SeatStatus.COMPLIMENTARY
        );
    }

    private Mono<TicketOrder> buildCreateTransaction(TicketOrder order, OrderHistory history,
                                                     IdempotencyRecord idempotency,
                                                     String inventoryExpr,
                                                     SeatStatus seatStatus) {
                if (!hasValidSeatIds(order)) {
                        return Mono.error(new BusinessException(GeneralMessage.VAL_INVALID_SEAT_SELECTION));
                }
        IdempotencyEntity idempotencyEntity = idempotencyMapper.toEntity(idempotency);
        TicketOrderEntity orderEntity = orderMapper.toEntity(order);
        OrderHistoryEntity historyEntity = historyMapper.toEntity(history);
        TableSchema<IdempotencyEntity> idempotencySchema = TableSchema.fromBean(IdempotencyEntity.class);
        TableSchema<TicketOrderEntity> orderSchema = TableSchema.fromBean(TicketOrderEntity.class);
        TableSchema<OrderHistoryEntity> historySchema = TableSchema.fromBean(OrderHistoryEntity.class);
        Map<String, AttributeValue> inventoryKey = Map.of(ATTRIBUTE_EVENT_ID, AttributeValue.builder().s(order.getEventId()).build());
        Map<String, AttributeValue> inventoryValues = Map.of(
                ":q", AttributeValue.builder().n(order.getQuantity().toString()).build(),
                ":now", AttributeValue.builder().s(Instant.now().toString()).build());

        List<TransactWriteItem> transactionItems = new ArrayList<>();
        transactionItems.add(TransactWriteItem.builder().put(p -> p
                                .tableName(idempotencyTable.tableName())
                                .item(idempotencySchema.itemToMap(idempotencyEntity, true))
                                .conditionExpression("attribute_not_exists(idempotencyKey)")
                        ).build());
        transactionItems.add(TransactWriteItem.builder().update(Update.builder()
                                .tableName(inventoryTableName)
                                .key(inventoryKey)
                                .updateExpression(inventoryExpr)
                                .expressionAttributeValues(inventoryValues)
                                .conditionExpression("available >= :q")
                                .build()).build());
        transactionItems.add(TransactWriteItem.builder().put(p -> p
                                .tableName(ordersTable.tableName())
                                .item(orderSchema.itemToMap(orderEntity, true))
                                .conditionExpression("attribute_not_exists(orderId)")
                        ).build());
        for (String seatId : Optional.ofNullable(order.getSeatIds()).orElse(List.of())) {
            transactionItems.add(buildSeatUpdateItem(order.getEventId(), seatId, SeatStatus.AVAILABLE,
                    seatStatus, order.getId(), Instant.now()));
        }
        transactionItems.add(TransactWriteItem.builder().put(p -> p
                .tableName(historyTable.tableName())
                .item(historySchema.itemToMap(historyEntity, true))
                .conditionExpression("attribute_not_exists(orderId) AND attribute_not_exists(#seq)")
                .expressionAttributeNames(Map.of("#seq", "sequence"))
        ).build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(transactionItems)
                .build();

        return Mono.fromFuture(() -> asyncClient.transactWriteItems(request))
                .thenReturn(order)
                .onErrorResume(TransactionCanceledException.class, e -> {
                    if (isInventoryConditionalFailed(e)) {
                        return Mono.error(new BusinessException(GeneralMessage.BUS_INSUFFICIENT_INVENTORY));
                    }
                    if (isSeatConditionalFailed(e, order.getSeatIds() == null ? 0 : order.getSeatIds().size())) {
                        return Mono.error(new BusinessException(GeneralMessage.BUS_SEAT_UNAVAILABLE));
                    }
                    return Mono.error(new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
                })
                .onErrorMap(e -> !(e instanceof BusinessException), e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    private boolean isInventoryConditionalFailed(TransactionCanceledException e) {
        return e.cancellationReasons() != null && e.cancellationReasons().size() > 1
                && "ConditionalCheckFailed".equals(e.cancellationReasons().get(1).code() != null ? e.cancellationReasons().get(1).code() : "");
    }

    private boolean isSeatConditionalFailed(TransactionCanceledException error, int seatCount) {
        if (error.cancellationReasons() == null || seatCount == 0) {
            return false;
        }
        return error.cancellationReasons().stream().skip(3).limit(seatCount)
                .anyMatch(reason -> "ConditionalCheckFailed".equals(reason.code()));
        }

    @Override
    public Mono<TicketOrder> findById(String id) {
        return Mono.fromFuture(() -> ordersTable.getItem(Key.builder().partitionValue(id).build()))
                .map(orderMapper::toDomain)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<TicketOrder> confirmOrderTransaction(TicketOrder order, String paymentRef, String traceId) {
        Instant now = Instant.now();
        TicketOrder updated = order.toBuilder()
                .status(TicketStatus.PENDING_CONFIRMATION)
                .expiresAt(Instant.ofEpochSecond(now.getEpochSecond() + CONFIRM_TIMEOUT_SECONDS))
                .paymentReference(paymentRef)
                .version(order.getVersion() + 1)
                .updatedAt(now)
                .build();
        return transactionalUpdateOrder(order, updated, REASON_PAYMENT_INITIATED, traceId, ActorType.API)
                .onErrorResume(TransactionCanceledException.class,
                        e -> Mono.error(new BusinessException(GeneralMessage.BUS_CONCURRENCY_CONFLICT)))
                .onErrorMap(e -> !(e instanceof BusinessException), e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<TicketOrder> cancelOrderTransaction(TicketOrder order, String reason, String traceId) {
        Instant now = Instant.now();
        TicketOrder updated = order.toBuilder()
                .status(TicketStatus.CANCELLED)
                .releaseReason(ReleaseReason.CANCELLED_BY_USER)
                .expiresAt(null)
                .expiryShard(null)
                .version(order.getVersion() + 1)
                .updatedAt(now)
                .build();
        return transactionalUpdateOrder(order, updated, reason, traceId, ActorType.API)
                .onErrorResume(TransactionCanceledException.class,
                        e -> Mono.error(new BusinessException(GeneralMessage.BUS_CONCURRENCY_CONFLICT)))
                .onErrorMap(e -> !(e instanceof BusinessException), e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<TicketOrder> finalizeOrderSaleTransaction(TicketOrder order, String traceId, String sqsMessageId) {
        Instant now = Instant.now();
        TicketOrder updated = order.toBuilder()
                .status(TicketStatus.SOLD)
                .expiresAt(null)
                .expiryShard(null)
                .version(order.getVersion() + 1)
                .updatedAt(now)
                .build();
        return transactionalUpdateOrder(order, updated, REASON_PAYMENT_APPROVED, traceId, ActorType.CONSUMER)
                .onErrorResume(TransactionCanceledException.class,
                        e -> Mono.error(new BusinessException(GeneralMessage.BUS_CONCURRENCY_CONFLICT)))
                .onErrorMap(e -> !(e instanceof BusinessException), e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<TicketOrder> rejectOrderPaymentTransaction(TicketOrder order, String failureCode, String failureDesc, String traceId) {
        Instant now = Instant.now();
        TicketOrder updated = order.toBuilder()
                .status(TicketStatus.CANCELLED)
                .releaseReason(ReleaseReason.PAYMENT_REJECTED)
                .failureCode(failureCode)
                .failureDescription(failureDesc)
                .expiresAt(null)
                .expiryShard(null)
                .version(order.getVersion() + 1)
                .updatedAt(now)
                .build();
        return transactionalUpdateOrder(order, updated, failureCode != null ? failureCode : REASON_PAYMENT_REJECTED, traceId, ActorType.CONSUMER)
                .onErrorResume(TransactionCanceledException.class,
                        e -> Mono.error(new BusinessException(GeneralMessage.BUS_CONCURRENCY_CONFLICT)))
                .onErrorMap(e -> !(e instanceof BusinessException), e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<TicketOrder> expireOrderTransaction(TicketOrder order, String traceId) {
        Instant now = Instant.now();
        TicketOrder updated = order.toBuilder()
                .status(TicketStatus.EXPIRED)
                .releaseReason(ReleaseReason.EXPIRED)
                .expiresAt(null)
                .expiryShard(null)
                .version(order.getVersion() + 1)
                .updatedAt(now)
                .build();
        return transactionalUpdateOrder(order, updated, REASON_RESERVATION_EXPIRED, traceId, ActorType.EXPIRER)
                .onErrorResume(TransactionCanceledException.class, e -> Mono.empty())
                .onErrorMap(e -> !(e instanceof BusinessException), e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    private Mono<TicketOrder> transactionalUpdateOrder(TicketOrder before, TicketOrder updated, String reasonCode, String traceId, ActorType actor) {
                if (!hasValidSeatIds(before)) {
                        return Mono.error(new BusinessException(GeneralMessage.BUS_INVALID_STATE_TRANSITION));
                }
        TicketOrderEntity entity = orderMapper.toEntity(updated);
        long expectedVersion = before.getVersion();

        Map<String, AttributeValue> orderKey = Map.of("orderId", AttributeValue.builder().s(before.getId()).build());
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":status", AttributeValue.builder().s(entity.getStatus()).build());
        values.put(":newVersion", AttributeValue.builder().n(String.valueOf(expectedVersion + 1)).build());
        values.put(":expectedVersion", AttributeValue.builder().n(String.valueOf(expectedVersion)).build());
        values.put(":now", AttributeValue.builder().s(entity.getUpdatedAt()).build());
        Optional.ofNullable(updated.getExpiresAt()).ifPresent(v -> values.put(":deadline", AttributeValue.builder().n(String.valueOf(v.getEpochSecond())).build()));
        Optional.ofNullable(entity.getPaymentReference()).ifPresent(v -> values.put(":payRef", AttributeValue.builder().s(v).build()));
        Optional.ofNullable(entity.getReleaseReason()).ifPresent(v -> values.put(":releaseReason", AttributeValue.builder().s(v).build()));
        Optional.ofNullable(entity.getFailureCode()).ifPresent(v -> values.put(":failureCode", AttributeValue.builder().s(v).build()));
        Optional.ofNullable(entity.getFailureDescription()).ifPresent(v -> values.put(":failureDesc", AttributeValue.builder().s(v).build()));

        TableSchema<OrderHistoryEntity> historySchema = TableSchema.fromBean(OrderHistoryEntity.class);
        OrderHistoryEntity historyEntity = historyMapper.toTransitionEntity(before, updated, reasonCode, traceId, actor);

        Map<String, AttributeValue> inventoryKey = Map.of(ATTRIBUTE_EVENT_ID, AttributeValue.builder().s(before.getEventId()).build());
        Map<String, AttributeValue> inventoryValues = Map.of(
                ":q", AttributeValue.builder().n(before.getQuantity().toString()).build(),
                ":now", AttributeValue.builder().s(entity.getUpdatedAt()).build()
        );

        List<TransactWriteItem> transactionItems = new ArrayList<>();
        transactionItems.add(TransactWriteItem.builder().update(Update.builder()
                                .tableName(ordersTableName)
                                .key(orderKey)
                                .updateExpression(buildUpdateExpression(entity))
                                .expressionAttributeNames(Map.of("#st", "status", "#ver", "version"))
                                .expressionAttributeValues(values)
                                .conditionExpression("#ver = :expectedVersion")
                                .build()).build());
        transactionItems.add(TransactWriteItem.builder().update(Update.builder()
                                .tableName(inventoryTableName)
                                .key(inventoryKey)
                                .updateExpression(buildInventoryUpdateExpression(before.getStatus(), updated.getStatus()))
                                .expressionAttributeValues(inventoryValues)
                                .build()).build());
        for (String seatId : Optional.ofNullable(before.getSeatIds()).orElse(List.of())) {
            transactionItems.add(buildSeatUpdateItem(before.getEventId(), seatId,
                    mapSeatStatus(before.getStatus()), mapSeatStatus(updated.getStatus()),
                    before.getId(), updated.getUpdatedAt()));
        }
        transactionItems.add(TransactWriteItem.builder().put(p -> p
                                .tableName(historyTable.tableName())
                                .item(historySchema.itemToMap(historyEntity, true))
                                .conditionExpression("attribute_not_exists(orderId) AND attribute_not_exists(#seq)")
                                .expressionAttributeNames(Map.of("#seq", "sequence"))
                        ).build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(transactionItems)
                .build();

        return Mono.fromFuture(() -> asyncClient.transactWriteItems(request))
                .thenReturn(updated);
    }

    private boolean hasValidSeatIds(TicketOrder order) {
        List<String> seatIds = order.getSeatIds();
        return seatIds != null && !seatIds.isEmpty() && seatIds.size() <= 10
                && order.getQuantity() != null && seatIds.size() == order.getQuantity()
                && new HashSet<>(seatIds).size() == seatIds.size();
    }

    private TransactWriteItem buildSeatUpdateItem(String eventId, String seatId, SeatStatus fromStatus,
                                                   SeatStatus toStatus, String orderId, Instant now) {
        int seatShard = Math.floorMod(seatId.hashCode(), SeatEntityMapper.SEAT_SHARD_COUNT);
        Map<String, AttributeValue> key = Map.of(
                "seatKey", AttributeValue.builder().s(eventId + "#S#" + seatShard).build(),
                "seatId", AttributeValue.builder().s(seatId).build());
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":fromStatus", AttributeValue.builder().s(fromStatus.name()).build());
        values.put(":toStatus", AttributeValue.builder().s(toStatus.name()).build());
        values.put(":statusKey", AttributeValue.builder().s(eventId + "#" + toStatus.name() + "#S#" + seatShard).build());
        values.put(":now", AttributeValue.builder().s(now.toString()).build());
        String updateExpression = "SET #st = :toStatus, statusKey = :statusKey, updatedAt = :now";
        if (toStatus == SeatStatus.AVAILABLE) {
            updateExpression += " REMOVE orderId";
        } else {
            values.put(":orderId", AttributeValue.builder().s(orderId).build());
            updateExpression += ", orderId = :orderId";
        }
        return TransactWriteItem.builder().update(Update.builder()
                .tableName(seatsTableName)
                .key(key)
                .updateExpression(updateExpression)
                .expressionAttributeNames(Map.of("#st", "status"))
                .expressionAttributeValues(values)
                .conditionExpression("#st = :fromStatus")
                .build()).build();
    }

    private SeatStatus mapSeatStatus(TicketStatus orderStatus) {
        return switch (orderStatus) {
            case RESERVED -> SeatStatus.RESERVED;
            case PENDING_CONFIRMATION -> SeatStatus.PENDING_CONFIRMATION;
            case SOLD -> SeatStatus.SOLD;
            case COMPLIMENTARY -> SeatStatus.COMPLIMENTARY;
            case CANCELLED, EXPIRED -> SeatStatus.AVAILABLE;
        };
    }

    private String buildInventoryUpdateExpression(TicketStatus from, TicketStatus to) {
        return switch (from) {
            case RESERVED -> switch (to) {
                case PENDING_CONFIRMATION -> INVENTORY_EXPR_CONFIRM;
                default ->                  INVENTORY_EXPR_RELEASE_RESERVED;
            };
            case PENDING_CONFIRMATION -> switch (to) {
                case SOLD ->    INVENTORY_EXPR_SOLD;
                default ->      INVENTORY_EXPR_RELEASE_PENDING;
            };
            default -> INVENTORY_EXPR_NOOP;
        };
    }

    private String buildUpdateExpression(TicketOrderEntity entity) {
        StringBuilder sb = new StringBuilder("SET #st = :status, #ver = :newVersion, updatedAt = :now");
        Optional.ofNullable(entity.getDeadlineAt()).ifPresent(v -> sb.append(", deadlineAt = :deadline"));
        Optional.ofNullable(entity.getPaymentReference()).ifPresent(v -> sb.append(", paymentReference = :payRef"));
        Optional.ofNullable(entity.getReleaseReason()).ifPresent(v -> sb.append(", releaseReason = :releaseReason"));
        Optional.ofNullable(entity.getFailureCode()).ifPresent(v -> sb.append(", failureCode = :failureCode"));
        Optional.ofNullable(entity.getFailureDescription()).ifPresent(v -> sb.append(", failureDescription = :failureDesc"));
        if (entity.getDeadlineAt() == null) sb.append(" REMOVE deadlineAt, expiryShard");
        return sb.toString();
    }

    @Override
    public Flux<TicketOrder> findExpiredOrdersByShard(String shard, long nowEpoch) {
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(shard).build()))
                .build();

        return Flux.from(ordersTable.index("gsi-orders-deadline").query(queryRequest))
                .flatMapIterable(page -> page.items())
                .map(orderMapper::toDomain)
                .filter(o -> o.getExpiresAt() != null && o.getExpiresAt().getEpochSecond() <= nowEpoch)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }
}
