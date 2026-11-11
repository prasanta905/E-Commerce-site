package com.shopsphere.common.exception;

public class BadRequest extends BusinessException {
    public BadRequest(String code, String message) {
        super(400, code, message);
    }

    public static BadRequest of(String code, String message) {
        return new BadRequest(code, message);
    }
}
