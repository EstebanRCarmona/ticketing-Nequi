package co.com.nequi.ticketing.api.helper;

import co.com.nequi.ticketing.api.dto.request.ComplimentaryRequestDTO;
import co.com.nequi.ticketing.api.dto.request.ConfirmOrderRequestDTO;
import co.com.nequi.ticketing.api.dto.request.EventRequestDTO;
import co.com.nequi.ticketing.api.dto.request.PurchaseOrderRequestDTO;
import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.exception.BusinessException;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

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
    void validateNullTargetShouldReturnInvalidInput() {
        StepVerifier.create(validationHelper.validate(null))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_INVALID_INPUT, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validateWithUnknownTypeShouldPassThrough() {
        StepVerifier.create(validationHelper.validate(new Object()))
            .expectNextMatches(Object.class::isInstance)
                .verifyComplete();
    }

    @Test
    void validatePurchaseOrderWithBlankEventIdShouldReturnEventIdMessage() {
        PurchaseOrderRequestDTO dto = new PurchaseOrderRequestDTO("   ", List.of("S-00001"));

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_REQUIRED_EVENT_ID, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validatePurchaseOrderWithMissingSeatIdsShouldReturnQuantityMessage() {
        PurchaseOrderRequestDTO dto = new PurchaseOrderRequestDTO("evt-1", List.of());

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_INVALID_SEAT_SELECTION, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validatePurchaseOrderWithValidDataShouldPassThrough() {
        PurchaseOrderRequestDTO dto = new PurchaseOrderRequestDTO("evt-1", List.of("S-00001"));

        StepVerifier.create(validationHelper.validate(dto))
                .expectNextMatches(PurchaseOrderRequestDTO.class::isInstance)
                .verifyComplete();
    }

    @Test
    void validateEventWithBlankNameShouldReturnNameMessage() {
        EventRequestDTO dto = new EventRequestDTO("   ", "2026-01-01T20:00:00Z", null, "Stadium", 100, BigDecimal.TEN);

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_REQUIRED_EVENT_NAME, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validateEventWithBlankDateShouldReturnDateMessage() {
        EventRequestDTO dto = new EventRequestDTO("Concert", "   ", null, "Stadium", 100, BigDecimal.TEN);

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_REQUIRED_EVENT_DATE, businessException.getGeneralMessage());
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

    @Test
    void validateEventWithBlankVenueShouldReturnVenueMessage() {
        EventRequestDTO dto = new EventRequestDTO("Concert", "2026-01-01T20:00:00Z", null, "   ", 100, BigDecimal.TEN);

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_REQUIRED_EVENT_VENUE, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validateEventWithInvalidCapacityShouldReturnCapacityMessage() {
        EventRequestDTO dto = new EventRequestDTO("Concert", "2026-01-01T20:00:00Z", null, "Stadium", 0, BigDecimal.TEN);

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_REQUIRED_TOTAL_CAPACITY, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validateEventWithInvalidPriceShouldReturnPriceMessage() {
        EventRequestDTO dto = new EventRequestDTO("Concert", "2026-01-01T20:00:00Z", null, "Stadium", 100, BigDecimal.ZERO);

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_REQUIRED_TICKET_PRICE, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validateEventWithValidDataShouldPassThrough() {
        EventRequestDTO dto = new EventRequestDTO("Concert", "2026-01-01T20:00:00Z", null, "Stadium", 100, BigDecimal.TEN);

        StepVerifier.create(validationHelper.validate(dto))
                .expectNextMatches(EventRequestDTO.class::isInstance)
                .verifyComplete();
    }

    @Test
    void validateConfirmOrderWithBlankReferenceShouldReturnPaymentReferenceMessage() {
        ConfirmOrderRequestDTO dto = new ConfirmOrderRequestDTO("   ");

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_REQUIRED_PAYMENT_REFERENCE, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validateConfirmOrderWithValidDataShouldPassThrough() {
        ConfirmOrderRequestDTO dto = new ConfirmOrderRequestDTO("pay-1");

        StepVerifier.create(validationHelper.validate(dto))
                .expectNextMatches(ConfirmOrderRequestDTO.class::isInstance)
                .verifyComplete();
    }

    @Test
    void validateComplimentaryWithMissingSeatsShouldReturnQuantityMessage() {
        ComplimentaryRequestDTO dto = ComplimentaryRequestDTO.builder()
                .seatIds(List.of())
                .recipient("Press")
                .reason("Promo")
                .build();

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_INVALID_SEAT_SELECTION, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validateComplimentaryWithTooManySeatsShouldReturnQuantityMessage() {
        ComplimentaryRequestDTO dto = ComplimentaryRequestDTO.builder()
                .seatIds(java.util.stream.IntStream.rangeClosed(1, 11).mapToObj("S-%05d"::formatted).toList())
                .recipient("Press")
                .reason("Promo")
                .build();

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_INVALID_SEAT_SELECTION, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validateComplimentaryWithBlankRecipientShouldReturnRecipientMessage() {
        ComplimentaryRequestDTO dto = ComplimentaryRequestDTO.builder()
                .seatIds(List.of("S-00001"))
                .recipient("   ")
                .reason("Promo")
                .build();

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_REQUIRED_RECIPIENT, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validateComplimentaryWithBlankReasonShouldReturnReasonMessage() {
        ComplimentaryRequestDTO dto = ComplimentaryRequestDTO.builder()
                .seatIds(List.of("S-00001"))
                .recipient("Press")
                .reason("   ")
                .build();

        StepVerifier.create(validationHelper.validate(dto))
                .expectErrorSatisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertEquals(GeneralMessage.VAL_REQUIRED_REASON, businessException.getGeneralMessage());
                })
                .verify();
    }

    @Test
    void validateComplimentaryWithValidDataShouldPassThrough() {
        ComplimentaryRequestDTO dto = ComplimentaryRequestDTO.builder()
                .seatIds(List.of("S-00001", "S-00002", "S-00003", "S-00004", "S-00005"))
                .recipient("Press")
                .reason("Promo")
                .build();

        StepVerifier.create(validationHelper.validate(dto))
                .expectNextMatches(ComplimentaryRequestDTO.class::isInstance)
                .verifyComplete();
    }
}
