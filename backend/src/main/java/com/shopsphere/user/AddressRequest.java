package com.shopsphere.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 120) String contactName,
        @Size(max = 20) String phone,
        @NotBlank @Size(max = 255) String line1,
        @Size(max = 255) String line2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Size(max = 10) String pincode,
        boolean isDefault
) {}
