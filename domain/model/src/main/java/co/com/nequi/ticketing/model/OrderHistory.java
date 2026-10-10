package co.com.nequi.ticketing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import co.com.nequi.ticketing.model.enums.ActorType;
import co.com.nequi.ticketing.model.enums.TicketStatus;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderHistory {
    private String orderId;
    private Long sequence;
    private TicketStatus fromStatus;
    private TicketStatus toStatus;
    private String reasonCode;
    private ActorType actor;
    private Integer quantity;
    private String traceId;
    private String sqsMessageId;
    private Instant occurredAt;
}
