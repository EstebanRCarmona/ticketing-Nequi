package co.com.nequi.ticketing.api.config;

import co.com.nequi.ticketing.api.RouterRest;
import co.com.nequi.ticketing.api.handlers.EventHandler;
import co.com.nequi.ticketing.api.handlers.OrderHandler;
import co.com.nequi.ticketing.api.helper.LogAppenderHelper;
import co.com.nequi.ticketing.api.helper.ValidationHelper;
import co.com.nequi.ticketing.api.mapper.EventDTOMapper;
import co.com.nequi.ticketing.api.mapper.OrderHistoryDTOMapper;
import co.com.nequi.ticketing.api.mapper.PurchaseOrderDTOMapper;
import co.com.nequi.ticketing.usecase.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigTest {

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
        SecurityHeadersConfig securityHeadersConfig = new SecurityHeadersConfig();

        webTestClient = WebTestClient
                .bindToRouterFunction(routerRest.routerFunction(eventHandler, orderHandler))
                .webFilter(securityHeadersConfig)
                .build();
    }

    @Test
    void securityHeadersShouldBePresentOnEveryResponse() {
        when(getEventUseCase.getAllPaginated(anyInt(), any())).thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/v1/events")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Content-Security-Policy",
                        "default-src 'self'; frame-ancestors 'self'; form-action 'self'")
                .expectHeader().valueEquals("Strict-Transport-Security",
                        "max-age=31536000; includeSubDomains; preload")
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin");
    }
}
