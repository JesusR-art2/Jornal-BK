package com.jornal.region;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "region_profiles")
@Getter
@Setter
@NoArgsConstructor
public class RegionProfile {

    @Id
    @Column(length = 2)
    private String code;

    @Column(name = "default_locale", nullable = false)
    private String defaultLocale;

    /** Lista separada por comas. */
    @Column(name = "supported_locales", nullable = false)
    private String supportedLocales;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "money_scale", nullable = false)
    private int moneyScale;

    @Column(name = "default_timezone", nullable = false)
    private String defaultTimezone;

    /** Lista separada por comas. */
    @Column(name = "allowed_timezones", nullable = false)
    private String allowedTimezones;

    @Column(name = "expected_grouping", nullable = false)
    private String expectedGrouping;

    @Column(name = "expected_decimal", nullable = false)
    private String expectedDecimal;

    @Enumerated(EnumType.STRING)
    @Column(name = "symbol_position", nullable = false)
    private SymbolPosition symbolPosition;

    @Column(name = "date_pattern", nullable = false)
    private String datePattern;

    @Column(name = "hour_cycle", nullable = false)
    private String hourCycle;

    @Column(name = "first_day_of_week", nullable = false)
    private String firstDayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_system", nullable = false)
    private MeasurementSystem measurementSystem;

    @Column(name = "phone_prefix", nullable = false)
    private String phonePrefix;

    @Column(name = "postal_code_regex", nullable = false)
    private String postalCodeRegex;

    @Column(name = "postal_label_key", nullable = false)
    private String postalLabelKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "name_order", nullable = false)
    private NameOrder nameOrder;

    @Column(name = "prices_include_tax", nullable = false)
    private boolean pricesIncludeTax;

    @Column(name = "receipt_type", nullable = false)
    private String receiptType;

    @Column(name = "receipt_prefix", nullable = false)
    private String receiptPrefix;

    @Column(name = "data_protection_law", nullable = false)
    private String dataProtectionLaw;

    @Column(name = "terms_version", nullable = false)
    private String termsVersion;

    @Column(name = "privacy_version", nullable = false)
    private String privacyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tip_mode", nullable = false)
    private TipMode tipMode;

    /** Porcentajes sugeridos separados por comas; vacío si no hay. */
    @Column(name = "tip_suggested_pcts", nullable = false)
    private String tipSuggestedPcts = "";

    @Column(name = "commission_bps", nullable = false)
    private int commissionBps;

    @Column(name = "travel_fee_minor", nullable = false)
    private long travelFeeMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.VERIFICAR;

    private String source;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "region_address_fields", joinColumns = @JoinColumn(name = "region_code"))
    @OrderColumn(name = "position")
    private List<AddressField> addressFields = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "region_tax_rules", joinColumns = @JoinColumn(name = "region_code"))
    @OrderColumn(name = "position")
    private List<TaxRule> taxRules = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "region_payment_methods", joinColumns = @JoinColumn(name = "region_code"))
    @OrderColumn(name = "position")
    private List<PaymentMethod> paymentMethods = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "region_document_types", joinColumns = @JoinColumn(name = "region_code"))
    @OrderColumn(name = "position")
    private List<DocumentType> documentTypes = new ArrayList<>();
}
