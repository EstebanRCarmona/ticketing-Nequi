package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseExpiredReservationsUseCaseTest {

    private static final String ORDER_ID = "ord-1";
    private static final String TRACE_ID = "trace-1";
    private static final long PAST_DEADLINE = Instant.now().minusSeconds(60).getEpochSecond();

    @Mock private TicketOrderRepository ticketOrderRepository;

    private ReleaseExpiredReservationsUseCase releaseExpiredReservationsUseCase;

    @BeforeEach
    void setUp() {
        releaseExpiredReservationsUseCase = new ReleaseExpiredReservationsUseCase(ticketOrderRepository);
    }

    @Test
    void shouldExpireReservedOrderById() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).status(TicketStatus.RESERVED)
                .expiresAt(Instant.ofEpochSecond(PAST_DEADLINE)).build();
        TicketOrder expired = order.toBuilder().status(TicketStatus.EXPIRED).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));
        when(ticketOrderRepository.expireOrderTransaction(any(), anyString())).thenReturn(Mono.just(expired));

        StepVerifier.create(releaseExpiredReservationsUseCase.expireOrderById(ORDER_ID, PAST_DEADLINE, TRACE_ID))
                .verifyComplete();
    }

    @Test
    void shouldExpirePendingConfirmationOrderById() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).status(TicketStatus.PENDING_CONFIRMATION)
                .expiresAt(Instant.ofEpochSecond(PAST_DEADLINE)).build();
        TicketOrder expired = order.toBuilder().status(TicketStatus.EXPIRED).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));
        when(ticketOrderRepository.expireOrderTransaction(any(), anyString())).thenReturn(Mono.just(expired));

        StepVerifier.create(releaseExpiredReservationsUseCase.expireOrderById(ORDER_ID, PAST_DEADLINE, TRACE_ID))
                .verifyComplete();
    }

    @Test
    void shouldSkipWhenOrderNotFound() {
        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(releaseExpiredReservationsUseCase.expireOrderById(ORDER_ID, PAST_DEADLINE, TRACE_ID))
                .verifyComplete();
    }

    @Test
    void shouldSkipWhenOrderIsAlreadyTerminal() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).status(TicketStatus.SOLD)
                .expiresAt(Instant.ofEpochSecond(PAST_DEADLINE)).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(releaseExpiredReservationsUseCase.expireOrderById(ORDER_ID, PAST_DEADLINE, TRACE_ID))
                .verifyComplete();
    }

    @Test
    void shouldSkipWhenDeadlineDoesNotMatch() {
        long differentDeadline = PAST_DEADLINE - 100;
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).status(TicketStatus.RESERVED)
                .expiresAt(Instant.ofEpochSecond(PAST_DEADLINE)).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(releaseExpiredReservationsUseCase.expireOrderById(ORDER_ID, differentDeadline, TRACE_ID))
                .verifyComplete();
    }

    @Test
    void shouldRunSweeperAcrossAllShards() {
        TicketOrder order = TicketOrder.builder()
                .id(ORDER_ID).status(TicketStatus.RESERVED)
                .expiresAt(Instant.now().minusSeconds(10)).build();
        TicketOrder expired = order.toBuilder().status(TicketStatus.EXPIRED).build();

        when(ticketOrderRepository.findExpiredOrdersByShard(anyString(), anyLong())).thenReturn(Flux.just(order));
        when(ticketOrderRepository.expireOrderTransaction(any(), anyString())).thenReturn(Mono.just(expired));

        StepVerifier.create(releaseExpiredReservationsUseCase.executeSweeper())
                .expectNextCount(4)
                .verifyComplete();
    }

    @Test
    void shouldRunSweeperWithNoExpiredOrders() {
        when(ticketOrderRepository.findExpiredOrdersByShard(anyString(), anyLong())).thenReturn(Flux.empty());

        StepVerifier.create(releaseExpiredReservationsUseCase.executeSweeper())
                .verifyComplete();
    }
}
