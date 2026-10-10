package co.com.nequi.ticketing.api.dto.request;

import lombok.Builder;

@Builder
public record ComplimentaryRequestDTO(
        Integer quantity,
        String recipient,
        String reason
) {
}
