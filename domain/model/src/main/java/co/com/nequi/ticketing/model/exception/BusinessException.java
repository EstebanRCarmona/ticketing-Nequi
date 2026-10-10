package co.com.nequi.ticketing.model.exception;

import co.com.nequi.ticketing.model.enums.GeneralMessage;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final GeneralMessage generalMessage;

    public BusinessException(GeneralMessage generalMessage) {
        super(generalMessage.getDescription());
        this.generalMessage = generalMessage;
    }

    public BusinessException(GeneralMessage generalMessage, String customDetail) {
        super(customDetail);
        this.generalMessage = generalMessage;
    }
}
