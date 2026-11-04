package com.shopsphere.order.entity;

/**
 * Canonical order state machine. `previousStatus` validity is enforced
 * centrally in OrderService.transition() so no other code can advance state.
 */
public enum OrderStatus {
    PENDING,           // created by customer / awaiting payment (prepaid) or confirmation (COD)
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    RETURN_REQUESTED,
    RETURNED;

    /** Valid forward transitions for the FSM. */
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == PROCESSING || target == CANCELLED;
            case PROCESSING -> target == SHIPPED;
            case SHIPPED -> target == OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> target == DELIVERED;
            case DELIVERED -> target == RETURN_REQUESTED;
            case RETURN_REQUESTED -> target == RETURNED || target == DELIVERED; // reject returns
            case CANCELLED, RETURNED -> false;
        };
    }

    public boolean isCancellable() {
        return this == PENDING || this == CONFIRMED;
    }

    public boolean isReturnable() {
        return this == DELIVERED;
    }

    public boolean isActive() {
        return !isTerminal();
    }

    private boolean isTerminal() {
        return this == CANCELLED || this == RETURNED || this == DELIVERED;
    }
}
