package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.OrderHistoryEntity;
import co.com.nequi.ticketing.dynamodb.mapper.OrderHistoryEntityMapper;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.model.gateways.OrderHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
public class DynamoDBOrderHistoryRepositoryAdapter implements OrderHistoryRepository {

    private final DynamoDbAsyncTable<OrderHistoryEntity> table;
    private final OrderHistoryEntityMapper mapper;

    public DynamoDBOrderHistoryRepositoryAdapter(DynamoDbEnhancedAsyncClient enhancedAsyncClient, OrderHistoryEntityMapper mapper, @Value("${aws.dynamodb.table-order-history:ticketing-order-history}") String tableName) {
        this.table = enhancedAsyncClient.table(tableName, TableSchema.fromBean(OrderHistoryEntity.class));
        this.mapper = mapper;
    }

    @Override
    public Flux<OrderHistory> findByOrderId(String orderId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(orderId).build());
        return Flux.from(table.query(queryConditional))
                .flatMapIterable(page -> page.items())
                .map(mapper::toDomain)
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, e));
    }
}
