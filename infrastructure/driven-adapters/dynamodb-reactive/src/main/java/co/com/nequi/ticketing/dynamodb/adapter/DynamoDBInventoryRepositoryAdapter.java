package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.InventoryEntity;
import co.com.nequi.ticketing.dynamodb.mapper.InventoryEntityMapper;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.Inventory;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.model.gateways.InventoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
public class DynamoDBInventoryRepositoryAdapter implements InventoryRepository {

    private final DynamoDbAsyncTable<InventoryEntity> table;
    private final InventoryEntityMapper mapper;

    public DynamoDBInventoryRepositoryAdapter(DynamoDbEnhancedAsyncClient enhancedAsyncClient, InventoryEntityMapper mapper, @Value("${aws.dynamodb.table-inventory:ticketing-inventory}") String tableName) {
        this.table = enhancedAsyncClient.table(tableName, TableSchema.fromBean(InventoryEntity.class));
        this.mapper = mapper;
    }

    @Override
    public Mono<Inventory> findByEventIdConsistent(String eventId) {
        return Mono.fromFuture(() -> table.getItem(r -> r.key(Key.builder().partitionValue(eventId).build()).consistentRead(true)))
                .map(mapper::toDomain)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }
}
