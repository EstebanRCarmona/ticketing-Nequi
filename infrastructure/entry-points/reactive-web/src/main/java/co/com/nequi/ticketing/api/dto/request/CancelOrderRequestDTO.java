package co.com.nequi.ticketing.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;

@Builder
public record CancelOrderRequestDTO(String reason) {
}
