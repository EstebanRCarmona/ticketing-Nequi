package co.com.nequi.ticketing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import co.com.nequi.ticketing.model.enums.OrderType;
import co.com.nequi.ticketing.model.enums.ReleaseReason;
import co.com.nequi.ticketing.model.enums.TicketStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TicketOrder {
    private String id;
    private String eventId;
    private String userId;
    private OrderType orderType;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String currency;
    private TicketStatus status;
    private String expiryShard;
    private ReleaseReason releaseReason;
    private String failureCode;
    private String failureDescription;
    private String paymentReference;
    private String recipient;
    private String complimentaryReason;
    private String traceId;
    private Long version;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant updatedAt;
}
