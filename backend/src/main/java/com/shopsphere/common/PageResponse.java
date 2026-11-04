package com.shopsphere.common;

import java.util.List;

/**
 * Internally-generated paging metadata for the `meta` field.
 */
public record PageResponse(
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static PageResponse of(int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse(page, size, totalElements, totalPages);
    }

    public static PageResponse from(org.springframework.data.domain.Page<?> page) {
        return new PageResponse(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
