package co.com.nequi.ticketing.api.dto.request;

import lombok.Builder;

import java.util.List;

@Builder
public record PurchaseOrderRequestDTO(
        String eventId,
        List<String> seatIds
) {
}
