package com.jornal.requests.services;

import com.jornal.catalog.CategoryPriceSummary;
import com.jornal.region.RegionProfileView;
import com.jornal.region.TaxRule;
import com.jornal.region.TipMode;
import com.jornal.requests.RequestLine;
import com.jornal.requests.enums.ErrorRequest;
import com.jornal.shared.ApiException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Precio del pedido en unidad menor de la moneda, todo con enteros (D8). Solo usa datos del perfil regional:
 * <ul>
 *   <li>subtotal = precio de la categoría × unidades</li>
 *   <li>desplazamiento = {@code travelFeeMinor} del perfil</li>
 *   <li>impuesto sobre (subtotal + desplazamiento): si la regla está incluida en el precio se informa
 *       ({@code base × tasa / (1 + tasa)}) sin sumarse; si no, se suma ({@code base × tasa})</li>
 *   <li>propina según {@code tipMode}: HIDDEN no admite propina; no paga impuesto</li>
 * </ul>
 * Redondeo: medio hacia arriba a la unidad menor.
 */
@Service
public class PricingService {

    private static final BigDecimal BPS = BigDecimal.valueOf(10_000);

    public record Quote(List<RequestLine> lines, long subtotalMinor, long travelFeeMinor, long taxMinor,
                        boolean taxIncluded, long tipMinor, long totalMinor, String currency, int moneyScale) {
    }

    public Quote quote(RegionProfileView profile, CategoryPriceSummary price, int units,
                       Long tipMinor, Integer tipPercent) {
        List<RequestLine> lines = new ArrayList<>();

        long subtotal = Math.multiplyExact(price.amountMinor(), (long) units);
        lines.add(new RequestLine(RequestLine.SERVICE, price.name(), units, price.amountMinor(), subtotal, true));

        long travel = profile.travelFeeMinor();
        if (travel > 0) {
            lines.add(new RequestLine(RequestLine.TRAVEL_FEE, "travel_fee", 1, travel, travel, true));
        }

        long base = subtotal + travel;
        long taxAdded = 0;
        long taxTotal = 0;
        boolean allIncluded = true;
        for (TaxRule rule : profile.taxRules()) {
            BigDecimal rate = BigDecimal.valueOf(rule.rateBps());
            long amount = rule.included()
                    ? round(BigDecimal.valueOf(base).multiply(rate).divide(BPS.add(rate), 6, RoundingMode.HALF_UP))
                    : round(BigDecimal.valueOf(base).multiply(rate).divide(BPS, 6, RoundingMode.HALF_UP));
            lines.add(new RequestLine(RequestLine.TAX, rule.name(), 1, amount, amount, !rule.included()));
            taxTotal += amount;
            if (!rule.included()) {
                taxAdded += amount;
                allIncluded = false;
            }
        }

        long tip = tip(profile, subtotal, tipMinor, tipPercent);
        if (tip > 0) {
            lines.add(new RequestLine(RequestLine.TIP, "tip", 1, tip, tip, true));
        }

        long total = base + taxAdded + tip;
        return new Quote(List.copyOf(lines), subtotal, travel, taxTotal, allIncluded, tip, total,
                profile.currency(), profile.moneyScale());
    }

    private static long tip(RegionProfileView profile, long subtotal, Long tipMinor, Integer tipPercent) {
        long tip;
        if (tipMinor != null) {
            tip = tipMinor;
        } else if (tipPercent != null) {
            tip = round(BigDecimal.valueOf(subtotal).multiply(BigDecimal.valueOf(tipPercent))
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        } else {
            tip = 0;
        }
        if (tip > 0 && profile.tipMode() == TipMode.HIDDEN) {
            throw new ApiException(ErrorRequest.REQ005_TIP_NOT_ALLOWED);
        }
        return tip;
    }

    private static long round(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
