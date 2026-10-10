package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.OrderHistoryEntity;
import co.com.nequi.ticketing.dynamodb.mapper.OrderHistoryEntityMapper;
import co.com.nequi.ticketing.model.OrderHistory;
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
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DynamoDBOrderHistoryRepositoryAdapterTest {

    private static final String TABLE_NAME = "ticketing-order-history";
    private static final String ORDER_ID = "ord-1";

    @Mock private DynamoDbEnhancedAsyncClient enhancedAsyncClient;
    @Mock private DynamoDbAsyncTable<OrderHistoryEntity> table;
    @Mock private OrderHistoryEntityMapper mapper;

    private DynamoDBOrderHistoryRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        when(enhancedAsyncClient.table(TABLE_NAME, TableSchema.fromBean(OrderHistoryEntity.class))).thenReturn(table);
        adapter = new DynamoDBOrderHistoryRepositoryAdapter(enhancedAsyncClient, mapper, TABLE_NAME);
    }

    @SuppressWarnings("unchecked")
    private PagePublisher<OrderHistoryEntity> pagePublisher(Page<OrderHistoryEntity>... pages) {
        return PagePublisher.create(SdkPublisher.adapt(Flux.just(pages)));
    }

    @Test
    void shouldReturnHistoryForOrder() {
        OrderHistoryEntity entity = OrderHistoryEntity.builder()
                .orderId(ORDER_ID).sequence(1L).toStatus("RESERVED").occurredAt(Instant.now().toString()).build();
        OrderHistory history = OrderHistory.builder().orderId(ORDER_ID).sequence(1L).build();

        doReturn(pagePublisher(Page.create(List.of(entity)))).when(table).query(any(QueryConditional.class));
        when(mapper.toDomain(entity)).thenReturn(history);

        StepVerifier.create(adapter.findByOrderId(ORDER_ID))
                .expectNextMatches(h -> h.getOrderId().equals(ORDER_ID) && h.getSequence() == 1L)
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenNoHistory() {
        doReturn(pagePublisher(Page.create(List.of()))).when(table).query(any(QueryConditional.class));

        StepVerifier.create(adapter.findByOrderId(ORDER_ID))
                .verifyComplete();
    }

    @Test
    void shouldReturnMultipleHistoryEntries() {
        OrderHistoryEntity e1 = OrderHistoryEntity.builder().orderId(ORDER_ID).sequence(1L).build();
        OrderHistoryEntity e2 = OrderHistoryEntity.builder().orderId(ORDER_ID).sequence(2L).build();
        OrderHistory h1 = OrderHistory.builder().orderId(ORDER_ID).sequence(1L).build();
        OrderHistory h2 = OrderHistory.builder().orderId(ORDER_ID).sequence(2L).build();

        doReturn(pagePublisher(Page.create(List.of(e1, e2)))).when(table).query(any(QueryConditional.class));
        when(mapper.toDomain(e1)).thenReturn(h1);
        when(mapper.toDomain(e2)).thenReturn(h2);

        StepVerifier.create(adapter.findByOrderId(ORDER_ID))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldWrapDynamoExceptionAsTechnicalException() {
        doReturn(PagePublisher.create(SdkPublisher.adapt(Flux.<Page<OrderHistoryEntity>>error(new RuntimeException("DynamoDB error")))))
                .when(table).query(any(QueryConditional.class));

        StepVerifier.create(adapter.findByOrderId(ORDER_ID))
                .expectError(TechnicalException.class)
                .verify();
    }
}
