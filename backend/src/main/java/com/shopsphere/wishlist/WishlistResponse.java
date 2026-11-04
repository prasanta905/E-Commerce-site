package com.shopsphere.wishlist;

import java.util.List;

/** Mirrors product cards for the wishlist page — never raw entities. */
public record WishlistResponse(List<com.shopsphere.product.ProductCardDto> products) {}
