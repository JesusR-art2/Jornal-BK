package com.jornal.catalog.dto;

import java.util.UUID;

/**
 * Categoría para el cliente: nombre y descripción en su idioma, y precio de la región
 * en unidad menor ({@code moneyScale} indica cuántos decimales tiene la moneda). {@code price} es null si no hay precio.
 */
public record CategoryView(UUID id, String slug, String name, String description, String locale, Price price) {

    public record Price(String unit, long amountMinor, String currency, int moneyScale) {
    }
}
