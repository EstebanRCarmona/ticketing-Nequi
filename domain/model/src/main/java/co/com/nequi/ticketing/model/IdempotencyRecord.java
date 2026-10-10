package co.com.nequi.ticketing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {
    private String idempotencyKey;
    private String requestHash;
    private String orderId;
    private Long ttl;
}
