package co.com.nequi.ticketing.model.exception;

import co.com.nequi.ticketing.model.enums.GeneralMessage;
import lombok.Getter;

@Getter
public class TechnicalException extends RuntimeException {

    private final GeneralMessage generalMessage;

    public TechnicalException(GeneralMessage generalMessage, Throwable cause) {
        super(generalMessage.getDescription(), cause);
        this.generalMessage = generalMessage;
    }

    public TechnicalException(GeneralMessage generalMessage, String detail, Throwable cause) {
        super(detail, cause);
        this.generalMessage = generalMessage;
    }
}
