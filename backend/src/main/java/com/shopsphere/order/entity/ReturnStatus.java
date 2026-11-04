package com.shopsphere.order.entity;

/** Lifecycle of a return: REQUESTED → APPROVED → PICKED → RETURNED (or REJECTED). */
public enum ReturnStatus {
    REQUESTED, APPROVED, REJECTED, PICKED, RETURNED
}
