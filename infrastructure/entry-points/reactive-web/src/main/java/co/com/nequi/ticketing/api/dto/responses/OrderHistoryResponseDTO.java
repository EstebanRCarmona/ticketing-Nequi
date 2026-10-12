package co.com.nequi.ticketing.api.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Builder
public record OrderHistoryResponseDTO(
        Long sequence,
        String fromStatus,
        List<String> seatIds,
        String toStatus,
        String reasonCode,
        String actor,
        String traceId,
        String occurredAt
) {
}
