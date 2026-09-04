package com.apparel.tracking.common.exception;

/**
 * Thrown when a domain invariant is violated — a second active MAIN cut for a
 * model, a stage move that breaks piece-count reconciliation, an over-allocated
 * roll. Rendered as HTTP 422.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
