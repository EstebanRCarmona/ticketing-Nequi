package co.com.nequi.ticketing.api.helper;

import org.junit.jupiter.api.Test;

class LogAppenderHelperTest {

    private final LogAppenderHelper logAppender = new LogAppenderHelper();

    @Test
    void logRequest_shouldNotThrow() {
        logAppender.logRequest("POST /api/v1/events", "body");
    }

    @Test
    void logRequest_withNullBody_shouldNotThrow() {
        logAppender.logRequest("GET /api/v1/events", null);
    }

    @Test
    void logResponse_shouldNotThrow() {
        logAppender.logResponse("GET /api/v1/events", "response", 42L);
    }

    @Test
    void logError_shouldNotThrow() {
        logAppender.logError("POST /api/v1/orders", new RuntimeException("test error"));
    }
}
