package com.shopsphere.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Current profile fields the user may edit. Email changes are not allowed here. */
public record ProfileRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 20) String phone
) {}
