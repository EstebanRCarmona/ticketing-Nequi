package co.com.nequi.ticketing.api.handlers;

import co.com.nequi.ticketing.api.dto.request.ComplimentaryRequestDTO;
import co.com.nequi.ticketing.api.dto.request.EventRequestDTO;
import co.com.nequi.ticketing.api.dto.responses.EventResponseDTO;
import co.com.nequi.ticketing.api.dto.responses.PurchaseOrderResponseDTO;
import co.com.nequi.ticketing.api.filter.TraceAndSecurityFilter;
import co.com.nequi.ticketing.api.helper.LogAppenderHelper;
import co.com.nequi.ticketing.api.helper.ValidationHelper;
import co.com.nequi.ticketing.api.mapper.EventDTOMapper;
import co.com.nequi.ticketing.api.mapper.PurchaseOrderDTOMapper;
import co.com.nequi.ticketing.api.util.Constants;
import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.EventDetail;
import co.com.nequi.ticketing.model.Inventory;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.usecase.CreateComplimentaryUseCase;
import co.com.nequi.ticketing.usecase.CreateEventUseCase;
import co.com.nequi.ticketing.usecase.GetEventUseCase;
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
class EventHandlerTest {

    @Mock private CreateEventUseCase createEventUseCase;
    @Mock private GetEventUseCase getEventUseCase;
    @Mock private CreateComplimentaryUseCase createComplimentaryUseCase;
    @Mock private EventDTOMapper eventDTOMapper;
    @Mock private PurchaseOrderDTOMapper purchaseOrderDTOMapper;
    @Mock private ValidationHelper validationHelper;
    @Mock private LogAppenderHelper logAppender;

    private EventHandler handler;

    private static final Event SAMPLE_EVENT = Event.builder().id("evt-1").name("Concert")
            .totalCapacity(100).ticketPrice(BigDecimal.TEN).build();
    private static final EventResponseDTO SAMPLE_EVENT_DTO =
            EventResponseDTO.builder().eventId("evt-1").name("Concert").build();

    @BeforeEach
    void setUp() {
        handler = new EventHandler(createEventUseCase, getEventUseCase, createComplimentaryUseCase,
                eventDTOMapper, purchaseOrderDTOMapper, validationHelper, logAppender);
    }

    // ── createEvent ──────────────────────────────────────────────────────────

    @Test
    void createEvent_asAdmin_shouldReturn201() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .name("Concert").eventDate("2026-01-01T20:00:00Z").venue("Stadium")
                .totalCapacity(100).ticketPrice(BigDecimal.TEN).build();

        when(validationHelper.validate(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventDTOMapper.toDomain(any(EventRequestDTO.class))).thenReturn(SAMPLE_EVENT);
        when(createEventUseCase.execute(any(Event.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventDTOMapper.toResponseDTO(any(Event.class))).thenReturn(SAMPLE_EVENT_DTO);

        ServerRequest request = MockServerRequest.builder()
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, Constants.ROLE_ADMIN)
                .body(Mono.just(dto));

        StepVerifier.create(handler.createEvent(request))
                .expectNextMatches(r -> r.statusCode().value() == 201)
                .verifyComplete();
    }

    @Test
    void createEvent_withoutAdminRole_shouldReturn403() {
        ServerRequest request = MockServerRequest.builder()
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, "USER")
                .body(Mono.just(EventRequestDTO.builder().build()));

        StepVerifier.create(handler.createEvent(request))
                .expectNextMatches(r -> r.statusCode().value() == 403)
                .verifyComplete();
    }

    @Test
    void createEvent_withNoRoleAttribute_shouldReturn403() {
        ServerRequest request = MockServerRequest.builder()
                .body(Mono.just(EventRequestDTO.builder().build()));

        StepVerifier.create(handler.createEvent(request))
                .expectNextMatches(r -> r.statusCode().value() == 403)
                .verifyComplete();
    }

    @Test
    void createEvent_withValidationError_shouldReturn400() {
        when(validationHelper.validate(any()))
                .thenReturn(Mono.error(new BusinessException(GeneralMessage.VAL_INVALID_INPUT)));

        ServerRequest request = MockServerRequest.builder()
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, Constants.ROLE_ADMIN)
                .body(Mono.just(EventRequestDTO.builder().build()));

        StepVerifier.create(handler.createEvent(request))
                .expectNextMatches(r -> r.statusCode().value() == 400)
                .verifyComplete();
    }

    @Test
    void createEvent_withTechnicalError_shouldReturn500() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .name("Concert").eventDate("2026-01-01T20:00:00Z").venue("Stadium")
                .totalCapacity(100).ticketPrice(BigDecimal.TEN).build();

        when(validationHelper.validate(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventDTOMapper.toDomain(any(EventRequestDTO.class))).thenReturn(SAMPLE_EVENT);
        when(createEventUseCase.execute(any(Event.class)))
                .thenReturn(Mono.error(new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, new RuntimeException())));

        ServerRequest request = MockServerRequest.builder()
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, Constants.ROLE_ADMIN)
                .body(Mono.just(dto));

        StepVerifier.create(handler.createEvent(request))
                .expectNextMatches(r -> r.statusCode().value() == 500)
                .verifyComplete();
    }

    // ── getAllEvents ──────────────────────────────────────────────────────────

    @Test
    void getAllEvents_shouldReturn200WithList() {
        when(getEventUseCase.getAllPaginated(eq(5), eq("tok"))).thenReturn(Flux.just(SAMPLE_EVENT));
        when(eventDTOMapper.toResponseDTO(any(Event.class))).thenReturn(SAMPLE_EVENT_DTO);

        ServerRequest request = MockServerRequest.builder()
                .queryParam("limit", "5")
                .queryParam("cursor", "tok")
                .build();

        StepVerifier.create(handler.getAllEvents(request))
                .expectNextMatches(r -> r.statusCode().value() == 200)
                .verifyComplete();
    }

    @Test
    void getAllEvents_withNoQueryParams_shouldUseDefaults() {
        when(getEventUseCase.getAllPaginated(eq(20), isNull())).thenReturn(Flux.empty());

        ServerRequest request = MockServerRequest.builder().build();

        StepVerifier.create(handler.getAllEvents(request))
                .expectNextMatches(r -> r.statusCode().value() == 200)
                .verifyComplete();
    }

    @Test
    void getAllEvents_withError_shouldReturn500() {
        when(getEventUseCase.getAllPaginated(anyInt(), any()))
                .thenReturn(Flux.error(new BusinessException(GeneralMessage.TECH_INTERNAL_ERROR)));

        ServerRequest request = MockServerRequest.builder().build();

        StepVerifier.create(handler.getAllEvents(request))
                .expectNextMatches(r -> r.statusCode().value() == 500)
                .verifyComplete();
    }

    // ── getEventById ─────────────────────────────────────────────────────────

    @Test
    void getEventById_shouldReturn200() {
        EventDetail detail = EventDetail.builder()
                .event(SAMPLE_EVENT)
                .inventory(Inventory.builder().eventId("evt-1").available(100).reserved(0)
                        .pendingConfirmation(0).sold(0).complimentary(0).totalCapacity(100).build())
                .build();

        when(getEventUseCase.getEventDetail("evt-1")).thenReturn(Mono.just(detail));
        when(eventDTOMapper.toDetailResponseDTO(any(EventDetail.class))).thenReturn(SAMPLE_EVENT_DTO);

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("eventId", "evt-1")
                .build();

        StepVerifier.create(handler.getEventById(request))
                .expectNextMatches(r -> r.statusCode().value() == 200)
                .verifyComplete();
    }

    @Test
    void getEventById_notFound_shouldReturn404() {
        when(getEventUseCase.getEventDetail("evt-404"))
                .thenReturn(Mono.error(new BusinessException(GeneralMessage.BUS_EVENT_NOT_FOUND)));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("eventId", "evt-404")
                .build();

        StepVerifier.create(handler.getEventById(request))
                .expectNextMatches(r -> r.statusCode().value() == 404)
                .verifyComplete();
    }

    // ── createComplimentary ───────────────────────────────────────────────────

    @Test
    void createComplimentary_asAdmin_shouldReturn201() {
        ComplimentaryRequestDTO dto = ComplimentaryRequestDTO.builder()
                .quantity(5).recipient("VIP Press").reason("Promotion").build();
        TicketOrder order = TicketOrder.builder().id("ord-1").status(TicketStatus.COMPLIMENTARY)
                .quantity(5).totalAmount(BigDecimal.ZERO).createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(validationHelper.validate(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(createComplimentaryUseCase.execute(anyString(), anyString(), anyInt(),
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(order));
        when(purchaseOrderDTOMapper.toResponseDTO(any(TicketOrder.class)))
                .thenReturn(PurchaseOrderResponseDTO.builder().orderId("ord-1").build());

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("eventId", "evt-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, Constants.ROLE_ADMIN)
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.TRACE_ID_HEADER, "trace-1")
                .header(Constants.IDEMPOTENCY_KEY_HEADER, "idem-key-1")
                .body(Mono.just(dto));

        StepVerifier.create(handler.createComplimentary(request))
                .expectNextMatches(r -> r.statusCode().value() == 201)
                .verifyComplete();
    }

    @Test
    void createComplimentary_withoutAdminRole_shouldReturn403() {
        ServerRequest request = MockServerRequest.builder()
                .pathVariable("eventId", "evt-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, "USER")
                .header(Constants.IDEMPOTENCY_KEY_HEADER, "idem-key-1")
                .body(Mono.just(ComplimentaryRequestDTO.builder().build()));

        StepVerifier.create(handler.createComplimentary(request))
                .expectNextMatches(r -> r.statusCode().value() == 403)
                .verifyComplete();
    }

    @Test
    void createComplimentary_withoutIdempotencyKey_shouldReturn400() {
        ServerRequest request = MockServerRequest.builder()
                .pathVariable("eventId", "evt-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, Constants.ROLE_ADMIN)
                .body(Mono.just(ComplimentaryRequestDTO.builder().build()));

        StepVerifier.create(handler.createComplimentary(request))
                .expectNextMatches(r -> r.statusCode().value() == 400)
                .verifyComplete();
    }

    @Test
    void createComplimentary_withValidationError_shouldReturn400() {
        when(validationHelper.validate(any()))
                .thenReturn(Mono.error(new BusinessException(GeneralMessage.VAL_INVALID_INPUT)));

        ServerRequest request = MockServerRequest.builder()
                .pathVariable("eventId", "evt-1")
                .attribute(TraceAndSecurityFilter.ROLE_HEADER, Constants.ROLE_ADMIN)
                .attribute(TraceAndSecurityFilter.USER_ID_HEADER, "usr-1")
                .attribute(TraceAndSecurityFilter.TRACE_ID_HEADER, "trace-1")
                .header(Constants.IDEMPOTENCY_KEY_HEADER, "idem-key-1")
                .body(Mono.just(ComplimentaryRequestDTO.builder().quantity(5).recipient("VIP").reason("Promo").build()));

        StepVerifier.create(handler.createComplimentary(request))
                .expectNextMatches(r -> r.statusCode().value() == 400)
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
        TechnicalException ex = new TechnicalException(GeneralMessage.TECH_SQS_ERROR, new RuntimeException());

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
