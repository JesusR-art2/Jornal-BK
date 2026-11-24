package com.jornal.region.dto;

import com.jornal.region.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.DayOfWeek;
import java.util.List;

/** Reemplazo completo del perfil regional. {@code version} es la versión que el administrador leyó. */
public record RegionProfileRequest(
        @NotNull Long version,
        @NotBlank String defaultLocale,
        @NotEmpty List<@NotBlank String> supportedLocales,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @Min(0) @Max(4) int moneyScale,
        @NotBlank String defaultTimezone,
        @NotEmpty List<@NotBlank String> allowedTimezones,
        @NotBlank @Size(max = 4) String expectedGrouping,
        @NotBlank @Size(max = 4) String expectedDecimal,
        @NotNull SymbolPosition symbolPosition,
        @NotBlank @Size(max = 20) String datePattern,
        @NotBlank @Pattern(regexp = "h12|h23") String hourCycle,
        @NotNull DayOfWeek firstDayOfWeek,
        @NotNull MeasurementSystem measurementSystem,
        @NotBlank @Pattern(regexp = "\\+[0-9]{1,4}") String phonePrefix,
        @NotBlank @Size(max = 100) String postalCodeRegex,
        @NotBlank @Size(max = 40) String postalLabelKey,
        @NotEmpty List<@Valid AddressFieldRequest> addressSchema,
        @NotNull NameOrder nameOrder,
        @NotNull List<@Valid TaxRuleRequest> taxRules,
        boolean pricesIncludeTax,
        @NotBlank @Size(max = 40) String receiptType,
        @NotBlank @Size(max = 10) String receiptPrefix,
        @NotEmpty List<@Valid DocumentTypeRequest> documentTypes,
        @NotBlank @Size(max = 120) String dataProtectionLaw,
        @NotBlank @Size(max = 20) String termsVersion,
        @NotBlank @Size(max = 20) String privacyVersion,
        @NotEmpty List<@Valid PaymentMethodRequest> paymentMethods,
        @NotNull TipMode tipMode,
        @NotNull List<@Min(0) @Max(100) Integer> tipSuggestedPercents,
        @Min(0) @Max(10000) int commissionBps,
        @PositiveOrZero long travelFeeMinor,
        @NotNull ReviewStatus reviewStatus,
        @Size(max = 255) String source) {

    public record AddressFieldRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,39}") String fieldKey,
            @NotBlank @Size(max = 60) String labelKey,
            boolean required,
            @Size(max = 200) String regex,
            @Min(1) @Max(200) int maxLength) {
    }

    public record TaxRuleRequest(
            @NotBlank @Size(max = 60) String name,
            @Min(0) @Max(10000) int rateBps,
            boolean included,
            @NotBlank @Size(max = 255) String source,
            @NotNull java.time.LocalDate validFrom,
            @NotNull ReviewStatus reviewStatus) {
    }

    public record DocumentTypeRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,29}") String code,
            @NotNull DocumentKind kind,
            @NotBlank String validator,
            @Size(max = 200) String regex) {
    }

    public record PaymentMethodRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,29}") String code,
            @NotBlank @Size(max = 60) String labelKey,
            @NotNull PaymentFlow flow) {
    }
}
