package co.com.nequi.ticketing.api.filter;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

@Component
public class TraceAndSecurityFilter implements WebFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String ROLE_HEADER = "X-Role";
    public static final String USER_ID_HEADER = "X-User-Id";

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";
    private static final String DEFAULT_USER_ID = "usr-default";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER))
                .filter(v -> !v.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        String role = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(ROLE_HEADER))
                .filter(v -> !v.isBlank())
                .orElseGet(() -> Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("Authorization"))
                        .filter(auth -> auth.contains(ROLE_ADMIN))
                        .map(auth -> ROLE_ADMIN)
                        .orElse(ROLE_USER));

        String userId = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER))
                .filter(v -> !v.isBlank())
                .orElse(DEFAULT_USER_ID);

        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, traceId);
        exchange.getAttributes().put(TRACE_ID_HEADER, traceId);
        exchange.getAttributes().put(ROLE_HEADER, role);
        exchange.getAttributes().put(USER_ID_HEADER, userId);

        return chain.filter(exchange);
    }
}
