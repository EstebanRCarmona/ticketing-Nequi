package co.com.nequi.ticketing.usecase;

import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrderUseCaseTest {

    private static final String ORDER_ID = "ord-1";
    private static final String USER_ID = "usr-1";

    @Mock private TicketOrderRepository ticketOrderRepository;

    private GetOrderUseCase getOrderUseCase;

    @BeforeEach
    void setUp() {
        getOrderUseCase = new GetOrderUseCase(ticketOrderRepository);
    }

    @Test
    void shouldReturnOrderForOwner() {
        TicketOrder order = TicketOrder.builder().id(ORDER_ID).userId(USER_ID).status(TicketStatus.RESERVED).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(getOrderUseCase.getById(ORDER_ID, USER_ID, false))
                .expectNextMatches(o -> o.getId().equals(ORDER_ID))
                .verifyComplete();
    }

    @Test
    void adminShouldAccessAnyOrder() {
        TicketOrder order = TicketOrder.builder().id(ORDER_ID).userId("usr-other").status(TicketStatus.SOLD).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(getOrderUseCase.getById(ORDER_ID, "admin-usr", true))
                .expectNextMatches(o -> o.getId().equals(ORDER_ID))
                .verifyComplete();
    }

    @Test
    void shouldFailWhenOrderNotFound() {
        when(ticketOrderRepository.findById("ord-404")).thenReturn(Mono.empty());

        StepVerifier.create(getOrderUseCase.getById("ord-404", USER_ID, false))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_ORDER_NOT_FOUND)
                .verify();
    }

    @Test
    void shouldFailWhenUserDoesNotOwnOrder() {
        TicketOrder order = TicketOrder.builder().id(ORDER_ID).userId("usr-other").status(TicketStatus.RESERVED).build();

        when(ticketOrderRepository.findById(ORDER_ID)).thenReturn(Mono.just(order));

        StepVerifier.create(getOrderUseCase.getById(ORDER_ID, USER_ID, false))
                .expectErrorMatches(e -> e instanceof BusinessException be
                        && be.getGeneralMessage() == GeneralMessage.BUS_ORDER_NOT_FOUND)
                .verify();
    }
}
