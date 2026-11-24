package com.jornal.requests;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Línea del desglose en unidad menor de la moneda. {@code kind}: SERVICE, TRAVEL_FEE, TAX o TIP.
 * Un impuesto incluido en el precio tiene {@code includedInTotal = false}: se informa pero no se suma.
 */
@Embeddable
public record RequestLine(
        @Column(name = "kind", nullable = false) String kind,
        @Column(name = "description", nullable = false) String description,
        @Column(name = "quantity", nullable = false) int quantity,
        @Column(name = "unit_amount_minor", nullable = false) long unitAmountMinor,
        @Column(name = "amount_minor", nullable = false) long amountMinor,
        @Column(name = "included_in_total", nullable = false) boolean includedInTotal) {

    public static final String SERVICE = "SERVICE";
    public static final String TRAVEL_FEE = "TRAVEL_FEE";
    public static final String TAX = "TAX";
    public static final String TIP = "TIP";
}
