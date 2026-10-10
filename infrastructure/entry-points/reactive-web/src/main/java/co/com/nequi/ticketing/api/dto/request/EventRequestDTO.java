package co.com.nequi.ticketing.api.dto.request;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record EventRequestDTO(
        String name,
        String eventDate,
        String category,
        String venue,
        Integer totalCapacity,
        BigDecimal ticketPrice
) {
}
