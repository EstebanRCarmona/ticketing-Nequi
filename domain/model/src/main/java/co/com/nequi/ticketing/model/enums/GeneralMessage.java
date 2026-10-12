package co.com.nequi.ticketing.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum GeneralMessage {
    SUCCESS("0", "SUCCESS", GeneralMessage.HTTP_200),
    CREATED("0", "Resource created successfully", GeneralMessage.HTTP_201),
    ACCEPTED("0", "Order queued for asynchronous processing", GeneralMessage.HTTP_202),

    // Validation Errors (VALxxx)
    VAL_INVALID_INPUT("VAL001", "Required field is missing or invalid", GeneralMessage.HTTP_400),
    VAL_INVALID_QUANTITY("VAL002", "Ticket quantity must be between 1 and maximum allowed", GeneralMessage.HTTP_400),
    VAL_IDEMPOTENCY_KEY_REQUIRED("VAL003", "Idempotency-Key header is required", GeneralMessage.HTTP_400),
    VAL_IDEMPOTENCY_MISMATCH("VAL004", "Idempotency key reused with different request body", GeneralMessage.HTTP_422),
    VAL_REQUIRED_EVENT_ID("VAL005", "eventId is required", GeneralMessage.HTTP_400),
    VAL_REQUIRED_EVENT_NAME("VAL006", "name is required", GeneralMessage.HTTP_400),
    VAL_REQUIRED_EVENT_DATE("VAL007", "eventDate is required", GeneralMessage.HTTP_400),
    VAL_REQUIRED_EVENT_VENUE("VAL008", "venue is required", GeneralMessage.HTTP_400),
    VAL_REQUIRED_TOTAL_CAPACITY("VAL009", "totalCapacity is required", GeneralMessage.HTTP_400),
    VAL_REQUIRED_TICKET_PRICE("VAL010", "ticketPrice is required", GeneralMessage.HTTP_400),
    VAL_REQUIRED_PAYMENT_REFERENCE("VAL011", "paymentReference is required", GeneralMessage.HTTP_400),
    VAL_REQUIRED_RECIPIENT("VAL012", "recipient is required", GeneralMessage.HTTP_400),
    VAL_REQUIRED_REASON("VAL013", "reason is required", GeneralMessage.HTTP_400),
    VAL_INVALID_EVENT_DATE("VAL014", "eventDate must be a valid ISO-8601 instant", GeneralMessage.HTTP_400),
    VAL_INVALID_TEXT_LENGTH("VAL015", "text length is invalid", GeneralMessage.HTTP_400),
    VAL_INVALID_SEAT_SELECTION("VAL016", "Select between 1 and 10 distinct seat IDs", GeneralMessage.HTTP_400),

    // Security Errors (SECxxx)
    SEC_UNAUTHORIZED("SEC001", "Authentication header missing or invalid", GeneralMessage.HTTP_401),
    SEC_FORBIDDEN("SEC002", "Insufficient permissions for this operation", GeneralMessage.HTTP_403),

    // Business Errors (BUSxxx)
    BUS_EVENT_NOT_FOUND("BUS001", "Event not found or not active", GeneralMessage.HTTP_404),
    BUS_INSUFFICIENT_INVENTORY("BUS002", "Insufficient tickets available", GeneralMessage.HTTP_409),
    BUS_ORDER_EXPIRED("BUS003", "Order reservation has expired", GeneralMessage.HTTP_422),
    BUS_INVALID_STATE_TRANSITION("BUS004", "Illegal ticket status transition", GeneralMessage.HTTP_409),
    BUS_ORDER_NOT_FOUND("BUS005", "Order not found or unauthorized", GeneralMessage.HTTP_404),
    BUS_PAYMENT_REJECTED("BUS006", "Payment processing failed", GeneralMessage.HTTP_422),
    BUS_CONCURRENCY_CONFLICT("BUS007", "High concurrency conflict, please retry", GeneralMessage.HTTP_409),
    BUS_SEAT_UNAVAILABLE("BUS008", "One or more selected seats are unavailable", GeneralMessage.HTTP_409),

    // Technical Errors (ERRxxx)
    TECH_DYNAMO_ERROR("ERR001", "Database operation failed", GeneralMessage.HTTP_500),
    TECH_SQS_ERROR("ERR002", "Failed to dispatch message to SQS queue", GeneralMessage.HTTP_500),
    TECH_CIRCUIT_OPEN("ERR003", "Service temporarily unavailable due to circuit breaker", GeneralMessage.HTTP_503),
    TECH_INTERNAL_ERROR("ERR999", "An unexpected technical error occurred", GeneralMessage.HTTP_500);

    static final int HTTP_200 = 200;
    static final int HTTP_201 = 201;
    static final int HTTP_202 = 202;
    static final int HTTP_400 = 400;
    static final int HTTP_401 = 401;
    static final int HTTP_403 = 403;
    static final int HTTP_404 = 404;
    static final int HTTP_409 = 409;
    static final int HTTP_422 = 422;
    static final int HTTP_500 = 500;
    static final int HTTP_503 = 503;

    private final String code;
    private final String description;
    private final int httpStatus;

    private static final Map<String, GeneralMessage> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(GeneralMessage::getCode, gm -> gm, (a, b) -> a));

    public static GeneralMessage findByCode(String code) {
        return Objects.isNull(code) ? TECH_INTERNAL_ERROR : BY_CODE.getOrDefault(code, TECH_INTERNAL_ERROR);
    }
}
