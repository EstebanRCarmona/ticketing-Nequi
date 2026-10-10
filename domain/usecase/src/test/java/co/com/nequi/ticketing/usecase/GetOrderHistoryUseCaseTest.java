package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.OrderHistoryRepository;
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

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrderHistoryUseCaseTest {

    @Mock private TicketOrderRepository ticketOrderRepository;
    @Mock private OrderHistoryRepository orderHistoryRepository;

    private GetOrderHistoryUseCase getOrderHistoryUseCase;

    @BeforeEach
    void setUp() {
        getOrderHistoryUseCase = new GetOrderHistoryUseCase(ticketOrderRepository, orderHistoryRepository);
    }

    @Test
    void shouldReturnHistoryForOrderOwner() {
        TicketOrder order = TicketOrder.builder().id("ord-1").userId("usr-1").status(TicketStatus.SOLD).build();
        OrderHistory history = OrderHistory.builder().orderId("ord-1").sequence(1L).occurredAt(Instant.now()).build();

        when(ticketOrderRepository.findById("ord-1")).thenReturn(Mono.just(order));
        when(orderHistoryRepository.findByOrderId("ord-1")).thenReturn(Flux.just(history));

        StepVerifier.create(getOrderHistoryUseCase.execute("ord-1", "usr-1", false))
                .expectNextMatches(h -> h.getOrderId().equals("ord-1"))
                .verifyComplete();
    }

    @Test
    void adminShouldAccessAnyOrderHistory() {
        TicketOrder order = TicketOrder.builder().id("ord-1").userId("usr-other").status(TicketStatus.SOLD).build();
        OrderHistory history = OrderHistory.builder().orderId("ord-1").sequence(1L).occurredAt(Instant.now()).build();

        when(ticketOrderRepository.findById("ord-1")).thenReturn(Mono.just(order));
        when(orderHistoryRepository.findByOrderId("ord-1")).thenReturn(Flux.just(history));

        StepVerifier.create(getOrderHistoryUseCase.execute("ord-1", "admin-usr", true))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldFailWhenUserDoesNotOwnOrder() {
        TicketOrder order = TicketOrder.builder().id("ord-1").userId("usr-other").status(TicketStatus.SOLD).build();

        when(ticketOrderRepository.findById("ord-1")).thenReturn(Mono.just(order));

        StepVerifier.create(getOrderHistoryUseCase.execute("ord-1", "usr-1", false))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void shouldFailWhenOrderNotFound() {
        when(ticketOrderRepository.findById("ord-404")).thenReturn(Mono.empty());

        StepVerifier.create(getOrderHistoryUseCase.execute("ord-404", "usr-1", false))
                .expectError(BusinessException.class)
                .verify();
    }
}
