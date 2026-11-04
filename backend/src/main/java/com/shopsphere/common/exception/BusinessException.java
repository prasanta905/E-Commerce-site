package com.shopsphere.common.exception;

import com.shopsphere.common.ApiError;

/**
 * Base for all business exceptions — carries the safe message + the error CODE
 * the frontend switches on.
 */
public class BusinessException extends RuntimeException {
    private final int status;
    private final String code;

    public BusinessException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ApiError toError(String path) {
        return ApiError.of(status, code, getMessage(), path);
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }

    // ---- common factories -------------------------------------------------
    public static BusinessException notFound(String resource) {
        return new BusinessException(404, "NOT_FOUND", resource + " not found");
    }
    public static BusinessException conflict(String code, String message) {
        return new BusinessException(409, code, message);
    }
    public static BadRequest badRequest(String code, String message) {
        return new BadRequest(code, message);
    }
    public static Forbidden forbidden(String code, String msg) {
        return new Forbidden(code, msg);
    }
}
