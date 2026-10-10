package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.IdempotencyEntity;
import co.com.nequi.ticketing.dynamodb.mapper.IdempotencyEntityMapper;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.model.gateways.IdempotencyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
public class DynamoDBIdempotencyRepositoryAdapter implements IdempotencyRepository {

    private final DynamoDbAsyncTable<IdempotencyEntity> table;
    private final IdempotencyEntityMapper mapper;

    public DynamoDBIdempotencyRepositoryAdapter(DynamoDbEnhancedAsyncClient enhancedAsyncClient, IdempotencyEntityMapper mapper, @Value("${aws.dynamodb.table-idempotency:ticketing-idempotency}") String tableName) {
        this.table = enhancedAsyncClient.table(tableName, TableSchema.fromBean(IdempotencyEntity.class));
        this.mapper = mapper;
    }

    @Override
    public Mono<IdempotencyRecord> findByKey(String key) {
        return Mono.fromFuture(() -> table.getItem(Key.builder().partitionValue(key).build()))
                .map(mapper::toDomain)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }
}
