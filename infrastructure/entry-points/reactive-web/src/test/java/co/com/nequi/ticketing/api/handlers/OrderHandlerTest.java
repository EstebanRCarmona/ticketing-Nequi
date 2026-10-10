package co.com.nequi.ticketing.api.handlers;

import co.com.nequi.ticketing.api.dto.request.CancelOrderRequestDTO;
import co.com.nequi.ticketing.api.dto.request.ConfirmOrderRequestDTO;
import co.com.nequi.ticketing.api.dto.request.PurchaseOrderRequestDTO;
import co.com.nequi.ticketing.api.dto.responses.OrderHistoryResponseDTO;
import co.com.nequi.ticketing.api.dto.responses.PurchaseOrderResponseDTO;
import co.com.nequi.ticketing.api.filter.TraceAndSecurityFilter;
import co.com.nequi.ticketing.api.helper.LogAppenderHelper;
import co.com.nequi.ticketing.api.helper.ValidationHelper;
import co.com.nequi.ticketing.api.mapper.OrderHistoryDTOMapper;
import co.com.nequi.ticketing.api.mapper.PurchaseOrderDTOMapper;
import co.com.nequi.ticketing.api.util.Constants;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.usecase.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderHandlerTest {

    @Mock private CreateOrderUseCase createOrderUseCase;
    @Mock private GetOrderUseCase getOrderUseCase;
    @Mock private ConfirmOrderUseCase confirmOrderUseCase;
    @Mock private CancelOrderUseCase cancelOrderUseCase;
    @Mock private GetOrderHistoryUseCase getOrderHistoryUseCase;
    @Mock private PurchaseOrderDTOMapper purchaseOrderDTOMapper;
    @Mock private OrderHistoryDTOMapper orderHistoryDTOMapper;
    @Mock private ValidationHelper validationHelper;
    @Mock private LogAppenderHelper logAppender;

    private OrderHandler handler;

    private static final TicketOrder SAMPLE_ORDER = TicketOrder.builder()
            .id("ord-1").eventId("evt-1").status(TicketStatus.RESERVED)
            .quantity(2).totalAmount(BigDecimal.valueOf(200))
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();

    private static final PurchaseOrderResponseDTO SAMPLE_RESPONSE =
            PurchaseOrderResponseDTO.builder().orderId("ord-1").status("RESERVED").build();

    @BeforeEach
    void setUp() {
        handler = new OrderHandler(createOrderUseCase, getOrderUseCase, confirmOrderUseCase,
                cancelOrderUseCase, getOrderHistoryUseCase, purchaseOrderDTOMapper,
                orderHistoryDTOMapper, validationHelper, logAppender);
    }

    // ── createOrder ───────────────────────────────────────────────────────────

    @Test
    void createOrder_withIdempotencyKey_shouldReturn201() {
        PurchaseOrderRequestDTO dto = PurchaseOrderRequestDTO.builder().eventId("evt-1").quantity(2).build();

        when(validationHelper.validate(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(createOrderUseCase.execute(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(SAMPLE_ORDER));
        when(purchaseOrderDTOMapper.toResponseDTO(any())).thenReturn(SAMPLE_RESPONSE);

        ServerRequest request = MockServerRequest.builder()
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.TRACE_ID_HEADER, "trace-1")
                .header(Constants.IDEMPOTENCY_KEY_HEADER, "idem-key-1")
                .body(Mono.just(dto));

        StepVerifier.create(handler.createOrder(request))
                .expectNextMatches(r -> r.statusCode().value() == 201)
                .verifyComplete();
    }

    @Test
    void createOrder_withoutIdempotencyKey_shouldReturn400() {
        ServerRequest request = MockServerRequest.builder()
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .body(Mono.just(PurchaseOrderRequestDTO.builder().build()));

        StepVerifier.create(handler.createOrder(request))
                .expectNextMatches(r -> r.statusCode().value() == 400)
                .verifyComplete();
    }

    @Test
    void createOrder_withValidationError_shouldReturn400() {
        when(validationHelper.validate(any()))
                .thenReturn(Mono.error(new BusinessException(GeneralMessage.VAL_INVALID_INPUT)));

        ServerRequest request = MockServerRequest.builder()
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.TRACE_ID_HEADER, "trace-1")
                .header(Constants.IDEMPOTENCY_KEY_HEADER, "idem-key-1")
                .body(Mono.just(PurchaseOrderRequestDTO.builder().eventId("evt-1").quantity(2).build()));

        StepVerifier.create(handler.createOrder(request))
                .expectNextMatches(r -> r.statusCode().value() == 400)
                .verifyComplete();
    }

    @Test
    void createOrder_withUseCaseError_shouldReturn409() {
        PurchaseOrderRequestDTO dto = PurchaseOrderRequestDTO.builder().eventId("evt-1").quantity(2).build();

        when(validationHelper.validate(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(createOrderUseCase.execute(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new BusinessException(GeneralMessage.BUS_INSUFFICIENT_INVENTORY)));

        ServerRequest request = MockServerRequest.builder()
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.TRACE_ID_HEADER, "trace-1")
                .header(Constants.IDEMPOTENCY_KEY_HEADER, "idem-key-1")
                .body(Mono.just(dto));

        StepVerifier.create(handler.createOrder(request))
                .expectNextMatches(r -> r.statusCode().value() == 409)
                .verifyComplete();
    }

    // ── getOrderById ──────────────────────────────────────────────────────────

    @Test
    void getOrderById_shouldReturn200() {
        when(getOrderUseCase.getById(anyString(), anyString(), anyBoolean()))
                .thenReturn(Mono.just(SAMPLE_ORDER));
        when(purchaseOrderDTOMapper.toResponseDTO(any())).thenReturn(SAMPLE_RESPONSE);

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("orderId", "ord-1")
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, "USER")
                .build();

        StepVerifier.create(handler.getOrderById(request))
                .expectNextMatches(r -> r.statusCode().value() == 200)
                .verifyComplete();
    }

    @Test
    void getOrderById_asAdmin_shouldReturn200() {
        when(getOrderUseCase.getById(anyString(), anyString(), eq(true)))
                .thenReturn(Mono.just(SAMPLE_ORDER));
        when(purchaseOrderDTOMapper.toResponseDTO(any())).thenReturn(SAMPLE_RESPONSE);

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("orderId", "ord-1")
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, Constants.ROLE_ADMIN)
                .build();

        StepVerifier.create(handler.getOrderById(request))
                .expectNextMatches(r -> r.statusCode().value() == 200)
                .verifyComplete();
    }

    @Test
    void getOrderById_notFound_shouldReturn404() {
        when(getOrderUseCase.getById(anyString(), anyString(), anyBoolean()))
                .thenReturn(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("orderId", "ord-404")
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .build();

        StepVerifier.create(handler.getOrderById(request))
                .expectNextMatches(r -> r.statusCode().value() == 404)
                .verifyComplete();
    }

    // ── confirmOrder ──────────────────────────────────────────────────────────

    @Test
    void confirmOrder_shouldReturn202() {
        TicketOrder confirmed = SAMPLE_ORDER.toBuilder().status(TicketStatus.PENDING_CONFIRMATION).build();
        PurchaseOrderResponseDTO confirmedDTO = PurchaseOrderResponseDTO.builder().orderId("ord-1").status("PENDING_CONFIRMATION").build();

        when(confirmOrderUseCase.execute(anyString(), anyString(), anyBoolean(), anyString(), anyString()))
                .thenReturn(Mono.just(confirmed));
        when(purchaseOrderDTOMapper.toResponseDTO(any())).thenReturn(confirmedDTO);

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("orderId", "ord-1")
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.TRACE_ID_HEADER, "trace-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, "USER")
                .body(Mono.just(new ConfirmOrderRequestDTO("tok_visa_123")));

        StepVerifier.create(handler.confirmOrder(request))
                .expectNextMatches(r -> r.statusCode().value() == 202)
                .verifyComplete();
    }

    @Test
    void confirmOrder_withEmptyBody_shouldReturn202() {
        when(confirmOrderUseCase.execute(anyString(), anyString(), anyBoolean(), isNull(), anyString()))
                .thenReturn(Mono.just(SAMPLE_ORDER));
        when(purchaseOrderDTOMapper.toResponseDTO(any())).thenReturn(SAMPLE_RESPONSE);

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("orderId", "ord-1")
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.TRACE_ID_HEADER, "trace-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, "USER")
                .body(Mono.empty());

        StepVerifier.create(handler.confirmOrder(request))
                .expectNextMatches(r -> r.statusCode().value() == 202)
                .verifyComplete();
    }

    @Test
    void confirmOrder_withError_shouldReturn422() {
        when(confirmOrderUseCase.execute(anyString(), anyString(), anyBoolean(), anyString(), anyString()))
                .thenReturn(Mono.error(new BusinessException(GeneralMessage.BUS_ORDER_EXPIRED)));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("orderId", "ord-1")
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.TRACE_ID_HEADER, "trace-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, "USER")
                .body(Mono.just(new ConfirmOrderRequestDTO("tok_visa_123")));

        StepVerifier.create(handler.confirmOrder(request))
                .expectNextMatches(r -> r.statusCode().value() == 422)
                .verifyComplete();
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Test
    void cancelOrder_shouldReturn200() {
        TicketOrder cancelled = SAMPLE_ORDER.toBuilder().status(TicketStatus.CANCELLED).build();
        PurchaseOrderResponseDTO cancelledDTO = PurchaseOrderResponseDTO.builder().orderId("ord-1").status("CANCELLED").build();

        when(cancelOrderUseCase.execute(anyString(), anyString(), anyBoolean(), anyString(), anyString()))
                .thenReturn(Mono.just(cancelled));
        when(purchaseOrderDTOMapper.toResponseDTO(any())).thenReturn(cancelledDTO);

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("orderId", "ord-1")
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.TRACE_ID_HEADER, "trace-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, "USER")
                .body(Mono.just(new CancelOrderRequestDTO("CUSTOMER_REQUEST")));

        StepVerifier.create(handler.cancelOrder(request))
                .expectNextMatches(r -> r.statusCode().value() == 200)
                .verifyComplete();
    }

    @Test
    void cancelOrder_withEmptyBody_shouldReturn200() {
        when(cancelOrderUseCase.execute(anyString(), anyString(), anyBoolean(), isNull(), anyString()))
                .thenReturn(Mono.just(SAMPLE_ORDER));
        when(purchaseOrderDTOMapper.toResponseDTO(any())).thenReturn(SAMPLE_RESPONSE);

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("orderId", "ord-1")
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.TRACE_ID_HEADER, "trace-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, "USER")
                .body(Mono.empty());

        StepVerifier.create(handler.cancelOrder(request))
                .expectNextMatches(r -> r.statusCode().value() == 200)
                .verifyComplete();
    }

    @Test
    void cancelOrder_withError_shouldReturn409() {
        when(cancelOrderUseCase.execute(anyString(), anyString(), anyBoolean(), anyString(), anyString()))
                .thenReturn(Mono.error(new BusinessException(GeneralMessage.BUS_INVALID_STATE_TRANSITION)));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("orderId", "ord-1")
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.TRACE_ID_HEADER, "trace-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, "USER")
                .body(Mono.just(new CancelOrderRequestDTO("CUSTOMER_REQUEST")));

        StepVerifier.create(handler.cancelOrder(request))
                .expectNextMatches(r -> r.statusCode().value() == 409)
                .verifyComplete();
    }

    // ── getOrderHistory ───────────────────────────────────────────────────────

    @Test
    void getOrderHistory_shouldReturn200() {
        OrderHistory history = OrderHistory.builder().orderId("ord-1").sequence(1L).build();
        OrderHistoryResponseDTO historyDTO = OrderHistoryResponseDTO.builder().sequence(1L).build();

        when(getOrderHistoryUseCase.execute(anyString(), anyString(), anyBoolean()))
                .thenReturn(Flux.just(history));
        when(orderHistoryDTOMapper.toResponseDTO(any())).thenReturn(historyDTO);

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("orderId", "ord-1")
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, "USER")
                .build();

        StepVerifier.create(handler.getOrderHistory(request))
                .expectNextMatches(r -> r.statusCode().value() == 200)
                .verifyComplete();
    }

    @Test
    void getOrderHistory_withError_shouldReturn404() {
        when(getOrderHistoryUseCase.execute(anyString(), anyString(), anyBoolean()))
                .thenReturn(Flux.error(new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND)));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("orderId", "ord-404")
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, "USER")
                .build();

        StepVerifier.create(handler.getOrderHistory(request))
                .expectNextMatches(r -> r.statusCode().value() == 404)
                .verifyComplete();
    }

    // ── fallback methods ──────────────────────────────────────────────────────

    @Test
    void fallback_withCallNotPermittedException_shouldReturn503() {
        ServerRequest request = MockServerRequest.builder().build();
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.ofDefaults("test"));

        StepVerifier.create(handler.fallback(request, ex))
                .expectNextMatches(r -> r.statusCode().value() == 503)
                .verifyComplete();
    }

    @Test
    void fallback_withTechnicalException_shouldReturnItsStatus() {
        ServerRequest request = MockServerRequest.builder().build();
        TechnicalException ex = new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, new RuntimeException());

        StepVerifier.create(handler.fallback(request, ex))
                .expectNextMatches(r -> r.statusCode().value() == 500)
                .verifyComplete();
    }

    @Test
    void fallback_withGenericException_shouldReturn500() {
        ServerRequest request = MockServerRequest.builder().build();

        StepVerifier.create(handler.fallback(request, new RuntimeException("boom")))
                .expectNextMatches(r -> r.statusCode().value() == 500)
                .verifyComplete();
    }
}
