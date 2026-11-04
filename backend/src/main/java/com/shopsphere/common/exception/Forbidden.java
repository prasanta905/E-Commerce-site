package com.shopsphere.common.exception;

public class Forbidden extends BusinessException {
    public Forbidden(String code, String message) {
        super(403, code, message);
    }
}
