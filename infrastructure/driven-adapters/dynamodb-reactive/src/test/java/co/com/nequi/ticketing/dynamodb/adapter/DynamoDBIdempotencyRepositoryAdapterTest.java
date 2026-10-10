package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.IdempotencyEntity;
import co.com.nequi.ticketing.dynamodb.mapper.IdempotencyEntityMapper;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.test.StepVerifier;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DynamoDBIdempotencyRepositoryAdapterTest {

    private static final String TABLE_NAME = "ticketing-idempotency";
    private static final String IDEMPOTENCY_KEY = "usr-1#POST_ORDERS#key-123";
    private static final String ORDER_ID = "ord-1";
    private static final String REQUEST_HASH = "hash-abc";

    @Mock private DynamoDbEnhancedAsyncClient enhancedAsyncClient;
    @Mock private DynamoDbAsyncTable<IdempotencyEntity> table;
    @Mock private IdempotencyEntityMapper mapper;

    private DynamoDBIdempotencyRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        when(enhancedAsyncClient.table(TABLE_NAME, TableSchema.fromBean(IdempotencyEntity.class))).thenReturn(table);
        adapter = new DynamoDBIdempotencyRepositoryAdapter(enhancedAsyncClient, mapper, TABLE_NAME);
    }

    @Test
    void shouldReturnRecordWhenFound() {
        IdempotencyEntity entity = IdempotencyEntity.builder()
                .idempotencyKey(IDEMPOTENCY_KEY).orderId(ORDER_ID).requestHash(REQUEST_HASH).ttl(9999L).build();
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(IDEMPOTENCY_KEY).orderId(ORDER_ID).requestHash(REQUEST_HASH).build();

        when(table.getItem(any(software.amazon.awssdk.enhanced.dynamodb.Key.class)))
                .thenReturn(CompletableFuture.completedFuture(entity));
        when(mapper.toDomain(entity)).thenReturn(record);

        StepVerifier.create(adapter.findByKey(IDEMPOTENCY_KEY))
                .expectNextMatches(r -> r.getOrderId().equals(ORDER_ID))
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        when(table.getItem(any(software.amazon.awssdk.enhanced.dynamodb.Key.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.findByKey(IDEMPOTENCY_KEY))
                .verifyComplete();
    }

    @Test
    void shouldWrapDynamoExceptionAsTechnicalException() {
        when(table.getItem(any(software.amazon.awssdk.enhanced.dynamodb.Key.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("DynamoDB error")));

        StepVerifier.create(adapter.findByKey(IDEMPOTENCY_KEY))
                .expectError(TechnicalException.class)
                .verify();
    }
}
