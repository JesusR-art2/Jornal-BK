package com.jornal.users.dto;

import java.util.Map;
import java.util.UUID;

/** {@code fields} viene en el orden del esquema del país; {@code formatted} es la dirección en una línea. */
public record AddressResponse(UUID id, String label, String countryCode,
                              Map<String, String> fields, String formatted) {
}
