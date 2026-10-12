package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.SeatEntity;
import co.com.nequi.ticketing.dynamodb.mapper.SeatEntityMapper;
import co.com.nequi.ticketing.model.Seat;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.enums.SeatStatus;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.model.gateways.SeatRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class DynamoDBSeatRepositoryAdapter implements SeatRepository {

    private static final int BATCH_SIZE = 25;
    private static final int MAX_BATCH_RETRIES = 5;

    private final DynamoDbAsyncClient asyncClient;
    private final DynamoDbAsyncTable<SeatEntity> seatsTable;
    private final SeatEntityMapper seatMapper;
    private final String tableName;

    public DynamoDBSeatRepositoryAdapter(DynamoDbAsyncClient asyncClient,
                                         DynamoDbEnhancedAsyncClient enhancedAsyncClient,
                                         SeatEntityMapper seatMapper,
                                         @Value("${aws.dynamodb.table-seats:ticketing-seats}") String tableName) {
        this.asyncClient = asyncClient;
        this.seatsTable = enhancedAsyncClient.table(tableName, TableSchema.fromBean(SeatEntity.class));
        this.seatMapper = seatMapper;
        this.tableName = tableName;
    }

    @Override
    public Mono<Void> createSeats(String eventId, int capacity) {
        Instant now = Instant.now();
        List<SeatEntity> seats = new ArrayList<>(capacity);
        for (int seatNumber = 1; seatNumber <= capacity; seatNumber++) {
            seats.add(seatMapper.toEntity(Seat.builder()
                    .eventId(eventId)
                    .seatId("S-%05d".formatted(seatNumber))
                    .status(SeatStatus.AVAILABLE)
                    .updatedAt(now)
                    .build()));
        }

        return Flux.fromIterable(seats)
                .buffer(BATCH_SIZE)
                .flatMap(batch -> writeBatch(toWriteRequests(batch), 0), 8)
                .then();
    }

    @Override
    public Flux<Seat> findAvailableSeats(String eventId) {
        return Flux.range(0, SeatEntityMapper.SEAT_SHARD_COUNT)
                .flatMap(shard -> Flux.from(seatsTable.index("gsi-seats-by-status").query(
                        QueryEnhancedRequest.builder()
                                .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                                        .partitionValue(eventId + "#" + SeatStatus.AVAILABLE.name() + "#S#" + shard)
                                        .build()))
                                .build())))
                .flatMapIterable(page -> page.items())
                .map(seatMapper::toDomain)
                .sort((left, right) -> left.getSeatId().compareTo(right.getSeatId()))
                .onErrorMap(error -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, error));
    }

    @Override
    public Flux<Seat> findSeats(String eventId, List<String> seatIds) {
        return Flux.fromIterable(seatIds)
            .flatMap(seatId -> Mono.fromFuture(() -> seatsTable.getItem(GetItemEnhancedRequest.builder()
                    .key(Key.builder().partitionValue(seatKey(eventId, seatId)).sortValue(seatId).build())
                    .consistentRead(true)
                    .build()))
                        .map(seatMapper::toDomain)
                        .onErrorMap(error -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, error)));
    }

    private List<WriteRequest> toWriteRequests(List<SeatEntity> entities) {
        return entities.stream().map(entity -> WriteRequest.builder()
                .putRequest(PutRequest.builder().item(Map.of(
                        "seatKey", AttributeValue.builder().s(entity.getSeatKey()).build(),
                        "eventId", AttributeValue.builder().s(entity.getEventId()).build(),
                        "seatId", AttributeValue.builder().s(entity.getSeatId()).build(),
                        "status", AttributeValue.builder().s(entity.getStatus()).build(),
                        "statusKey", AttributeValue.builder().s(entity.getStatusKey()).build(),
                        "updatedAt", AttributeValue.builder().s(entity.getUpdatedAt()).build()
                )).build())
                .build()).toList();
    }

        private String seatKey(String eventId, String seatId) {
            return eventId + "#S#" + Math.floorMod(seatId.hashCode(), SeatEntityMapper.SEAT_SHARD_COUNT);
        }

    private Mono<Void> writeBatch(List<WriteRequest> requests, int attempt) {
        BatchWriteItemRequest request = BatchWriteItemRequest.builder()
                .requestItems(Map.of(tableName, requests))
                .build();
        return Mono.fromFuture(() -> asyncClient.batchWriteItem(request))
                .flatMap(response -> {
                    List<WriteRequest> unprocessed = response.unprocessedItems().getOrDefault(tableName, List.of());
                    if (unprocessed.isEmpty()) {
                        return Mono.empty();
                    }
                    if (attempt >= MAX_BATCH_RETRIES) {
                        return Mono.error(new IllegalStateException("DynamoDB did not process all seat writes"));
                    }
                    return Mono.delay(Duration.ofMillis(25L << attempt))
                            .then(writeBatch(unprocessed, attempt + 1));
                })
                .onErrorMap(error -> new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, error));
    }
}