package co.com.nequi.ticketing.api.dto.responses;

import lombok.Builder;

@Builder
public record OrderFailureDTO(String code, String description) {
}
