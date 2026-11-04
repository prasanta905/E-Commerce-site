package com.shopsphere.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create/Update review payload. */
public record ReviewUpsertRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 120) String title,
        @Size(max = 5000) String body) {}
