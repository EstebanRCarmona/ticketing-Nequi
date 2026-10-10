package co.com.nequi.ticketing.api.helper;

import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

class ResponseHelperTest {

    @Test
    void buildResponse_shouldReturnCorrectStatus() {
        StepVerifier.create(ResponseHelper.buildResponse(GeneralMessage.SUCCESS, "data"))
                .expectNextMatches(r -> r.statusCode().value() == HttpStatus.OK.value())
                .verifyComplete();
    }

    @Test
    void buildResponse_withNullData_shouldReturnEmptyMap() {
        StepVerifier.create(ResponseHelper.buildResponse(GeneralMessage.CREATED, null))
                .expectNextMatches(r -> r.statusCode().value() == HttpStatus.CREATED.value())
                .verifyComplete();
    }

    @Test
    void buildErrorResponse_withMessage_shouldReturnCorrectStatus() {
        StepVerifier.create(ResponseHelper.buildErrorResponse(GeneralMessage.SEC_FORBIDDEN))
                .expectNextMatches(r -> r.statusCode().value() == HttpStatus.FORBIDDEN.value())
                .verifyComplete();
    }

    @Test
    void buildErrorResponse_withBusinessException_shouldUseItsMessage() {
        BusinessException ex = new BusinessException(GeneralMessage.BUS_ORDER_NOT_FOUND);

        StepVerifier.create(ResponseHelper.buildErrorResponse(ex))
                .expectNextMatches(r -> r.statusCode().value() == HttpStatus.NOT_FOUND.value())
                .verifyComplete();
    }

    @Test
    void buildErrorResponse_withTechnicalException_shouldUseItsMessage() {
        TechnicalException ex = new TechnicalException(GeneralMessage.TECH_DYNAMO_ERROR, new RuntimeException("db"));

        StepVerifier.create(ResponseHelper.buildErrorResponse(ex))
                .expectNextMatches(r -> r.statusCode().value() == HttpStatus.INTERNAL_SERVER_ERROR.value())
                .verifyComplete();
    }

    @Test
    void buildErrorResponse_withUnknownException_shouldReturn500() {
        StepVerifier.create(ResponseHelper.buildErrorResponse(new IllegalStateException("unexpected")))
                .expectNextMatches(r -> r.statusCode().value() == HttpStatus.INTERNAL_SERVER_ERROR.value())
                .verifyComplete();
    }
}
