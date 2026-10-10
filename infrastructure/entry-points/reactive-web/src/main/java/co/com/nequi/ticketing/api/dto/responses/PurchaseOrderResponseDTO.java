package co.com.nequi.ticketing.api.dto.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PurchaseOrderResponseDTO(
        String orderId,
        String eventId,
        String orderType,
        Integer quantity,
        String status,
        BigDecimal totalAmount,
        String currency,
        String expiresAt,
        String releaseReason,
        OrderFailureDTO failure,
        String createdAt,
        String updatedAt
) {
}
