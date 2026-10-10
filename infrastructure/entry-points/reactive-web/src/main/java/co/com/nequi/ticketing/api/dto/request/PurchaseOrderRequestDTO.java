package co.com.nequi.ticketing.api.dto.request;

import lombok.Builder;

@Builder
public record PurchaseOrderRequestDTO(
        String eventId,
        Integer quantity
) {
}
