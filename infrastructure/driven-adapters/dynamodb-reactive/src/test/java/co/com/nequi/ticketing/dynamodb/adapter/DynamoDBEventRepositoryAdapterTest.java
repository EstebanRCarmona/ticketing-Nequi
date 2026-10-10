package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.EventEntity;
import co.com.nequi.ticketing.dynamodb.entity.InventoryEntity;
import co.com.nequi.ticketing.dynamodb.mapper.EventEntityMapper;
import co.com.nequi.ticketing.dynamodb.mapper.InventoryEntityMapper;
import co.com.nequi.ticketing.model.Event;
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
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DynamoDBEventRepositoryAdapterTest {

    private static final String EVENTS_TABLE = "ticketing-events";
    private static final String INVENTORY_TABLE = "ticketing-inventory";
    private static final String EVENT_ID = "evt-1";

    @Mock private DynamoDbAsyncClient dynamoDbAsyncClient;
    @Mock private EventEntityMapper eventMapper;
    @Mock private InventoryEntityMapper inventoryMapper;

    private DynamoDBEventRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DynamoDbEnhancedAsyncClient enhancedAsyncClient = DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(dynamoDbAsyncClient)
                .build();
        adapter = new DynamoDBEventRepositoryAdapter(enhancedAsyncClient, eventMapper, inventoryMapper, EVENTS_TABLE, INVENTORY_TABLE);
    }

    @Test
    void shouldCreateEventWithInventorySuccessfully() {
        Event event = Event.builder().id(EVENT_ID).name("Concert").totalCapacity(100).ticketPrice(BigDecimal.TEN).currency("COP").build();
        Inventory inventory = Inventory.builder().eventId(EVENT_ID).available(100).reserved(0)
                .pendingConfirmation(0).sold(0).complimentary(0).totalCapacity(100).build();

        when(eventMapper.toEntity(event)).thenReturn(
                EventEntity.builder().eventId(EVENT_ID).name("Concert").totalCapacity(100)
                        .ticketPrice(BigDecimal.TEN).currency("COP").listingKey("EVENT").build());
        when(inventoryMapper.toEntity(inventory)).thenReturn(
                InventoryEntity.builder().eventId(EVENT_ID).available(100).reserved(0)
                        .pendingConfirmation(0).sold(0).complimentary(0).totalCapacity(100).build());
        when(dynamoDbAsyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));

        StepVerifier.create(adapter.createWithInventory(event, inventory))
                .expectNextMatches(e -> e.getId().equals(EVENT_ID))
                .verifyComplete();
    }

    @Test
    void shouldWrapExceptionOnCreateWithInventory() {
        Event event = Event.builder().id(EVENT_ID).name("Concert").totalCapacity(100).ticketPrice(BigDecimal.TEN).currency("COP").build();
        Inventory inventory = Inventory.builder().eventId(EVENT_ID).available(100).reserved(0)
                .pendingConfirmation(0).sold(0).complimentary(0).totalCapacity(100).build();

        when(eventMapper.toEntity(event)).thenReturn(
                EventEntity.builder().eventId(EVENT_ID).name("Concert").totalCapacity(100)
                        .ticketPrice(BigDecimal.TEN).currency("COP").listingKey("EVENT").build());
        when(inventoryMapper.toEntity(inventory)).thenReturn(
                InventoryEntity.builder().eventId(EVENT_ID).available(100).reserved(0)
                        .pendingConfirmation(0).sold(0).complimentary(0).totalCapacity(100).build());
        when(dynamoDbAsyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("DynamoDB error")));

        StepVerifier.create(adapter.createWithInventory(event, inventory))
                .expectError(TechnicalException.class)
                .verify();
    }

    @Test
    void shouldFindEventById() {
        Event event = Event.builder().id(EVENT_ID).name("Concert").build();

        when(dynamoDbAsyncClient.getItem(any(GetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(GetItemResponse.builder()
                        .item(Map.of("eventId", AttributeValue.builder().s(EVENT_ID).build(),
                                     "name",    AttributeValue.builder().s("Concert").build()))
                        .build()));
        when(eventMapper.toDomain(any(EventEntity.class))).thenReturn(event);

        StepVerifier.create(adapter.findById(EVENT_ID))
                .expectNextMatches(e -> e.getId().equals(EVENT_ID))
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenEventNotFound() {
        when(dynamoDbAsyncClient.getItem(any(GetItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(GetItemResponse.builder().build()));

        StepVerifier.create(adapter.findById(EVENT_ID))
                .verifyComplete();
    }

    @Test
    void shouldWrapExceptionOnFindById() {
        when(dynamoDbAsyncClient.getItem(any(GetItemRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("DynamoDB error")));

        StepVerifier.create(adapter.findById(EVENT_ID))
                .expectError(TechnicalException.class)
                .verify();
    }

    @Test
    void shouldWrapExceptionOnFindAllPaginated() {
        software.amazon.awssdk.services.dynamodb.model.QueryRequest req =
                software.amazon.awssdk.services.dynamodb.model.QueryRequest.builder().tableName(EVENTS_TABLE).build();
        software.amazon.awssdk.services.dynamodb.paginators.QueryPublisher failingPublisher =
                new software.amazon.awssdk.services.dynamodb.paginators.QueryPublisher(dynamoDbAsyncClient, req);

        when(dynamoDbAsyncClient.queryPaginator(any(software.amazon.awssdk.services.dynamodb.model.QueryRequest.class)))
                .thenReturn(failingPublisher);
        when(dynamoDbAsyncClient.query(any(software.amazon.awssdk.services.dynamodb.model.QueryRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("DynamoDB error")));

        StepVerifier.create(adapter.findAllPaginated(10, null))
                .expectError(TechnicalException.class)
                .verify();
    }
}
