package com.shopsphere.order.entity;

/**
 * How the buyer pays.
 *
 * RAZORPAY is verified by Razorpay's checkout handshake/webhook. UPI is the
 * self-hosted YouPI PG flow: the browser is redirected to a hosted UPI
 * checkout and the order is fulfilled only by a signed RECONCILED webhook.
 */
public enum PaymentMethod {
    COD, RAZORPAY, UPI
}
