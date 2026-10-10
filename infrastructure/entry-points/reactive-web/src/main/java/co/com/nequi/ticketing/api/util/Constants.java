package co.com.nequi.ticketing.api.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String DEFAULT_USER_ID = "usr-default";
    public static final String DEFAULT_ROLE = "USER";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    // Circuit Breaker names
    public static final String CB_CREATE_EVENT = "createEventCB";
    public static final String CB_GET_EVENTS = "getEventsCB";
    public static final String CB_GET_EVENT_BY_ID = "getEventByIdCB";
    public static final String CB_CREATE_COMPLIMENTARY = "createComplimentaryCB";
    public static final String CB_CREATE_ORDER = "createOrderCB";
    public static final String CB_GET_ORDER = "getOrderCB";
    public static final String CB_CONFIRM_ORDER = "confirmOrderCB";
    public static final String CB_CANCEL_ORDER = "cancelOrderCB";
    public static final String CB_GET_ORDER_HISTORY = "getOrderHistoryCB";
    public static final String CB_FALLBACK = "fallback";
}
