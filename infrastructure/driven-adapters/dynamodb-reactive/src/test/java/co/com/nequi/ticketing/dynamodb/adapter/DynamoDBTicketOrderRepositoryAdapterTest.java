package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.IdempotencyEntity;
import co.com.nequi.ticketing.dynamodb.entity.OrderHistoryEntity;
import co.com.nequi.ticketing.dynamodb.entity.TicketOrderEntity;
import co.com.nequi.ticketing.dynamodb.mapper.IdempotencyEntityMapper;
import co.com.nequi.ticketing.dynamodb.mapper.OrderHistoryEntityMapper;
import co.com.nequi.ticketing.dynamodb.mapper.TicketOrderEntityMapper;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.ActorType;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DynamoDBTicketOrderRepositoryAdapterTest {

    private static final String ORDERS_TABLE = "ticketing-orders";
    private static final String INVENTORY_TABLE = "ticketing-inventory";
    private static final String HISTORY_TABLE = "ticketing-order-history";
    private static final String IDEMPOTENCY_TABLE = "ticketing-idempotency";
    private static final String ORDER_ID = "ord-1";
    private static final String EVENT_ID = "evt-1";
    private static final String USER_ID = "usr-1";
    private static final String TRACE_ID = "trace-1";
    private static final String PAYMENT_REF = "tok_visa";
    private static final String SHARD = "S#0";

    @Mock private DynamoDbAsyncClient asyncClient;
    @Mock private DynamoDbEnhancedAsyncClient enhancedAsyncClient;
    @Mock private DynamoDbAsyncTable<TicketOrderEntity> ordersTable;
    @Mock private DynamoDbAsyncTable<IdempotencyEntity> idempotencyTable;
    @Mock private DynamoDbAsyncTable<OrderHistoryEntity> historyTable;
    @Mock private DynamoDbAsyncIndex<TicketOrderEntity> gsiIndex;
    @Mock private TicketOrderEntityMapper orderMapper;
    @Mock private OrderHistoryEntityMapper historyMapper;
    @Mock private IdempotencyEntityMapper idempotencyMapper;

    private DynamoDBTicketOrderRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        when(enhancedAsyncClient.table(ORDERS_TABLE, TableSchema.fromBean(TicketOrderEntity.class))).thenReturn(ordersTable);
        when(enhancedAsyncClient.table(IDEMPOTENCY_TABLE, TableSchema.fromBean(IdempotencyEntity.class))).thenReturn(idempotencyTable);
        when(enhancedAsyncClient.table(HISTORY_TABLE, TableSchema.fromBean(OrderHistoryEntity.class))).thenReturn(historyTable);
        when(ordersTable.tableName()).thenReturn(ORDERS_TABLE);
        when(idempotencyTable.tableName()).thenReturn(IDEMPOTENCY_TABLE);
        when(historyTable.tableName()).thenReturn(HISTORY_TABLE);

        adapter = new DynamoDBTicketOrderRepositoryAdapter(
                asyncClient, enhancedAsyncClient, orderMapper, historyMapper, idempotencyMapper,
                ORDERS_TABLE, INVENTORY_TABLE, HISTORY_TABLE, IDEMPOTENCY_TABLE);
    }

    private TicketOrder reservedOrder() {
        return TicketOrder.builder()
                .id(ORDER_ID).eventId(EVENT_ID).userId(USER_ID)
                .status(TicketStatus.RESERVED).quantity(2)
                .unitPrice(BigDecimal.valueOf(100)).totalAmount(BigDecimal.valueOf(200))
                .version(1L).createdAt(Instant.now()).updatedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600)).expiryShard(SHARD)
                .build();
    }

    private TicketOrder pendingOrder() {
        return reservedOrder().toBuilder()
                .status(TicketStatus.PENDING_CONFIRMATION)
                .paymentReference(PAYMENT_REF)
                .version(2L).build();
    }

    private void mockTransactWrite() {
        when(asyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));
    }

    private void mockOrderEntity(TicketOrder order) {
        TicketOrderEntity entity = TicketOrderEntity.builder()
                .orderId(order.getId()).status(order.getStatus().name())
                .updatedAt(Instant.now().toString()).version(order.getVersion()).build();
        when(orderMapper.toEntity(any())).thenReturn(entity);
    }

    private void mockHistoryEntity() {
        when(historyMapper.toTransitionEntity(any(), any(), anyString(), anyString(), any(ActorType.class)))
                .thenReturn(OrderHistoryEntity.builder().orderId(ORDER_ID).sequence(2L).build());
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void shouldFindOrderById() {
        TicketOrder order = reservedOrder();
        TicketOrderEntity entity = TicketOrderEntity.builder().orderId(ORDER_ID).status("RESERVED").build();

        when(ordersTable.getItem(any(software.amazon.awssdk.enhanced.dynamodb.Key.class)))
                .thenReturn(CompletableFuture.completedFuture(entity));
        when(orderMapper.toDomain(entity)).thenReturn(order);

        StepVerifier.create(adapter.findById(ORDER_ID))
                .expectNextMatches(o -> o.getId().equals(ORDER_ID))
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenOrderNotFound() {
        when(ordersTable.getItem(any(software.amazon.awssdk.enhanced.dynamodb.Key.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(orderMapper.toDomain(null)).thenReturn(null);

        StepVerifier.create(adapter.findById(ORDER_ID))
                .verifyComplete();
    }

    @Test
    void shouldWrapExceptionOnFindById() {
        when(ordersTable.getItem(any(software.amazon.awssdk.enhanced.dynamodb.Key.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("DynamoDB error")));

        StepVerifier.create(adapter.findById(ORDER_ID))
                .expectError(TechnicalException.class)
                .verify();
    }

    // ── createOrderTransaction ────────────────────────────────────────────────

    @Test
    void shouldCreateOrderTransactionSuccessfully() {
        TicketOrder order = reservedOrder();
        OrderHistory history = OrderHistory.builder().orderId(ORDER_ID).sequence(1L).build();
        IdempotencyRecord idempotency = IdempotencyRecord.builder().idempotencyKey("key").requestHash("hash").orderId(ORDER_ID).ttl(9999L).build();

        when(orderMapper.toEntity(order)).thenReturn(TicketOrderEntity.builder().orderId(ORDER_ID).status("RESERVED").build());
        when(historyMapper.toEntity(history)).thenReturn(OrderHistoryEntity.builder().orderId(ORDER_ID).sequence(1L).build());
        when(idempotencyMapper.toEntity(idempotency)).thenReturn(IdempotencyEntity.builder().idempotencyKey("key").requestHash("hash").orderId(ORDER_ID).ttl(9999L).build());
        mockTransactWrite();

        StepVerifier.create(adapter.createOrderTransaction(order, history, idempotency))
                .expectNextMatches(o -> o.getId().equals(ORDER_ID))
                .verifyComplete();
    }

    @Test
    void shouldFailWithInsufficientInventoryOnCreateOrder() {
        TicketOrder order = reservedOrder();
        OrderHistory history = OrderHistory.builder().orderId(ORDER_ID).sequence(1L).build();
        IdempotencyRecord idempotency = IdempotencyRecord.builder().idempotencyKey("key").requestHash("hash").orderId(ORDER_ID).ttl(9999L).build();

        when(orderMapper.toEntity(order)).thenReturn(TicketOrderEntity.builder().orderId(ORDER_ID).status("RESERVED").build());
        when(historyMapper.toEntity(history)).thenReturn(OrderHistoryEntity.builder().orderId(ORDER_ID).sequence(1L).build());
        when(idempotencyMapper.toEntity(idempotency)).thenReturn(IdempotencyEntity.builder().idempotencyKey("key").requestHash("hash").orderId(ORDER_ID).ttl(9999L).build());

        TransactionCanceledException ex = TransactionCanceledException.builder()
                .cancellationReasons(
                        CancellationReason.builder().code("None").build(),
                        CancellationReason.builder().code("ConditionalCheckFailed").build()
                ).build();
        when(asyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(ex));

        StepVerifier.create(adapter.createOrderTransaction(order, history, idempotency))
                .expectError(BusinessException.class)
                .verify();
    }

    // ── createComplimentaryTransaction ────────────────────────────────────────

    @Test
    void shouldCreateComplimentaryTransactionSuccessfully() {
        TicketOrder order = reservedOrder().toBuilder().status(TicketStatus.COMPLIMENTARY).build();
        OrderHistory history = OrderHistory.builder().orderId(ORDER_ID).sequence(1L).build();
        IdempotencyRecord idempotency = IdempotencyRecord.builder().idempotencyKey("key").requestHash("hash").orderId(ORDER_ID).ttl(9999L).build();

        when(orderMapper.toEntity(order)).thenReturn(TicketOrderEntity.builder().orderId(ORDER_ID).status("COMPLIMENTARY").build());
        when(historyMapper.toEntity(history)).thenReturn(OrderHistoryEntity.builder().orderId(ORDER_ID).sequence(1L).build());
        when(idempotencyMapper.toEntity(idempotency)).thenReturn(IdempotencyEntity.builder().idempotencyKey("key").requestHash("hash").orderId(ORDER_ID).ttl(9999L).build());
        mockTransactWrite();

        StepVerifier.create(adapter.createComplimentaryTransaction(order, history, idempotency))
                .expectNextMatches(o -> o.getId().equals(ORDER_ID))
                .verifyComplete();
    }

    // ── confirmOrderTransaction ───────────────────────────────────────────────

    @Test
    void shouldConfirmOrderSuccessfully() {
        TicketOrder order = reservedOrder();
        mockOrderEntity(order);
        mockHistoryEntity();
        mockTransactWrite();

        StepVerifier.create(adapter.confirmOrderTransaction(order, PAYMENT_REF, TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.PENDING_CONFIRMATION)
                .verifyComplete();
    }

    @Test
    void shouldFailWithConcurrencyConflictOnConfirm() {
        TicketOrder order = reservedOrder();
        mockOrderEntity(order);
        mockHistoryEntity();

        TransactionCanceledException ex = TransactionCanceledException.builder()
                .cancellationReasons(CancellationReason.builder().code("ConditionalCheckFailed").build())
                .build();
        when(asyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(ex));

        StepVerifier.create(adapter.confirmOrderTransaction(order, PAYMENT_REF, TRACE_ID))
                .expectError(BusinessException.class)
                .verify();
    }

    // ── cancelOrderTransaction ────────────────────────────────────────────────

    @Test
    void shouldCancelOrderSuccessfully() {
        TicketOrder order = reservedOrder();
        mockOrderEntity(order);
        mockHistoryEntity();
        mockTransactWrite();

        StepVerifier.create(adapter.cancelOrderTransaction(order, "CUSTOMER_REQUEST", TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.CANCELLED)
                .verifyComplete();
    }

    // ── finalizeOrderSaleTransaction ──────────────────────────────────────────

    @Test
    void shouldFinalizeOrderSaleSuccessfully() {
        TicketOrder order = pendingOrder();
        mockOrderEntity(order);
        mockHistoryEntity();
        mockTransactWrite();

        StepVerifier.create(adapter.finalizeOrderSaleTransaction(order, TRACE_ID, "msg-1"))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.SOLD)
                .verifyComplete();
    }

    // ── rejectOrderPaymentTransaction ─────────────────────────────────────────

    @Test
    void shouldRejectOrderPaymentSuccessfully() {
        TicketOrder order = pendingOrder();
        mockOrderEntity(order);
        mockHistoryEntity();
        mockTransactWrite();

        StepVerifier.create(adapter.rejectOrderPaymentTransaction(order, "BUS006", "Payment rejected", TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.CANCELLED)
                .verifyComplete();
    }

    @Test
    void shouldRejectOrderPaymentWithNullFailureCode() {
        TicketOrder order = pendingOrder();
        mockOrderEntity(order);
        mockHistoryEntity();
        mockTransactWrite();

        StepVerifier.create(adapter.rejectOrderPaymentTransaction(order, null, "Payment rejected", TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.CANCELLED)
                .verifyComplete();
    }

    // ── expireOrderTransaction ────────────────────────────────────────────────

    @Test
    void shouldExpireOrderSuccessfully() {
        TicketOrder order = reservedOrder();
        mockOrderEntity(order);
        mockHistoryEntity();
        mockTransactWrite();

        StepVerifier.create(adapter.expireOrderTransaction(order, TRACE_ID))
                .expectNextMatches(o -> o.getStatus() == TicketStatus.EXPIRED)
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyOnConcurrencyConflictDuringExpire() {
        TicketOrder order = reservedOrder();
        mockOrderEntity(order);
        mockHistoryEntity();

        TransactionCanceledException ex = TransactionCanceledException.builder()
                .cancellationReasons(CancellationReason.builder().code("ConditionalCheckFailed").build())
                .build();
        when(asyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(ex));

        StepVerifier.create(adapter.expireOrderTransaction(order, TRACE_ID))
                .verifyComplete();
    }

    // ── findExpiredOrdersByShard ──────────────────────────────────────────────

    @Test
    void shouldFindExpiredOrdersByShard() {
        long nowEpoch = Instant.now().getEpochSecond();
        TicketOrder expiredOrder = reservedOrder().toBuilder()
                .expiresAt(Instant.ofEpochSecond(nowEpoch - 10)).build();
        TicketOrderEntity entity = TicketOrderEntity.builder().orderId(ORDER_ID).expiryShard(SHARD)
                .deadlineAt(nowEpoch - 10).build();
        Page<TicketOrderEntity> page = Page.create(List.of(entity));

        when(ordersTable.index("gsi-orders-deadline")).thenReturn(gsiIndex);
        doReturn(PagePublisher.create(SdkPublisher.adapt(Flux.just(page)))).when(gsiIndex).query(any(QueryEnhancedRequest.class));
        when(orderMapper.toDomain(entity)).thenReturn(expiredOrder);

        StepVerifier.create(adapter.findExpiredOrdersByShard(SHARD, nowEpoch))
                .expectNextMatches(o -> o.getId().equals(ORDER_ID))
                .verifyComplete();
    }

    @Test
    void shouldFilterOutNonExpiredOrders() {
        long nowEpoch = Instant.now().getEpochSecond();
        TicketOrder futureOrder = reservedOrder().toBuilder()
                .expiresAt(Instant.ofEpochSecond(nowEpoch + 600)).build();
        TicketOrderEntity entity = TicketOrderEntity.builder().orderId(ORDER_ID).expiryShard(SHARD)
                .deadlineAt(nowEpoch + 600).build();
        Page<TicketOrderEntity> page = Page.create(List.of(entity));

        when(ordersTable.index("gsi-orders-deadline")).thenReturn(gsiIndex);
        doReturn(PagePublisher.create(SdkPublisher.adapt(Flux.just(page)))).when(gsiIndex).query(any(QueryEnhancedRequest.class));
        when(orderMapper.toDomain(entity)).thenReturn(futureOrder);

        StepVerifier.create(adapter.findExpiredOrdersByShard(SHARD, nowEpoch))
                .verifyComplete();
    }

    @Test
    void shouldWrapExceptionOnFindExpiredOrdersByShard() {
        when(ordersTable.index("gsi-orders-deadline")).thenReturn(gsiIndex);
        doReturn(PagePublisher.create(SdkPublisher.adapt(Flux.<Page<TicketOrderEntity>>error(new RuntimeException("DynamoDB error")))))
                .when(gsiIndex).query(any(QueryEnhancedRequest.class));

        StepVerifier.create(adapter.findExpiredOrdersByShard(SHARD, Instant.now().getEpochSecond()))
                .expectError(TechnicalException.class)
                .verify();
    }
}
