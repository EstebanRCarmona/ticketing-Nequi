package co.com.nequi.ticketing.api.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogAppenderHelper {

    private static final Logger log = LoggerFactory.getLogger(LogAppenderHelper.class);

    public void logRequest(String endpoint, Object body) {
        log.info("[REQUEST] Endpoint: {} | Body: {}", endpoint, body);
    }

    public void logResponse(String endpoint, Object response, long durationMs) {
        log.info("[RESPONSE] Endpoint: {} | Time: {}ms | Response: {}", endpoint, durationMs, response);
    }

    public void logError(String endpoint, Throwable error) {
        log.error("[ERROR] Endpoint: {} | Message: {}", endpoint, error.getMessage(), error);
    }
}
