package com.shopsphere.user;

public record AddressResponse(
        Long id,
        String contactName,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String pincode,
        boolean isDefault
) {}
