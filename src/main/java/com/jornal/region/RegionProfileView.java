package com.jornal.region;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;

/** Vista inmutable del perfil regional: es lo que ven los demás módulos y el endpoint público. */
public record RegionProfileView(
        String code,
        String defaultLocale,
        List<String> supportedLocales,
        String currency,
        int moneyScale,
        String defaultTimezone,
        List<String> allowedTimezones,
        String expectedGrouping,
        String expectedDecimal,
        SymbolPosition symbolPosition,
        String datePattern,
        String hourCycle,
        DayOfWeek firstDayOfWeek,
        MeasurementSystem measurementSystem,
        String phonePrefix,
        String postalCodeRegex,
        String postalLabelKey,
        List<AddressField> addressSchema,
        NameOrder nameOrder,
        List<TaxRule> taxRules,
        boolean pricesIncludeTax,
        String receiptType,
        String receiptPrefix,
        List<DocumentType> documentTypes,
        String dataProtectionLaw,
        String termsVersion,
        String privacyVersion,
        List<PaymentMethod> paymentMethods,
        TipMode tipMode,
        List<Integer> tipSuggestedPercents,
        int commissionBps,
        long travelFeeMinor,
        ReviewStatus reviewStatus,
        String source,
        long version,
        Instant updatedAt) {
}
