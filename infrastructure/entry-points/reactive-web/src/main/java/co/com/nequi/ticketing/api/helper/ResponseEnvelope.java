package co.com.nequi.ticketing.api.helper;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;

@Value
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ResponseEnvelope<T> {
    String statusCode;
    String description;
    Object data;

    public static <T> ResponseEnvelope<T> success(String statusCode, String description, T data) {
        return ResponseEnvelope.<T>builder()
                .statusCode(statusCode)
                .description(description)
                .data(data != null ? data : Collections.emptyMap())
                .build();
    }

    public static <T> ResponseEnvelope<T> error(String statusCode, String description) {
        return ResponseEnvelope.<T>builder()
                .statusCode(statusCode)
                .description(description)
                .data(Collections.emptyMap())
                .build();
    }
}
