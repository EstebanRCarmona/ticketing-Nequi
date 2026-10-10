package co.com.nequi.ticketing.api.helper;

import co.com.nequi.ticketing.api.dto.request.EventRequestDTO;
import co.com.nequi.ticketing.api.dto.request.PurchaseOrderRequestDTO;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.exception.BusinessException;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationHelperTest {

    private final ValidationHelper validationHelper = new ValidationHelper();

    @Test
    void validateWithValidObjectShouldPassThrough() {
        EventRequestDTO dto = new EventRequestDTO("Concert", "2026-01-01T20:00:00Z", null, "Stadium", 100, BigDecimal.TEN);

        StepVerifier.create(validationHelper.validate(dto))
                .expectNextMatches(d -> d.name().equals("Concert"))
                .verifyComplete();
    }

    @Test
    void validateWithInvalidObjectShouldReturnBusinessException() {
        EventRequestDTO dto = new EventRequestDTO(null, null, null, null, null, null);

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_REQUIRED_EVENT_NAME, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validatePurchaseOrderWithInvalidQuantityShouldReturnQuantityMessage() {
        PurchaseOrderRequestDTO dto = new PurchaseOrderRequestDTO("evt-1", 0);

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_INVALID_QUANTITY, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validateEventWithInvalidDateShouldReturnDateMessage() {
        EventRequestDTO dto = new EventRequestDTO("Concert", "not-a-date", null, "Stadium", 100, BigDecimal.TEN);

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_INVALID_EVENT_DATE, businessException.getGeneralMessage());
                })
                .verify();
    }
}
