package co.com.nequi.ticketing.api.dto.request;

import lombok.Builder;

import java.util.List;

@Builder
public record ComplimentaryRequestDTO(
        List<String> seatIds,
        String recipient,
        String reason
) {
}
