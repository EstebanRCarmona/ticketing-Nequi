package co.com.nequi.ticketing.api.dto.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventResponseDTO(
        String eventId,
        String name,
        String eventDate,
        String venue,
        String category,
        BigDecimal ticketPrice,
        String currency,
        Integer totalCapacity,
        AvailabilityDTO availability
) {
}
