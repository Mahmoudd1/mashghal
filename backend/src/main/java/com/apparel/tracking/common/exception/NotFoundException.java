package com.apparel.tracking.common.exception;

/** Thrown when a referenced record does not exist. Rendered as HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException("%s %s was not found".formatted(entity, id));
    }
}
