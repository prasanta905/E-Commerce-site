package com.shopsphere.review;

import java.time.Instant;

/** Public review projection — never leaks the reviewer's id or email. */
public record ReviewDto(
        Long id,
        Long productId,
        String reviewerName,
        Integer rating,
        String title,
        String body,
        Boolean verified,
        Instant createdAt
) {}
