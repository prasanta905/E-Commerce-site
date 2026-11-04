package com.shopsphere.inventory.entity;

/**
 * Allowed inventory movement types, stored as VARCHAR in inventory_transactions.
 */
public enum InventoryTransactionType {
    /** Initial/launcher stock entry. */
    PURCHASE,
    /** Manual admin adjustment (audit note required). */
    ADJUST,
    /** Checkout reservation held (positive delta recorded on inventory). */
    RESERVE,
    /** Reservation released — failed payment or expired session. */
    RELEASE,
    /** Reservation converted to permanent deduction at order confirmation. */
    COMMIT,
    /** Units restored to stock after a completed return. */
    RETURN
}
