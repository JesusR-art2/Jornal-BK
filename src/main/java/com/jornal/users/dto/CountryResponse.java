package com.jornal.users.dto;

import java.util.List;

public record CountryResponse(String code, String name, String currency,
                              String defaultLocale, String phonePrefix, List<String> documentTypes) {
}
