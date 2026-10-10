package co.com.nequi.ticketing.api.helper;

import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import lombok.experimental.UtilityClass;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@UtilityClass
public class ResponseHelper {

    public static <T> Mono<ServerResponse> buildResponse(GeneralMessage message, T data) {
        return ServerResponse.status(message.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ResponseEnvelope.success(message.getCode(), message.getDescription(), data));
    }

    public static Mono<ServerResponse> buildErrorResponse(GeneralMessage message) {
        return ServerResponse.status(message.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ResponseEnvelope.error(message.getCode(), message.getDescription()));
    }

    public static Mono<ServerResponse> buildErrorResponse(Throwable error) {
        return Mono.just(error)
                .filter(BusinessException.class::isInstance)
                .cast(BusinessException.class)
                .map(BusinessException::getGeneralMessage)
                .switchIfEmpty(Mono.just(error)
                        .filter(TechnicalException.class::isInstance)
                        .cast(TechnicalException.class)
                        .map(TechnicalException::getGeneralMessage))
                .defaultIfEmpty(GeneralMessage.TECH_INTERNAL_ERROR)
                .flatMap(ResponseHelper::buildErrorResponse);
    }
}
