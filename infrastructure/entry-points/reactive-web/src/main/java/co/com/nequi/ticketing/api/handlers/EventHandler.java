package co.com.nequi.ticketing.api.handlers;

import co.com.nequi.ticketing.api.dto.request.ComplimentaryRequestDTO;
import co.com.nequi.ticketing.api.dto.request.EventRequestDTO;
import co.com.nequi.ticketing.api.filter.TraceAndSecurityFilter;
import co.com.nequi.ticketing.api.helper.LogAppenderHelper;
import co.com.nequi.ticketing.api.helper.ResponseHelper;
import co.com.nequi.ticketing.api.helper.ValidationHelper;
import co.com.nequi.ticketing.api.mapper.EventDTOMapper;
import co.com.nequi.ticketing.api.mapper.PurchaseOrderDTOMapper;
import co.com.nequi.ticketing.api.util.Constants;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.usecase.CreateComplimentaryUseCase;
import co.com.nequi.ticketing.usecase.CreateEventUseCase;
import co.com.nequi.ticketing.usecase.GetEventUseCase;
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
public class EventHandler {

    private final CreateEventUseCase createEventUseCase;
    private final GetEventUseCase getEventUseCase;
    private final CreateComplimentaryUseCase createComplimentaryUseCase;
    private final EventDTOMapper eventDTOMapper;
    private final PurchaseOrderDTOMapper purchaseOrderDTOMapper;
    private final ValidationHelper validationHelper;
    private final LogAppenderHelper logAppender;

    @CircuitBreaker(name = Constants.CB_CREATE_EVENT, fallbackMethod = CB_FALLBACK)
    public Mono<ServerResponse> createEvent(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String endpoint = "POST /api/v1/events";
        String role = request.attribute(TraceAndSecurityFilter.ROLE_HEADER).orElse(Constants.DEFAULT_ROLE).toString();

        return Mono.just(role)
                .filter(r -> Constants.ROLE_ADMIN.equalsIgnoreCase(r))
                .switchIfEmpty(Mono.error(() -> new BusinessException(GeneralMessage.SEC_FORBIDDEN)))
                .flatMap(r -> request.bodyToMono(EventRequestDTO.class))
                .doOnNext(dto -> logAppender.logRequest(endpoint, dto))
                .flatMap(validationHelper::validate)
                .map(eventDTOMapper::toDomain)
                .flatMap(createEventUseCase::execute)
                .map(eventDTOMapper::toResponseDTO)
                .flatMap(response -> {
                    logAppender.logResponse(endpoint, response, System.currentTimeMillis() - startTime);
                    return ResponseHelper.buildResponse(GeneralMessage.CREATED, response);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return ResponseHelper.buildErrorResponse(error);
                });
    }

    @CircuitBreaker(name = Constants.CB_GET_EVENTS, fallbackMethod = CB_FALLBACK)
    public Mono<ServerResponse> getAllEvents(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String endpoint = "GET /api/v1/events";
        int limit = request.queryParam("limit").map(Integer::parseInt).orElse(20);
        String cursor = request.queryParam("cursor").orElse(null);

        logAppender.logRequest(endpoint, null);

        return getEventUseCase.getAllPaginated(limit, cursor)
                .map(eventDTOMapper::toResponseDTO)
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

    @CircuitBreaker(name = Constants.CB_GET_EVENT_BY_ID, fallbackMethod = CB_FALLBACK)
    public Mono<ServerResponse> getEventById(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String eventId = request.pathVariable("eventId");
        String endpoint = "GET /api/v1/events/" + eventId;

        logAppender.logRequest(endpoint, eventId);

        return getEventUseCase.getEventDetail(eventId)
                .map(eventDTOMapper::toDetailResponseDTO)
                .flatMap(response -> {
                    logAppender.logResponse(endpoint, response, System.currentTimeMillis() - startTime);
                    return ResponseHelper.buildResponse(GeneralMessage.SUCCESS, response);
                })
                .onErrorResume(error -> {
                    logAppender.logError(endpoint, error);
                    return ResponseHelper.buildErrorResponse(error);
                });
    }

    @CircuitBreaker(name = Constants.CB_CREATE_COMPLIMENTARY, fallbackMethod = CB_FALLBACK)
    public Mono<ServerResponse> createComplimentary(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        String eventId = request.pathVariable("eventId");
        String endpoint = "POST /api/v1/events/" + eventId + "/complimentary";
        String role = request.attribute(TraceAndSecurityFilter.ROLE_HEADER).orElse(Constants.DEFAULT_ROLE).toString();
        String userId = request.attribute(TraceAndSecurityFilter.USER_ID_HEADER).orElse(Constants.DEFAULT_USER_ID).toString();
        String traceId = request.attribute(TraceAndSecurityFilter.TRACE_ID_HEADER).orElse("").toString();
        String idempotencyKey = request.headers().firstHeader(Constants.IDEMPOTENCY_KEY_HEADER);

        return Mono.just(role)
                .filter(r -> Constants.ROLE_ADMIN.equalsIgnoreCase(r))
                .switchIfEmpty(Mono.error(() -> new BusinessException(GeneralMessage.SEC_FORBIDDEN)))
                .flatMap(r -> Mono.justOrEmpty(idempotencyKey).filter(k -> !k.isBlank())
                        .switchIfEmpty(Mono.error(() -> new BusinessException(GeneralMessage.VAL_IDEMPOTENCY_KEY_REQUIRED))))
                .flatMap(key -> request.bodyToMono(ComplimentaryRequestDTO.class))
                .doOnNext(dto -> logAppender.logRequest(endpoint, dto))
                .flatMap(validationHelper::validate)
                .flatMap(dto -> createComplimentaryUseCase.execute(
                eventId, userId, dto.quantity(), dto.recipient(), dto.reason(), idempotencyKey, hashBody(dto), traceId))
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
