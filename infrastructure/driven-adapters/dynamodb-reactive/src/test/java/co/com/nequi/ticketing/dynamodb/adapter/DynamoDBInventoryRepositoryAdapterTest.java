package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.InventoryEntity;
import co.com.nequi.ticketing.dynamodb.mapper.InventoryEntityMapper;
import co.com.nequi.ticketing.model.Inventory;
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
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DynamoDBInventoryRepositoryAdapterTest {

    private static final String TABLE_NAME = "ticketing-inventory";
    private static final String EVENT_ID = "evt-1";

    @Mock private DynamoDbEnhancedAsyncClient enhancedAsyncClient;
    @Mock private DynamoDbAsyncTable<InventoryEntity> table;
    @Mock private InventoryEntityMapper mapper;

    private DynamoDBInventoryRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        when(enhancedAsyncClient.table(TABLE_NAME, TableSchema.fromBean(InventoryEntity.class))).thenReturn(table);
        adapter = new DynamoDBInventoryRepositoryAdapter(enhancedAsyncClient, mapper, TABLE_NAME);
    }

    @Test
    void shouldReturnInventoryWhenFound() {
        InventoryEntity entity = InventoryEntity.builder()
                .eventId(EVENT_ID).available(100).reserved(5).totalCapacity(200).build();
        Inventory inventory = Inventory.builder().eventId(EVENT_ID).available(100).build();

        when(table.getItem(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(entity));
        when(mapper.toDomain(entity)).thenReturn(inventory);

        StepVerifier.create(adapter.findByEventIdConsistent(EVENT_ID))
                .expectNextMatches(inv -> inv.getEventId().equals(EVENT_ID) && inv.getAvailable() == 100)
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        when(table.getItem(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.findByEventIdConsistent(EVENT_ID))
                .verifyComplete();
    }

    @Test
    void shouldWrapDynamoExceptionAsTechnicalException() {
        when(table.getItem(any(Consumer.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("DynamoDB error")));

        StepVerifier.create(adapter.findByEventIdConsistent(EVENT_ID))
                .expectError(TechnicalException.class)
                .verify();
    }
}
