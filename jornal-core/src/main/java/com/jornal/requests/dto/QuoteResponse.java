package com.jornal.requests.dto;

import com.jornal.requests.RequestLine;

import java.util.List;
import java.util.UUID;

/**
 * Desglose en unidad menor ({@code moneyScale} decimales). {@code taxIncluded}: el impuesto ya está dentro
 * del precio y se informa sin sumarlo. {@code tipMode} y {@code tipSuggestedPercents} vienen del perfil.
 */
public record QuoteResponse(UUID categoryId, String categoryName, String unit, int units,
                            List<RequestLine> lines, long subtotalMinor, long travelFeeMinor, long taxMinor,
                            boolean taxIncluded, long tipMinor, long totalMinor, String currency, int moneyScale,
                            String tipMode, List<Integer> tipSuggestedPercents) {
}
