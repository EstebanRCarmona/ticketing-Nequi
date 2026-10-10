package co.com.nequi.ticketing.usecase.utils;

public final class UseCaseConstants {

    private UseCaseConstants() {}

    // ── Versioning ────────────────────────────────────────────────────────────
    public static final long INITIAL_VERSION = 1L;
    public static final long INITIAL_SEQUENCE = 1L;

    // ── Defaults ──────────────────────────────────────────────────────────────
    public static final String DEFAULT_CURRENCY = "COP";

    // ── Idempotency ───────────────────────────────────────────────────────────
    public static final long IDEMPOTENCY_TTL_SECONDS = 86400L;
    public static final String IDEMPOTENCY_SCOPE_ORDERS = "#POST_ORDERS#";
    public static final String IDEMPOTENCY_SCOPE_COMPLIMENTARY = "#POST_COMPLIMENTARY#";

    // ── Reservation ───────────────────────────────────────────────────────────
    public static final long RESERVATION_TTL_SECONDS = 600L;
    public static final long CONFIRM_TIMEOUT_SECONDS = 300L;
    public static final long SAFETY_MARGIN_SECONDS = 5L;

    // ── Sharding ──────────────────────────────────────────────────────────────
    public static final int EXPIRY_SHARD_COUNT = 4;
    public static final String SHARD_PREFIX = "S#";

    // ── Retry ─────────────────────────────────────────────────────────────────
    public static final int MAX_RETRIES = 3;
    public static final long RETRY_MIN_BACKOFF_MS = 50L;
    public static final long RETRY_MAX_BACKOFF_MS = 500L;
    public static final double RETRY_JITTER = 0.5;

    // ── Reason codes ──────────────────────────────────────────────────────────
    public static final String REASON_ORDER_CREATED = "ORDER_CREATED";
    public static final String REASON_COMPLIMENTARY_ISSUED = "COMPLIMENTARY_ISSUED";
    public static final String REASON_SYSTEM_EXPIRER = "SYSTEM_EXPIRER";

    // ── Payment ───────────────────────────────────────────────────────────────
    public static final String FAILURE_CODE_PAYMENT_REJECTED = "BUS006";
    public static final String FAILURE_DESC_PAYMENT_REJECTED = "Payment rejected by gateway";
}
