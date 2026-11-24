package com.jornal.region;

import java.util.Map;
import java.util.Optional;

/** Contrato público del módulo region. */
public interface RegionApi {

    /** Código del país que atiende esta instancia ({@code APP_REGION}). */
    String currentCode();

    /** Perfil del país de esta instancia. */
    RegionProfileView current();

    Optional<RegionProfileView> find(String code);

    /**
     * Valida una dirección contra el esquema del país y la devuelve normalizada (recortada y en el orden del esquema).
     * Lanza {@code REG002_INVALID_ADDRESS} con el detalle por campo.
     */
    Map<String, String> validateAddress(String regionCode, Map<String, String> fields);

    /** Une las partes de la dirección en una línea, en el orden del esquema del país. */
    String formatAddress(String regionCode, Map<String, String> fields);

    /**
     * Valida un documento del tipo y clase indicados y devuelve el número normalizado.
     * Lanza {@code REG003_DOCUMENT_TYPE_NOT_ALLOWED} o {@code REG004_INVALID_DOCUMENT}.
     */
    String validateDocument(String regionCode, String type, String number, DocumentKind kind);
}
