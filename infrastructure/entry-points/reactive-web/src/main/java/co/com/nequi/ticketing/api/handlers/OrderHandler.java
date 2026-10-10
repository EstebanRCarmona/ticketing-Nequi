package co.com.nequi.ticketing.api.handlers;

import co.com.nequi.ticketing.api.dto.request.CancelOrderRequestDTO;
import co.com.nequi.ticketing.api.dto.request.ConfirmOrderRequestDTO;
import co.com.nequi.ticketing.api.dto.request.PurchaseOrderRequestDTO;
import co.com.nequi.ticketing.api.filter.TraceAndSecurityFilter;
import co.com.nequi.ticketing.api.helper.LogAppenderHelper;
import co.com.nequi.ticketing.api.helper.ResponseHelper;
import co.com.nequi.ticketing.api.helper.ValidationHelper;
import co.com.nequi.ticketing.api.mapper.OrderHistoryDTOMapper;
import co.com.nequi.ticketing.api.mapper.PurchaseOrderDTOMapper;
import co.com.nequi.ticketing.api.util.Constants;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.usecase.CancelOrderUseCase;
import co.com.nequi.ticketing.usecase.ConfirmOrderUseCase;
import co.com.nequi.ticketing.usecase.CreateOrderUseCase;
import co.com.nequi.ticketing.usecase.GetOrderHistoryUseCase;
import co.com.nequi.ticketing.usecase.GetOrderUseCase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static co.com.nequi.ticketing.api.util.Constants.CB_FALLBACK;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHandler {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final ConfirmOrderUseCase confirmOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final GetOrderHistoryUseCase getOrderHistoryUseCase;
    private final PurchaseOrderDTOMapper purchaseOrderDTOMapper;
    private final OrderHistoryDTOMapper orderHistoryDTOMapper;
    private final ValidationHelper validationHelper;
    private final LogAppenderHelper logAppender;

    @CircuitBreaker(name = Constants.CB_CREATE_ORDER, fallbackMethod = CB_FALLBACK)
    public Mono<ServerResponse> createOrder(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String endpoint = "POST /api/v1/orders";
        String idempotencyKey = request.headers().firstHeader(Constants.IDEMPOTENCY_KEY_HEADER);
        String userId = request.attribute(TraceAndSecurityFilter.USER_ID_HEADER).orElse(Constants.DEFAULT_USER_ID).toString();
        String traceId = request.attribute(TraceAndSecurityFilter.TRACE_ID_HEADER).orElse("").toString();

        return Mono.justOrEmpty(idempotencyKey)
                .filter(k -> !k.isBlank())
                .switchIfEmpty(Mono.error(() -> new BusinessException(GeneralMessage.VAL_IDEMPOTENCY_KEY_REQUIRED)))
                .flatMap(key -> request.bodyToMono(PurchaseOrderRequestDTO.class))
                .doOnNext(dto -> logAppender.logRequest(endpoint, dto))
                .flatMap(validationHelper::validate)
                .flatMap(dto -> createOrderUseCase.execute(dto.eventId(), userId, dto.quantity(), idempotencyKey, hashBody(dto), traceId))
                .map(purchaseOrderDTOMapper::toResponseDTO)
                .flatMap(response -> {
                    logAppender.logResponse(endpoint, response, System.currentTimeMillis() - startTime);
                    return ResponseHelper.buildResponse(GeneralMessage.CREATED, response);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return ResponseHelper.buildErrorResponse(error);
                });
    }

    @CircuitBreaker(name = Constants.CB_GET_ORDER, fallbackMethod = CB_FALLBACK)
    public Mono<ServerResponse> getOrderById(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String orderId = request.pathVariable("orderId");
        String endpoint = "GET /api/v1/orders/" + orderId;
        String userId = request.attribute(TraceAndSecurityFilter.USER_ID_HEADER).orElse(Constants.DEFAULT_USER_ID).toString();
        boolean isAdmin = Constants.ROLE_ADMIN.equalsIgnoreCase(
                request.attribute(TraceAndSecurityFilter.ROLE_HEADER).orElse(Constants.DEFAULT_ROLE).toString());

        logAppender.logRequest(endpoint, orderId);

        return getOrderUseCase.getById(orderId, userId, isAdmin)
                .map(purchaseOrderDTOMapper::toResponseDTO)
                .flatMap(response -> {
                    logAppender.logResponse(endpoint, response, System.currentTimeMillis() - startTime);
                    return ResponseHelper.buildResponse(GeneralMessage.SUCCESS, response);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return ResponseHelper.buildErrorResponse(error);
                });
    }

    @CircuitBreaker(name = Constants.CB_CONFIRM_ORDER, fallbackMethod = CB_FALLBACK)
    public Mono<ServerResponse> confirmOrder(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String orderId = request.pathVariable("orderId");
        String endpoint = "POST /api/v1/orders/" + orderId + "/confirm";
        String userId = request.attribute(TraceAndSecurityFilter.USER_ID_HEADER).orElse(Constants.DEFAULT_USER_ID).toString();
        String traceId = request.attribute(TraceAndSecurityFilter.TRACE_ID_HEADER).orElse("").toString();
        boolean isAdmin = Constants.ROLE_ADMIN.equalsIgnoreCase(
                request.attribute(TraceAndSecurityFilter.ROLE_HEADER).orElse(Constants.DEFAULT_ROLE).toString());

        return request.bodyToMono(ConfirmOrderRequestDTO.class)
                .defaultIfEmpty(new ConfirmOrderRequestDTO(null))
                .doOnNext(dto -> logAppender.logRequest(endpoint, dto))
                .flatMap(dto -> confirmOrderUseCase.execute(orderId, userId, isAdmin, dto.paymentReference(), traceId))
                .map(purchaseOrderDTOMapper::toResponseDTO)
                .flatMap(response -> {
                    logAppender.logResponse(endpoint, response, System.currentTimeMillis() - startTime);
                    return ResponseHelper.buildResponse(GeneralMessage.ACCEPTED, response);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return ResponseHelper.buildErrorResponse(error);
                });
    }

    @CircuitBreaker(name = Constants.CB_CANCEL_ORDER, fallbackMethod = CB_FALLBACK)
    public Mono<ServerResponse> cancelOrder(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String orderId = request.pathVariable("orderId");
        String endpoint = "POST /api/v1/orders/" + orderId + "/cancel";
        String userId = request.attribute(TraceAndSecurityFilter.USER_ID_HEADER).orElse(Constants.DEFAULT_USER_ID).toString();
        String traceId = request.attribute(TraceAndSecurityFilter.TRACE_ID_HEADER).orElse("").toString();
        boolean isAdmin = Constants.ROLE_ADMIN.equalsIgnoreCase(
                request.attribute(TraceAndSecurityFilter.ROLE_HEADER).orElse(Constants.DEFAULT_ROLE).toString());

        return request.bodyToMono(CancelOrderRequestDTO.class)
                .defaultIfEmpty(new CancelOrderRequestDTO(null))
                .doOnNext(dto -> logAppender.logRequest(endpoint, dto))
                .flatMap(dto -> cancelOrderUseCase.execute(orderId, userId, isAdmin, dto.reason(), traceId))
                .map(purchaseOrderDTOMapper::toResponseDTO)
                .flatMap(response -> {
                    logAppender.logResponse(endpoint, response, System.currentTimeMillis() - startTime);
                    return ResponseHelper.buildResponse(GeneralMessage.SUCCESS, response);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return ResponseHelper.buildErrorResponse(error);
                });
    }

    @CircuitBreaker(name = Constants.CB_GET_ORDER_HISTORY, fallbackMethod = CB_FALLBACK)
    public Mono<ServerResponse> getOrderHistory(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String orderId = request.pathVariable("orderId");
        String endpoint = "GET /api/v1/orders/" + orderId + "/history";
        String userId = request.attribute(TraceAndSecurityFilter.USER_ID_HEADER).orElse(Constants.DEFAULT_USER_ID).toString();
        boolean isAdmin = Constants.ROLE_ADMIN.equalsIgnoreCase(
                request.attribute(TraceAndSecurityFilter.ROLE_HEADER).orElse(Constants.DEFAULT_ROLE).toString());

        logAppender.logRequest(endpoint, orderId);

        return getOrderHistoryUseCase.execute(orderId, userId, isAdmin)
                .map(orderHistoryDTOMapper::toResponseDTO)
                .collectList()
                .flatMap(list -> {
                    logAppender.logResponse(endpoint, list, System.currentTimeMillis() - startTime);
                    return ResponseHelper.buildResponse(GeneralMessage.SUCCESS, list);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return ResponseHelper.buildErrorResponse(error);
                });
    }

    @SuppressWarnings("unused")
    public Mono<ServerResponse> fallback(ServerRequest request, CallNotPermittedException ex) {
        log.error("[CB_OPEN] Circuit breaker open for request: {}", request.path(), ex);
        return ResponseHelper.buildErrorResponse(GeneralMessage.TECH_CIRCUIT_OPEN);
    }

    @SuppressWarnings("unused")
    public Mono<ServerResponse> fallback(ServerRequest request, TechnicalException ex) {
        log.error("[CB_FALLBACK] Technical exception for request: {}", request.path(), ex);
        return ResponseHelper.buildErrorResponse(ex.getGeneralMessage());
    }

    @SuppressWarnings("unused")
    public Mono<ServerResponse> fallback(ServerRequest request, Exception ex) {
        log.error("[CB_FALLBACK] Unexpected exception for request: {}", request.path(), ex);
        return ResponseHelper.buildErrorResponse(GeneralMessage.TECH_INTERNAL_ERROR);
    }

    private String hashBody(Object body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(body.toString().getBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(body.hashCode());
        }
    }
}
