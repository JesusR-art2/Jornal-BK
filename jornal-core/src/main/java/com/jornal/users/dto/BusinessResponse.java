package com.jornal.users.dto;

public record BusinessResponse(String legalName, String taxIdType, String taxIdNumber,
                               AddressResponse billingAddress) {
}
