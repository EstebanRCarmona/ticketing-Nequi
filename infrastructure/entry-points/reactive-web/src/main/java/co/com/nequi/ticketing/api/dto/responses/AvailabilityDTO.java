package co.com.nequi.ticketing.api.dto.responses;

import lombok.Builder;

@Builder
public record AvailabilityDTO(
        Integer totalCapacity,
        Integer available,
        Integer reserved,
        Integer pendingConfirmation,
        Integer sold,
        Integer complimentary,
        String asOf
) {
}
