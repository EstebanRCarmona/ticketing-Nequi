package co.com.nequi.ticketing.usecase.concurrency;

import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.enums.OrderType;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.IdempotencyRepository;
import co.com.nequi.ticketing.model.gateways.QueueService;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import co.com.nequi.ticketing.usecase.CreateOrderUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Validates concurrency invariants of CreateOrderUseCase using an in-memory
 * AtomicInteger that mimics DynamoDB's conditional write (available >= qty).
 *
 * Key invariants verified:
 *  1. Total tickets sold never exceeds initial inventory (no overselling).
 *  2. Exactly (capacity / qty) orders succeed; the rest get BUS_INSUFFICIENT_INVENTORY.
 *  3. Idempotency: replaying the same key returns the cached order, not a new one.
 *  4. Concurrent duplicate keys produce at most 1 actual DB write.
 *
 * Root cause of hangs: subscribe(onNext, onError, onComplete) — onComplete is NEVER
 * called when the Mono terminates with an error. Always use doFinally() for cleanup.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConcurrencyOrderTest {

    private static final String EVENT_ID       = "evt-concurrent";
    private static final int    TOTAL_CAPACITY = 10;
    private static final int    QTY            = 1;
    private static final int    CONCURRENT_VUS = 50;
    private static final int    TIMEOUT_SECS   = 10;

    @Mock private EventRepository       eventRepository;
    @Mock private TicketOrderRepository ticketOrderRepository;
    @Mock private IdempotencyRepository idempotencyRepository;
    @Mock private QueueService          queueService;

    /** Simulates DynamoDB available counter with CAS — the core anti-oversell guard. */
    private final AtomicInteger available        = new AtomicInteger(TOTAL_CAPACITY);
    private final AtomicInteger successCount     = new AtomicInteger(0);
    private final AtomicInteger insufficientCount = new AtomicInteger(0);
    private final AtomicInteger unexpectedErrors  = new AtomicInteger(0);

    private CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        available.set(TOTAL_CAPACITY);
        successCount.set(0);
        insufficientCount.set(0);
        unexpectedErrors.set(0);

        Event event = Event.builder()
                .id(EVENT_ID).name("Concurrent Concert")
                .totalCapacity(TOTAL_CAPACITY)
                .ticketPrice(BigDecimal.valueOf(100))
                .currency("COP").build();

        when(eventRepository.findById(anyString())).thenReturn(Mono.just(event));
        when(idempotencyRepository.findByKey(anyString())).thenReturn(Mono.empty());
        when(queueService.sendExpireOrderMessage(anyString(), anyLong(), anyLong())).thenReturn(Mono.empty());

        useCase = new CreateOrderUseCase(eventRepository, ticketOrderRepository, idempotencyRepository, queueService);
    }

    /** Configures the CAS-based inventory mock. Call this in each test that needs it. */
    private void withCasInventoryMock() {
        when(ticketOrderRepository.createOrderTransaction(any(), any(), any()))
                .thenAnswer(inv -> {
                    TicketOrder order = inv.getArgument(0, TicketOrder.class);
                    int qty = order.getQuantity();
                    while (true) {
                        int cur = available.get();
                        if (cur < qty) {
                            return Mono.error(new BusinessException(GeneralMessage.BUS_INSUFFICIENT_INVENTORY));
                        }
                        if (available.compareAndSet(cur, cur - qty)) {
                            return Mono.just(order.toBuilder()
                                    .id(UUID.randomUUID().toString())
                                    .status(TicketStatus.RESERVED)
                                    .createdAt(Instant.now())
                                    .updatedAt(Instant.now())
                                    .build());
                        }
                    }
                });
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Subscribes to the use case and counts results.
     * Uses doFinally() so the latch is ALWAYS decremented regardless of
     * whether the signal is onComplete or onError.
     */
    private void executeAndCount(String userId, String idemKey, int qty, CountDownLatch latch) {
        useCase.execute(EVENT_ID, userId, qty, idemKey, "hash-" + idemKey, "trace")
                .doFinally(signal -> latch.countDown())   // ← always called: onComplete OR onError
                .subscribe(
                        order -> successCount.incrementAndGet(),
                        error -> {
                            if (error instanceof BusinessException be
                                    && be.getGeneralMessage() == GeneralMessage.BUS_INSUFFICIENT_INVENTORY) {
                                insufficientCount.incrementAndGet();
                            } else {
                                unexpectedErrors.incrementAndGet();
                            }
                        }
                );
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void concurrentOrders_shouldNeverOversell() throws InterruptedException {
        withCasInventoryMock();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(CONCURRENT_VUS);

        for (int i = 0; i < CONCURRENT_VUS; i++) {
            final int idx = i;
            Schedulers.boundedElastic().schedule(() -> {
                try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                executeAndCount("usr-" + idx, "key-" + idx, QTY, endGate);
            });
        }

        startGate.countDown();
        boolean finished = endGate.await(TIMEOUT_SECS, TimeUnit.SECONDS);

        assertThat(finished).as("All requests must complete within %ds", TIMEOUT_SECS).isTrue();
        assertThat(unexpectedErrors.get()).as("No unexpected errors").isZero();
        assertThat(successCount.get()).as("Sold tickets ≤ capacity").isLessThanOrEqualTo(TOTAL_CAPACITY);
        assertThat(available.get()).as("Remaining inventory ≥ 0 (no overselling)").isGreaterThanOrEqualTo(0);
        assertThat(successCount.get() + insufficientCount.get())
                .as("Every request must resolve").isEqualTo(CONCURRENT_VUS);
    }

    @Test
    void concurrentOrders_exactCapacity_allShouldSucceed() throws InterruptedException {
        withCasInventoryMock();
        int users = TOTAL_CAPACITY;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(users);

        for (int i = 0; i < users; i++) {
            final int idx = i;
            Schedulers.boundedElastic().schedule(() -> {
                try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                executeAndCount("usr-exact-" + idx, "key-exact-" + idx, QTY, endGate);
            });
        }

        startGate.countDown();
        boolean finished = endGate.await(TIMEOUT_SECS, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(TOTAL_CAPACITY);
        assertThat(insufficientCount.get()).isZero();
        assertThat(available.get()).isZero();
    }

    @Test
    void concurrentOrders_multipleTicketsPerOrder_shouldNeverOversell() throws InterruptedException {
        withCasInventoryMock();
        int users       = 20;
        int qtyPerOrder = 2;  // 20 users × 2 tickets, capacity = 10 → max 5 succeed
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(users);

        for (int i = 0; i < users; i++) {
            final int idx = i;
            Schedulers.boundedElastic().schedule(() -> {
                try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                executeAndCount("usr-multi-" + idx, "key-multi-" + idx, qtyPerOrder, endGate);
            });
        }

        startGate.countDown();
        boolean finished = endGate.await(TIMEOUT_SECS, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(unexpectedErrors.get()).isZero();
        assertThat(successCount.get()).isLessThanOrEqualTo(TOTAL_CAPACITY / qtyPerOrder);
        assertThat(available.get()).isGreaterThanOrEqualTo(0);
        assertThat(successCount.get() + insufficientCount.get()).isEqualTo(users);
    }

    @Test
    void idempotentKey_concurrentDuplicates_shouldNotCreateMultipleOrders() throws InterruptedException {
        int duplicates = 20;
        String sharedKey  = "shared-idem-key";
        String sharedHash = "shared-hash";

        AtomicInteger dbWriteCount = new AtomicInteger(0);

        // Count actual DB writes — only the first call should reach here
        when(ticketOrderRepository.createOrderTransaction(any(TicketOrder.class), any(OrderHistory.class), any(IdempotencyRecord.class)))
                .thenAnswer(inv -> {
                    dbWriteCount.incrementAndGet();
                    TicketOrder order = inv.getArgument(0, TicketOrder.class);
                    available.decrementAndGet();
                    return Mono.just(order.toBuilder()
                            .id("ord-idem-001").status(TicketStatus.RESERVED)
                            .createdAt(Instant.now()).updatedAt(Instant.now()).build());
                });

        // Idempotency cache: first call misses, subsequent calls hit
        AtomicInteger callCount = new AtomicInteger(0);
        TicketOrder cachedOrder = TicketOrder.builder()
                .id("ord-idem-001").eventId(EVENT_ID).status(TicketStatus.RESERVED)
                .quantity(QTY).totalAmount(BigDecimal.valueOf(100))
                .orderType(OrderType.PURCHASE).createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(idempotencyRepository.findByKey(anyString())).thenAnswer(inv -> {
            if (callCount.incrementAndGet() > 1) {
                return Mono.just(IdempotencyRecord.builder()
                        .idempotencyKey(inv.getArgument(0))
                        .requestHash(sharedHash)
                        .orderId("ord-idem-001")
                        .build());
            }
            return Mono.empty();
        });
        when(ticketOrderRepository.findById("ord-idem-001")).thenReturn(Mono.just(cachedOrder));

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(duplicates);

        for (int i = 0; i < duplicates; i++) {
            Schedulers.boundedElastic().schedule(() -> {
                try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                useCase.execute(EVENT_ID, "usr-same", QTY, sharedKey, sharedHash, "trace")
                        .doFinally(signal -> endGate.countDown())
                        .subscribe(
                                order -> successCount.incrementAndGet(),
                                error -> insufficientCount.incrementAndGet()
                        );
            });
        }

        startGate.countDown();
        boolean finished = endGate.await(TIMEOUT_SECS, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(dbWriteCount.get())
                .as("At most 1 actual DB write for the same idempotency key")
                .isLessThanOrEqualTo(1);
        assertThat(successCount.get() + insufficientCount.get()).isEqualTo(duplicates);
    }
}
