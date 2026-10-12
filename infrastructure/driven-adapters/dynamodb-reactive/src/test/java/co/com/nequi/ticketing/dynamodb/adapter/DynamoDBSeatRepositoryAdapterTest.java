package co.com.nequi.ticketing.dynamodb.adapter;

import co.com.nequi.ticketing.dynamodb.entity.SeatEntity;
import co.com.nequi.ticketing.dynamodb.mapper.SeatEntityMapper;
import co.com.nequi.ticketing.model.Seat;
import co.com.nequi.ticketing.model.enums.SeatStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoDBSeatRepositoryAdapterTest {

    private static final String TABLE = "ticketing-seats";
    private static final String EVENT_ID = "evt-1";
        private static final int TEST_SEAT_SHARD = Math.floorMod("S-00001".hashCode(), SeatEntityMapper.SEAT_SHARD_COUNT);
        private static final String TEST_SEAT_KEY = EVENT_ID + "#S#" + TEST_SEAT_SHARD;
        private static final String TEST_AVAILABLE_KEY = EVENT_ID + "#AVAILABLE#S#" + TEST_SEAT_SHARD;

    @Mock private DynamoDbAsyncClient asyncClient;
    @Mock private DynamoDbEnhancedAsyncClient enhancedClient;
    @Mock private DynamoDbAsyncTable<SeatEntity> seatsTable;
    @Mock private DynamoDbAsyncIndex<SeatEntity> seatsIndex;
    @Mock private SeatEntityMapper seatMapper;

    private DynamoDBSeatRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(TABLE, TableSchema.fromBean(SeatEntity.class))).thenReturn(seatsTable);
        adapter = new DynamoDBSeatRepositoryAdapter(asyncClient, enhancedClient, seatMapper, TABLE);
    }

    @Test
    void createsAvailableSeatRecordsInBatches() {
        SeatEntity seat = SeatEntity.builder().seatKey(TEST_SEAT_KEY).eventId(EVENT_ID).seatId("S-00001")
                .status("AVAILABLE").statusKey(TEST_AVAILABLE_KEY).updatedAt(Instant.now().toString()).build();
        when(seatMapper.toEntity(any(Seat.class))).thenReturn(seat);
        when(asyncClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(BatchWriteItemResponse.builder().build()));

        StepVerifier.create(adapter.createSeats(EVENT_ID, 1)).verifyComplete();

        ArgumentCaptor<BatchWriteItemRequest> captor = ArgumentCaptor.forClass(BatchWriteItemRequest.class);
        verify(asyncClient).batchWriteItem(captor.capture());
        Map<String, AttributeValue> writtenSeat = captor.getValue().requestItems().get(TABLE).getFirst().putRequest().item();
        assertEquals(TEST_SEAT_KEY, writtenSeat.get("seatKey").s());
        assertEquals(TEST_AVAILABLE_KEY, writtenSeat.get("statusKey").s());
        assertEquals("AVAILABLE", writtenSeat.get("status").s());
    }

    @Test
    void retriesUnprocessedSeatWrites() {
        SeatEntity seat = SeatEntity.builder().seatKey(TEST_SEAT_KEY).eventId(EVENT_ID).seatId("S-00001")
                .status("AVAILABLE").statusKey(TEST_AVAILABLE_KEY).updatedAt(Instant.now().toString()).build();
        when(seatMapper.toEntity(any(Seat.class))).thenReturn(seat);
        WriteRequest unprocessed = WriteRequest.builder().putRequest(p -> p.item(Map.of(
                "seatKey", AttributeValue.builder().s(seat.getSeatKey()).build(),
                "seatId", AttributeValue.builder().s(seat.getSeatId()).build()))).build();
        when(asyncClient.batchWriteItem(any(BatchWriteItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(BatchWriteItemResponse.builder()
                                .unprocessedItems(Map.of(TABLE, List.of(unprocessed))).build()))
                .thenReturn(CompletableFuture.completedFuture(BatchWriteItemResponse.builder().build()));

        StepVerifier.create(adapter.createSeats(EVENT_ID, 1)).verifyComplete();

        verify(asyncClient, times(2)).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    @Test
    void findsSelectedSeatsUsingTheirDeterministicShardKey() {
        SeatEntity entity = SeatEntity.builder().seatKey(TEST_SEAT_KEY).eventId(EVENT_ID)
                .seatId("S-00001").status("AVAILABLE").build();
        Seat seat = Seat.builder().eventId(EVENT_ID).seatId("S-00001").status(SeatStatus.AVAILABLE).build();
        when(seatsTable.getItem(any(GetItemEnhancedRequest.class))).thenReturn(CompletableFuture.completedFuture(entity));
        when(seatMapper.toDomain(entity)).thenReturn(seat);

        StepVerifier.create(adapter.findSeats(EVENT_ID, List.of("S-00001")))
                .expectNext(seat)
                .verifyComplete();
    }

    @Test
    void queriesAllStatusShardsForAvailableSeats() {
        SeatEntity entity = SeatEntity.builder().seatKey(TEST_SEAT_KEY).eventId(EVENT_ID)
                .seatId("S-00001").status("AVAILABLE").build();
        Seat seat = Seat.builder().eventId(EVENT_ID).seatId("S-00001").status(SeatStatus.AVAILABLE).build();
        Page<SeatEntity> page = Page.<SeatEntity>builder(SeatEntity.class).items(List.of(entity)).build();
        when(seatsTable.index("gsi-seats-by-status")).thenReturn(seatsIndex);
        doReturn(PagePublisher.create(SdkPublisher.adapt(Flux.just(page))))
                .when(seatsIndex).query(any(QueryEnhancedRequest.class));
        when(seatMapper.toDomain(entity)).thenReturn(seat);

        StepVerifier.create(adapter.findAvailableSeats(EVENT_ID))
                .expectNextCount(SeatEntityMapper.SEAT_SHARD_COUNT)
                .verifyComplete();

        verify(seatsIndex, times(SeatEntityMapper.SEAT_SHARD_COUNT)).query(any(QueryEnhancedRequest.class));
    }
}
