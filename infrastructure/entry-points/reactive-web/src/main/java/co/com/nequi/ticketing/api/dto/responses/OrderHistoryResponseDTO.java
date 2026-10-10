package co.com.nequi.ticketing.api.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;

@Builder
public record OrderHistoryResponseDTO(
        Long sequence,
        String fromStatus,
        String toStatus,
        String reasonCode,
        String actor,
        String traceId,
        String occurredAt
) {
}
