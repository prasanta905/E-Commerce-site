package com.shopsphere.common.exception;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.shopsphere.common.ApiError;

/**
 * Global error translation. Always emits ApiError never a stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException ex,
            org.springframework.web.context.request.WebRequest request) {
        String path = currentPath(request);
        return ResponseEntity.status(ex.getStatus()).body(ex.toError(path));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex,
            org.springframework.web.context.request.WebRequest request) {
        List<ApiError.FieldIssue> issues = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldIssue(f.getField(), f.getDefaultMessage()))
                .toList();
        ApiError body = ApiError.of(400, "VALIDATION_FAILED", "Request validation failed",
                currentPath(request), issues);
        return ResponseEntity.badRequest().body(body);
    }

    /** RBAC denial — @PreAuthorize rethrows AccessDeniedException which the
     * generic handler would otherwise turn into a misleading 500. */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> accessDenied(
            org.springframework.security.access.AccessDeniedException ex,
            org.springframework.web.context.request.WebRequest request) {
        ApiError body = ApiError.of(403, "FORBIDDEN",
                "You do not have permission to perform this action", currentPath(request));
        return ResponseEntity.status(403).body(body);
    }

    /** Authentication failures surfaced by the method-security layer. */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiError> unauthenticated(
            org.springframework.security.core.AuthenticationException ex,
            org.springframework.web.context.request.WebRequest request) {
        ApiError body = ApiError.of(401, "AUTH_REQUIRED", "Authentication required",
                currentPath(request));
        return ResponseEntity.status(401).body(body);
    }

    /** Generic catch-all — never leaks internals to the customer. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unhandled(Exception ex,
            org.springframework.web.context.request.WebRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Unhandled error at {}: {}", servletRequest.getRequestURI(), ex.getMessage(), ex);
        ApiError body = ApiError.of(500, "INTERNAL_ERROR",
                "Something went wrong. Our team has been notified.", currentPath(request));
        return ResponseEntity.internalServerError().body(body);
    }

    private String currentPath(org.springframework.web.context.request.WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
