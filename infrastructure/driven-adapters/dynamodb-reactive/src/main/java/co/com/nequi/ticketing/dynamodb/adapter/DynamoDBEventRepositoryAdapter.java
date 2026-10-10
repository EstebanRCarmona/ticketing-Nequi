package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.EventEntity;
import co.com.nequi.ticketing.dynamodb.entity.InventoryEntity;
import co.com.nequi.ticketing.dynamodb.mapper.EventEntityMapper;
import co.com.nequi.ticketing.dynamodb.mapper.InventoryEntityMapper;
import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.Inventory;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

@Repository
public class DynamoDBEventRepositoryAdapter implements EventRepository {

    private final DynamoDbEnhancedAsyncClient enhancedAsyncClient;
    private final DynamoDbAsyncTable<EventEntity> eventsTable;
    private final DynamoDbAsyncTable<InventoryEntity> inventoryTable;
    private final EventEntityMapper eventMapper;
    private final InventoryEntityMapper inventoryMapper;

    public DynamoDBEventRepositoryAdapter(DynamoDbEnhancedAsyncClient enhancedAsyncClient, EventEntityMapper eventMapper, InventoryEntityMapper inventoryMapper,
                                           @Value("${aws.dynamodb.table-events:ticketing-events}") String eventsTableName,
                                           @Value("${aws.dynamodb.table-inventory:ticketing-inventory}") String inventoryTableName) {
        this.enhancedAsyncClient = enhancedAsyncClient;
        this.eventsTable = enhancedAsyncClient.table(eventsTableName, TableSchema.fromBean(EventEntity.class));
        this.inventoryTable = enhancedAsyncClient.table(inventoryTableName, TableSchema.fromBean(InventoryEntity.class));
        this.eventMapper = eventMapper;
        this.inventoryMapper = inventoryMapper;
    }

    @Override
    public Mono<Event> createWithInventory(Event event, Inventory inventory) {
        Expression notExists = Expression.builder().expression("attribute_not_exists(eventId)").build();

        TransactWriteItemsEnhancedRequest request = TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(eventsTable, TransactPutItemEnhancedRequest.builder(EventEntity.class)
                        .item(eventMapper.toEntity(event))
                        .conditionExpression(notExists)
                        .build())
                .addPutItem(inventoryTable, TransactPutItemEnhancedRequest.builder(InventoryEntity.class)
                        .item(inventoryMapper.toEntity(inventory))
                        .conditionExpression(notExists)
                        .build())
                .build();

        return Mono.fromFuture(() -> enhancedAsyncClient.transactWriteItems(request))
                .thenReturn(event)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Mono<Event> findById(String id) {
        return Mono.fromFuture(() -> eventsTable.getItem(Key.builder().partitionValue(id).build()))
                .map(eventMapper::toDomain)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }

    @Override
    public Flux<Event> findAllPaginated(int limit, String cursor) {
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue("EVENT").build()))
                .limit(limit)
                .build();

        return Flux.from(eventsTable.index("gsi-events-by-date").query(queryRequest))
                .flatMapIterable(page -> page.items())
                .map(eventMapper::toDomain)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }
}
