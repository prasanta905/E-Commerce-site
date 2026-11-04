package com.shopsphere.common.exception;

public class BadRequest extends BusinessException {
    public BadRequest(String code, String message) {
        super(400, code, message);
    }
}
