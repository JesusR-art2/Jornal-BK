package com.jornal.requests.services;

import com.jornal.catalog.CategoryPriceSummary;
import com.jornal.region.*;
import com.jornal.requests.RequestLine;
import com.jornal.shared.ApiException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pedido equivalente de la prueba de localización (D4): 2 jornadas de bodega + desplazamiento + propina,
 * con los valores de referencia de V10/V11 (marcados VERIFICAR).
 */
class PricingServiceTest {

    final PricingService pricing = new PricingService();

    @Test
    void colombiaTaxIncludedAndOptionalTip() {
        var q = pricing.quote(profile("COP", 0, 8000, TipMode.OPTIONAL, tax("IVA", 1900, true)),
                price(85000, "COP"), 2, null, 10);
        assertThat(q.subtotalMinor()).isEqualTo(170000);
        assertThat(q.travelFeeMinor()).isEqualTo(8000);
        assertThat(q.taxMinor()).isEqualTo(28420);          // 178000 × 19/119, incluido
        assertThat(q.taxIncluded()).isTrue();
        assertThat(q.tipMinor()).isEqualTo(17000);          // 10 % del subtotal
        assertThat(q.totalMinor()).isEqualTo(195000);       // el IVA no se suma
        assertThat(q.lines()).extracting(RequestLine::kind)
                .containsExactly(RequestLine.SERVICE, RequestLine.TRAVEL_FEE, RequestLine.TAX, RequestLine.TIP);
    }

    @Test
    void unitedStatesTaxAddedAndPromptedTip() {
        var q = pricing.quote(profile("USD", 2, 1000, TipMode.PROMPT, tax("Sales tax (NY State)", 400, false)),
                price(15000, "USD"), 2, null, 18);
        assertThat(q.subtotalMinor()).isEqualTo(30000);     // $300.00
        assertThat(q.taxMinor()).isEqualTo(1240);           // 4 % de $310.00
        assertThat(q.taxIncluded()).isFalse();
        assertThat(q.tipMinor()).isEqualTo(5400);
        assertThat(q.totalMinor()).isEqualTo(37640);        // $376.40
        assertThat(q.moneyScale()).isEqualTo(2);
    }

    @Test
    void japanHidesTip() {
        var profile = profile("JPY", 0, 1000, TipMode.HIDDEN, tax("消費税", 1000, true));
        var q = pricing.quote(profile, price(14000, "JPY"), 2, null, null);
        assertThat(q.taxMinor()).isEqualTo(2636);           // 29000 × 10/110
        assertThat(q.tipMinor()).isZero();
        assertThat(q.totalMinor()).isEqualTo(29000);
        assertThatThrownBy(() -> pricing.quote(profile, price(14000, "JPY"), 2, 500L, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void franceAndBrazilIncludeTax() {
        var fr = pricing.quote(profile("EUR", 2, 800, TipMode.OPTIONAL, tax("TVA", 2000, true)),
                price(13000, "EUR"), 2, null, 5);
        assertThat(fr.taxMinor()).isEqualTo(4467);          // 268.00 × 20/120 = 44.666...
        assertThat(fr.totalMinor()).isEqualTo(28100);       // 26800 + 1300 de propina

        var br = pricing.quote(profile("BRL", 2, 1000, TipMode.OPTIONAL, tax("ISS", 500, true)),
                price(17000, "BRL"), 2, 3400L, null);
        assertThat(br.taxMinor()).isEqualTo(1667);          // 350.00 × 5/105
        assertThat(br.totalMinor()).isEqualTo(38400);
    }

    private static TaxRule tax(String name, int bps, boolean included) {
        return new TaxRule(name, bps, included, "test", LocalDate.of(2020, 1, 1), ReviewStatus.VERIFICAR);
    }

    private static CategoryPriceSummary price(long amount, String currency) {
        return new CategoryPriceSummary(UUID.randomUUID(), "warehouse", "Warehouse", "DAY", amount, currency);
    }

    private static RegionProfileView profile(String currency, int scale, long travelFee, TipMode tipMode, TaxRule tax) {
        return new RegionProfileView("XX", "en-US", List.of("en-US"), currency, scale, "UTC", List.of("UTC"),
                ",", ".", SymbolPosition.BEFORE, "dd/MM/yyyy", "h23", DayOfWeek.MONDAY, MeasurementSystem.METRIC,
                "+1", "^\\d+$", "postal", List.of(), NameOrder.GIVEN_FAMILY, List.of(tax), tax.included(),
                "RECEIPT", "R", List.of(), "law", "v1", "v1", List.of(), tipMode, List.of(10), 1000, travelFee,
                ReviewStatus.VERIFICAR, "test", 0, Instant.EPOCH);
    }
}
