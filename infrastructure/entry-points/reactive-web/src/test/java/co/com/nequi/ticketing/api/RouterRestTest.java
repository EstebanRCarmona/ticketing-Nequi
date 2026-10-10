package co.com.nequi.ticketing.api;

import co.com.nequi.ticketing.api.handlers.EventHandler;
import co.com.nequi.ticketing.api.handlers.OrderHandler;
import co.com.nequi.ticketing.api.helper.LogAppenderHelper;
import co.com.nequi.ticketing.api.helper.ValidationHelper;
import co.com.nequi.ticketing.api.mapper.EventDTOMapper;
import co.com.nequi.ticketing.api.mapper.OrderHistoryDTOMapper;
import co.com.nequi.ticketing.api.mapper.PurchaseOrderDTOMapper;
import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.api.dto.responses.PurchaseOrderResponseDTO;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.usecase.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunctions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouterRestTest {

    @Mock private CreateEventUseCase createEventUseCase;
    @Mock private GetEventUseCase getEventUseCase;
    @Mock private CreateComplimentaryUseCase createComplimentaryUseCase;
    @Mock private CreateOrderUseCase createOrderUseCase;
    @Mock private GetOrderUseCase getOrderUseCase;
    @Mock private ConfirmOrderUseCase confirmOrderUseCase;
    @Mock private CancelOrderUseCase cancelOrderUseCase;
    @Mock private GetOrderHistoryUseCase getOrderHistoryUseCase;
    @Mock private EventDTOMapper eventDTOMapper;
    @Mock private PurchaseOrderDTOMapper purchaseOrderDTOMapper;
    @Mock private OrderHistoryDTOMapper orderHistoryDTOMapper;
    @Mock private ValidationHelper validationHelper;
    @Mock private LogAppenderHelper logAppender;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        EventHandler eventHandler = new EventHandler(createEventUseCase, getEventUseCase,
                createComplimentaryUseCase, eventDTOMapper, purchaseOrderDTOMapper, validationHelper, logAppender);
        OrderHandler orderHandler = new OrderHandler(createOrderUseCase, getOrderUseCase,
                confirmOrderUseCase, cancelOrderUseCase, getOrderHistoryUseCase,
                purchaseOrderDTOMapper, orderHistoryDTOMapper, validationHelper, logAppender);

        RouterRest routerRest = new RouterRest();
        webTestClient = WebTestClient
                .bindToRouterFunction(routerRest.routerFunction(eventHandler, orderHandler))
                .build();
    }

    @Test
    void getEvents_shouldReturn200() {
        when(getEventUseCase.getAllPaginated(anyInt(), any())).thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/v1/events")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getEventById_notFound_shouldReturn404() {
        when(getEventUseCase.getEventDetail(anyString()))
                .thenReturn(Mono.error(new BusinessException(GeneralMessage.BUS_EVENT_NOT_FOUND)));

        webTestClient.get()
                .uri("/api/v1/events/evt-404")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createEvent_withoutAdminRole_shouldReturn403() {
        webTestClient.post()
                .uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Concert\",\"totalCapacity\":100,\"ticketPrice\":50.0,\"date\":\"2026-01-01T20:00:00Z\",\"venue\":\"Stadium\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void createOrder_withoutIdempotencyKey_shouldReturn400() {
        webTestClient.post()
                .uri("/api/v1/orders")
                .header("X-User-Id", "usr-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"evt-1\",\"quantity\":2}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createOrder_withIdempotencyKey_shouldReturn201() {
        TicketOrder order = TicketOrder.builder()
                .id("ord-1").eventId("evt-1").status(TicketStatus.RESERVED)
                .quantity(2).totalAmount(BigDecimal.valueOf(200))
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(validationHelper.validate(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(createOrderUseCase.execute(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(order));
        when(purchaseOrderDTOMapper.toResponseDTO(any())).thenReturn(PurchaseOrderResponseDTO.builder().orderId("ord-1").status("RESERVED").build());

        webTestClient.post()
                .uri("/api/v1/orders")
                .header("X-User-Id", "usr-1")
                .header("Idempotency-Key", "key-abc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"evt-1\",\"quantity\":2}")
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void getOrderById_notFound_shouldReturn404() {
        when(getOrderUseCase.getById(anyString(), anyString(), anyBoolean()))
                .thenReturn(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)));

        webTestClient.get()
                .uri("/api/v1/orders/ord-404")
                .header("X-User-Id", "usr-1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getOrderHistory_shouldReturn200() {
        when(getOrderHistoryUseCase.execute(anyString(), anyString(), anyBoolean()))
                .thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/v1/orders/ord-1/history")
                .header("X-User-Id", "usr-1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }
}
