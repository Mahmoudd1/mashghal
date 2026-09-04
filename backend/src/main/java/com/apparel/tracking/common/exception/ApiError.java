package com.apparel.tracking.common.exception;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body for every failed API call.
 *
 * @param code      stable machine-readable code the UI can map to a translation key
 * @param message   human-readable fallback message (English)
 * @param details   per-field validation messages, empty for non-validation errors
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldError> details) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, List.of());
    }
}
