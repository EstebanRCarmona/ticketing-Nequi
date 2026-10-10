package co.com.nequi.ticketing.api.helper;

import co.com.nequi.ticketing.api.dto.request.ComplimentaryRequestDTO;
import co.com.nequi.ticketing.api.dto.request.ConfirmOrderRequestDTO;
import co.com.nequi.ticketing.api.dto.request.EventRequestDTO;
import co.com.nequi.ticketing.api.dto.request.PurchaseOrderRequestDTO;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.exception.BusinessException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class ValidationHelper {

    public <T> Mono<T> validate(T target) {
        if (target == null) {
            return invalid(GeneralMessage.VAL_INVALID_INPUT);
        }
        return validateTarget(target).thenReturn(target);
    }

    private Mono<Void> validateTarget(Object target) {
        return switch (target) {
            case PurchaseOrderRequestDTO dto -> validatePurchaseOrder(dto);
            case EventRequestDTO dto -> validateEvent(dto);
            case ConfirmOrderRequestDTO dto -> validateConfirmOrder(dto);
            case ComplimentaryRequestDTO dto -> validateComplimentary(dto);
            default -> Mono.empty();
        };
    }

    private Mono<Void> validatePurchaseOrder(PurchaseOrderRequestDTO dto) {
        if (isBlank(dto.eventId())) {
            return invalid(GeneralMessage.VAL_REQUIRED_EVENT_ID);
        }
        if (dto.quantity() == null || dto.quantity() < 1) {
            return invalid(GeneralMessage.VAL_INVALID_QUANTITY);
        }
        return Mono.empty();
    }

    private Mono<Void> validateEvent(EventRequestDTO dto) {
        if (isBlank(dto.name())) {
            return invalid(GeneralMessage.VAL_REQUIRED_EVENT_NAME);
        }
        if (isBlank(dto.eventDate())) {
            return invalid(GeneralMessage.VAL_REQUIRED_EVENT_DATE);
        }
        if (!isIsoInstant(dto.eventDate())) {
            return invalid(GeneralMessage.VAL_INVALID_EVENT_DATE);
        }
        if (isBlank(dto.venue())) {
            return invalid(GeneralMessage.VAL_REQUIRED_EVENT_VENUE);
        }
        if (dto.totalCapacity() == null || dto.totalCapacity() < 1) {
            return invalid(GeneralMessage.VAL_REQUIRED_TOTAL_CAPACITY);
        }
        if (dto.ticketPrice() == null || dto.ticketPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return invalid(GeneralMessage.VAL_REQUIRED_TICKET_PRICE);
        }
        return Mono.empty();
    }

    private Mono<Void> validateConfirmOrder(ConfirmOrderRequestDTO dto) {
        if (isBlank(dto.paymentReference())) {
            return invalid(GeneralMessage.VAL_REQUIRED_PAYMENT_REFERENCE);
        }
        return Mono.empty();
    }

    private Mono<Void> validateComplimentary(ComplimentaryRequestDTO dto) {
        if (dto.quantity() == null || dto.quantity() < 1 || dto.quantity() > 100) {
            return invalid(GeneralMessage.VAL_INVALID_QUANTITY);
        }
        if (isBlank(dto.recipient())) {
            return invalid(GeneralMessage.VAL_REQUIRED_RECIPIENT);
        }
        if (isBlank(dto.reason())) {
            return invalid(GeneralMessage.VAL_REQUIRED_REASON);
        }
        return Mono.empty();
    }

    private <T> Mono<T> invalid(GeneralMessage message) {
        return Mono.error(new BusinessException(message));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isIsoInstant(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
