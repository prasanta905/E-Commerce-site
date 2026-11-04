package com.shopsphere.cart;

import java.util.List;

/** Derived fully server-side; prices never travel from the client. */
public record CartDto(List<CartItemDto> items, double subtotal, int itemCount) {}
