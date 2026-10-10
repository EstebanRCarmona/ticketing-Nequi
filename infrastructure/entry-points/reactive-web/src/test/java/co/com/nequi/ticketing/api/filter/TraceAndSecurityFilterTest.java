package co.com.nequi.ticketing.api.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TraceAndSecurityFilterTest {

    private final TraceAndSecurityFilter filter = new TraceAndSecurityFilter();

    private MockServerWebExchange exchange(MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
    }

    private WebFilterChain chain(MockServerWebExchange ex) {
        return serverWebExchange -> Mono.empty();
    }

    @Test
    void filter_withAllHeaders_shouldUseProvidedValues() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header(TraceAndSecurityFilter.TRACE_ID_HEADER, "trace-123")
                .header(TraceAndSecurityFilter.ROLE_HEADER, "ADMIN")
                .header(TraceAndSecurityFilter.USER_ID_HEADER, "usr-001")
                .build();
        MockServerWebExchange ex = exchange(request);

        StepVerifier.create(filter.filter(ex, chain(ex)))
                .verifyComplete();

        assertEquals("trace-123", ex.getAttribute(TraceAndSecurityFilter.TRACE_ID_HEADER));
        assertEquals("ADMIN", ex.getAttribute(TraceAndSecurityFilter.ROLE_HEADER));
        assertEquals("usr-001", ex.getAttribute(TraceAndSecurityFilter.USER_ID_HEADER));
        assertEquals("trace-123", ex.getResponse().getHeaders().getFirst(TraceAndSecurityFilter.TRACE_ID_HEADER));
    }

    @Test
    void filter_withNoHeaders_shouldGenerateTraceIdAndDefaultRoleAndUserId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange ex = exchange(request);

        StepVerifier.create(filter.filter(ex, chain(ex)))
                .verifyComplete();

        assertNotNull(ex.getAttribute(TraceAndSecurityFilter.TRACE_ID_HEADER));
        assertEquals("USER", ex.getAttribute(TraceAndSecurityFilter.ROLE_HEADER));
        assertEquals("usr-default", ex.getAttribute(TraceAndSecurityFilter.USER_ID_HEADER));
    }

    @Test
    void filter_withBlankTraceId_shouldGenerateNewTraceId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header(TraceAndSecurityFilter.TRACE_ID_HEADER, "   ")
                .build();
        MockServerWebExchange ex = exchange(request);

        StepVerifier.create(filter.filter(ex, chain(ex)))
                .verifyComplete();

        String traceId = ex.getAttribute(TraceAndSecurityFilter.TRACE_ID_HEADER);
        assertNotNull(traceId);
        assertNotEquals("   ", traceId);
    }

    @Test
    void filter_withAuthorizationContainingAdmin_shouldSetAdminRole() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("Authorization", "Bearer token_ADMIN_xyz")
                .build();
        MockServerWebExchange ex = exchange(request);

        StepVerifier.create(filter.filter(ex, chain(ex)))
                .verifyComplete();

        assertEquals("ADMIN", ex.getAttribute(TraceAndSecurityFilter.ROLE_HEADER));
    }

    @Test
    void filter_withAuthorizationNotContainingAdmin_shouldSetUserRole() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("Authorization", "Bearer token_user_xyz")
                .build();
        MockServerWebExchange ex = exchange(request);

        StepVerifier.create(filter.filter(ex, chain(ex)))
                .verifyComplete();

        assertEquals("USER", ex.getAttribute(TraceAndSecurityFilter.ROLE_HEADER));
    }

    @Test
    void filter_withBlankRoleHeader_shouldFallbackToAuthorization() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header(TraceAndSecurityFilter.ROLE_HEADER, "  ")
                .header("Authorization", "Bearer ADMIN_token")
                .build();
        MockServerWebExchange ex = exchange(request);

        StepVerifier.create(filter.filter(ex, chain(ex)))
                .verifyComplete();

        assertEquals("ADMIN", ex.getAttribute(TraceAndSecurityFilter.ROLE_HEADER));
    }

    @Test
    void filter_withBlankUserId_shouldUseDefault() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header(TraceAndSecurityFilter.USER_ID_HEADER, "  ")
                .build();
        MockServerWebExchange ex = exchange(request);

        StepVerifier.create(filter.filter(ex, chain(ex)))
                .verifyComplete();

        assertEquals("usr-default", ex.getAttribute(TraceAndSecurityFilter.USER_ID_HEADER));
    }
}
