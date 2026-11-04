package com.shopsphere.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable error contract. Never leaks stack traces or SQL.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,          // e.g. AUTH_INVALID_CREDENTIALS
        String message,       // safe human-readable message
        String path,
        List<FieldIssue> fieldErrors
) {
    public record FieldIssue(String field, String message) {}

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, null);
    }

    public static ApiError of(int status, String code, String message, String path,
                              List<FieldIssue> fieldErrors) {
        return new ApiError(Instant.now(), status, code, message, path, fieldErrors);
    }
}
