package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.InventoryEntity;
import co.com.nequi.ticketing.dynamodb.entity.SeatEntity;
import co.com.nequi.ticketing.dynamodb.mapper.IdempotencyEntityMapperImpl;
import co.com.nequi.ticketing.dynamodb.mapper.OrderHistoryEntityMapperImpl;
import co.com.nequi.ticketing.dynamodb.mapper.TicketOrderEntityMapperImpl;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.ActorType;
import co.com.nequi.ticketing.model.enums.OrderType;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;

@Tag("integration")
@Testcontainers
@DisplayName("DynamoDB Concurrency & Transactional Integrity Integration Test")
class DynamoDBConcurrencyLocalStackTest {

    private static final String TABLE_ORDERS = "ticketing-orders";
    private static final String TABLE_INVENTORY = "ticketing-inventory";
    private static final String TABLE_HISTORY = "ticketing-order-history";
    private static final String TABLE_IDEMPOTENCY = "ticketing-idempotency";
    private static final String TABLE_SEATS = "ticketing-seats";

        @SuppressWarnings("resource")
        @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4"))
            .withServices(DYNAMODB);

    private static DynamoDbAsyncClient asyncClient;
    private static DynamoDbEnhancedAsyncClient enhancedClient;
    private static DynamoDBTicketOrderRepositoryAdapter adapter;

    @BeforeAll
    static void setUp() {
        asyncClient = DynamoDbAsyncClient.builder()
                .endpointOverride(localStack.getEndpointOverride(DYNAMODB))
                .region(Region.of(localStack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
                .build();

        enhancedClient = DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(asyncClient)
                .build();

        adapter = new DynamoDBTicketOrderRepositoryAdapter(
                asyncClient,
                enhancedClient,
                new TicketOrderEntityMapperImpl(),
                new OrderHistoryEntityMapperImpl(),
                new IdempotencyEntityMapperImpl(),
                TABLE_ORDERS,
                TABLE_INVENTORY,
                TABLE_HISTORY,
                TABLE_IDEMPOTENCY,
                TABLE_SEATS
        );

        createTables();
    }

    @AfterAll
    static void tearDown() {
        if (asyncClient != null) {
            asyncClient.close();
        }
    }

    private static void createTables() {
        asyncClient.createTable(CreateTableRequest.builder()
                .tableName(TABLE_INVENTORY)
                .keySchema(KeySchemaElement.builder().attributeName("eventId").keyType(KeyType.HASH).build())
                .attributeDefinitions(AttributeDefinition.builder().attributeName("eventId").attributeType(ScalarAttributeType.S).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build()).join();

        asyncClient.createTable(CreateTableRequest.builder()
                .tableName(TABLE_ORDERS)
                .keySchema(KeySchemaElement.builder().attributeName("orderId").keyType(KeyType.HASH).build())
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("orderId").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("expiryShard").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("deadlineAt").attributeType(ScalarAttributeType.N).build()
                )
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName("gsi-orders-deadline")
                        .keySchema(
                                KeySchemaElement.builder().attributeName("expiryShard").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("deadlineAt").keyType(KeyType.RANGE).build()
                        )
                        .projection(Projection.builder().projectionType(ProjectionType.KEYS_ONLY).build())
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build()).join();

        asyncClient.createTable(CreateTableRequest.builder()
                .tableName(TABLE_HISTORY)
                .keySchema(
                        KeySchemaElement.builder().attributeName("orderId").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sequence").keyType(KeyType.RANGE).build()
                )
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("orderId").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sequence").attributeType(ScalarAttributeType.N).build()
                )
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build()).join();

        asyncClient.createTable(CreateTableRequest.builder()
                .tableName(TABLE_IDEMPOTENCY)
                .keySchema(KeySchemaElement.builder().attributeName("idempotencyKey").keyType(KeyType.HASH).build())
                .attributeDefinitions(AttributeDefinition.builder().attributeName("idempotencyKey").attributeType(ScalarAttributeType.S).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build()).join();

        asyncClient.createTable(CreateTableRequest.builder()
                .tableName(TABLE_SEATS)
                .keySchema(
                        KeySchemaElement.builder().attributeName("seatKey").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("seatId").keyType(KeyType.RANGE).build())
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("seatKey").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("seatId").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("statusKey").attributeType(ScalarAttributeType.S).build())
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName("gsi-seats-by-status")
                        .keySchema(
                                KeySchemaElement.builder().attributeName("statusKey").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("seatId").keyType(KeyType.RANGE).build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build()).join();
    }

    @Test
    @DisplayName("Should ensure exactly M reservations succeed when N concurrent requests compete for limited inventory")
    void shouldEnsureExactlyMReservationsSucceedUnderHighConcurrency() {
        String eventId = "evt-concurrent-test";
        int totalTicketsM = 10;
        int concurrentAttemptsN = 30;

        DynamoDbAsyncTable<InventoryEntity> inventoryTable = enhancedClient.table(TABLE_INVENTORY, TableSchema.fromBean(InventoryEntity.class));
        InventoryEntity initialInventory = InventoryEntity.builder()
                .eventId(eventId)
                .totalCapacity(totalTicketsM)
                .available(totalTicketsM)
                .reserved(0)
                .sold(0)
                .complimentary(0)
                .pendingConfirmation(0)
                .updatedAt(Instant.now().toString())
                .build();
        inventoryTable.putItem(initialInventory).join();
        DynamoDbAsyncTable<SeatEntity> seatsTable = enhancedClient.table(TABLE_SEATS, TableSchema.fromBean(SeatEntity.class));
        for (int seatNumber = 1; seatNumber <= totalTicketsM; seatNumber++) {
            String seatId = "S-%05d".formatted(seatNumber);
            int shard = Math.floorMod(seatId.hashCode(), 16);
            seatsTable.putItem(SeatEntity.builder().seatKey(eventId + "#S#" + shard).eventId(eventId).seatId(seatId)
                    .status("AVAILABLE").statusKey(eventId + "#AVAILABLE#S#" + shard)
                    .updatedAt(Instant.now().toString()).build()).join();
        }

        AtomicInteger successfulReservations = new AtomicInteger();
        AtomicInteger rejectedReservations = new AtomicInteger();

        Flux.range(1, concurrentAttemptsN)
                .flatMap(i -> {
                    String orderId = UUID.randomUUID().toString();
                    Instant now = Instant.now();
                    String seatId = "S-%05d".formatted((i - 1) % totalTicketsM + 1);

                    TicketOrder order = TicketOrder.builder()
                            .id(orderId)
                            .eventId(eventId)
                            .userId("user-" + i)
                            .orderType(OrderType.PURCHASE)
                            .quantity(1)
                            .seatIds(java.util.List.of(seatId))
                            .unitPrice(BigDecimal.valueOf(50))
                            .totalAmount(BigDecimal.valueOf(50))
                            .currency("COP")
                            .status(TicketStatus.RESERVED)
                            .version(1L)
                            .createdAt(now)
                            .updatedAt(now)
                            .expiresAt(now.plusSeconds(600))
                            .expiryShard("S#0")
                            .build();

                    OrderHistory history = OrderHistory.builder()
                            .orderId(orderId)
                            .sequence(1L)
                            .toStatus(TicketStatus.RESERVED)
                            .reasonCode("ORDER_CREATED")
                            .actor(ActorType.API)
                            .quantity(1)
                            .seatIds(java.util.List.of(seatId))
                            .traceId("trace-" + i)
                            .occurredAt(now)
                            .build();

                    IdempotencyRecord idempotency = IdempotencyRecord.builder()
                            .idempotencyKey("idemp-" + i)
                            .requestHash("hash-" + i)
                            .orderId(orderId)
                            .ttl(now.getEpochSecond() + 3600)
                            .build();

                    return adapter.createOrderTransaction(order, history, idempotency)
                            .doOnSuccess(saved -> successfulReservations.incrementAndGet())
                            .onErrorResume(BusinessException.class, e -> {
                                rejectedReservations.incrementAndGet();
                                return Mono.empty();
                            });
                }, concurrentAttemptsN)
                .blockLast();

        assertEquals(totalTicketsM, successfulReservations.get(),
                "Successful reservations count must match initial capacity");
        assertEquals(concurrentAttemptsN - totalTicketsM, rejectedReservations.get(),
                "Excess reservation attempts must be rejected due to insufficient inventory");

        InventoryEntity finalInventory = inventoryTable.getItem(Key.builder().partitionValue(eventId).build()).join();
        assertEquals(0, finalInventory.getAvailable(), "Available tickets count must be zero");
        assertEquals(totalTicketsM, finalInventory.getReserved(), "Reserved tickets count must match initial capacity");
    }
}
