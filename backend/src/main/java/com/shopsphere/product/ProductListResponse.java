package com.shopsphere.product;

import java.util.List;

import com.shopsphere.common.PageResponse;

public record ProductListResponse(List<ProductCardDto> items, PageResponse meta) {}
