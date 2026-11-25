package com.jornal.region.services;

import com.jornal.region.*;
import com.jornal.region.dto.HolidayRequest;
import com.jornal.region.dto.HolidayResponse;
import com.jornal.region.dto.RegionProfileRequest;
import com.jornal.region.enums.ErrorRegion;
import com.jornal.shared.ApiException;
import com.jornal.shared.Locales;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionService implements RegionApi {

    private static final String POSTAL_CODE_FIELD = "postal_code";

    private final RegionProfileRepository profiles;
    private final RegionHolidayRepository holidays;
    private final RegionProperties properties;
    private final DocumentValidators validators;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Perfil de esta instancia en memoria; se invalida cuando el administrador lo cambia. */
    private final AtomicReference<RegionProfileView> currentCache = new AtomicReference<>();

    @Override
    public String currentCode() {
        return properties.code();
    }

    /**
     * Registra en {@link Locales} los idiomas que declaran los perfiles regionales. Con esto, un país nuevo es
     * solo datos: su idioma se acepta aunque no tenga textos traducidos en el código (salen del respaldo en-US).
     */
    @Transactional(readOnly = true)
    public void registerProfileLocales() {
        Locales.register(profiles.findAll().stream()
                .flatMap(p -> toView(p).supportedLocales().stream())
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RegionProfileView current() {
        RegionProfileView cached = currentCache.get();
        if (cached != null) return cached;
        RegionProfileView loaded = load(properties.code())
                .orElseThrow(() -> new ApiException(ErrorRegion.REG001_PROFILE_NOT_FOUND, properties.code()));
        currentCache.set(loaded);
        return loaded;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RegionProfileView> find(String code) {
        if (code == null) return Optional.empty();
        if (code.equalsIgnoreCase(properties.code())) return Optional.of(current());
        return load(code.toUpperCase(Locale.ROOT));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> validateAddress(String regionCode, Map<String, String> fields) {
        RegionProfileView profile = require(regionCode);
        Map<String, String> input = fields == null ? Map.of() : fields;
        Set<String> known = profile.addressSchema().stream().map(AddressField::fieldKey).collect(Collectors.toSet());

        Map<String, String> errors = new LinkedHashMap<>();
        input.keySet().stream().filter(k -> !known.contains(k)).forEach(k -> errors.put(k, "UNKNOWN_FIELD"));

        Map<String, String> normalized = new LinkedHashMap<>();
        for (AddressField field : profile.addressSchema()) {
            String raw = input.get(field.fieldKey());
            String value = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
            if (value.isEmpty()) {
                if (field.required()) errors.put(field.fieldKey(), "REQUIRED");
                continue;
            }
            if (value.length() > field.maxLength()) {
                errors.put(field.fieldKey(), "TOO_LONG");
                continue;
            }
            String regex = field.regex() != null ? field.regex()
                    : POSTAL_CODE_FIELD.equals(field.fieldKey()) ? profile.postalCodeRegex() : null;
            if (regex != null && !Pattern.matches(regex, value)) {
                errors.put(field.fieldKey(), "INVALID_FORMAT");
                continue;
            }
            normalized.put(field.fieldKey(), value);
        }
        if (!errors.isEmpty()) {
            throw ApiException.withFields(ErrorRegion.REG002_INVALID_ADDRESS, errors);
        }
        return normalized;
    }

    @Override
    @Transactional(readOnly = true)
    public String formatAddress(String regionCode, Map<String, String> fields) {
        RegionProfileView profile = require(regionCode);
        return profile.addressSchema().stream()
                .map(f -> fields.get(f.fieldKey()))
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.joining(", "));
    }

    @Override
    @Transactional(readOnly = true)
    public String validateDocument(String regionCode, String type, String number, DocumentKind kind) {
        RegionProfileView profile = require(regionCode);
        DocumentType documentType = profile.documentTypes().stream()
                .filter(d -> d.code().equalsIgnoreCase(type) && d.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorRegion.REG003_DOCUMENT_TYPE_NOT_ALLOWED, type));
        String normalized = validators.validate(documentType.validator(), documentType.regex(), number);
        if (normalized == null) {
            throw new ApiException(ErrorRegion.REG004_INVALID_DOCUMENT);
        }
        return normalized;
    }

    // ------------------------------------------------------------------ administración

    @Transactional
    public RegionProfileView update(RegionProfileRequest request) {
        RegionProfile profile = profiles.findById(properties.code())
                .orElseThrow(() -> new ApiException(ErrorRegion.REG001_PROFILE_NOT_FOUND, properties.code()));
        if (profile.getVersion() != request.version()) {
            throw new ApiException(ErrorRegion.REG005_VERSION_CONFLICT);
        }
        checkConsistency(request);
        apply(request, profile);
        profile.setUpdatedAt(clock.instant());
        RegionProfile saved = profiles.saveAndFlush(profile);

        currentCache.set(null);
        RegionProfileView view = toView(saved);
        Locales.register(view.supportedLocales());
        events.publishEvent(new RegionProfileUpdated(view.code(), view.version()));
        return view;
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> holidays(int year) {
        return holidays.findByRegionCodeAndHolidayDateBetweenOrderByHolidayDate(
                        properties.code(), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream()
                .map(h -> new HolidayResponse(h.getHolidayDate(), h.getName(), h.getReviewStatus()))
                .toList();
    }

    @Transactional
    public HolidayResponse upsertHoliday(LocalDate date, HolidayRequest request) {
        RegionHoliday holiday = holidays.findByRegionCodeAndHolidayDate(properties.code(), date)
                .orElseGet(() -> {
                    RegionHoliday created = new RegionHoliday();
                    created.setRegionCode(properties.code());
                    created.setHolidayDate(date);
                    return created;
                });
        holiday.setName(request.name().trim());
        holiday.setReviewStatus(request.reviewStatus() == null ? ReviewStatus.VERIFICAR : request.reviewStatus());
        RegionHoliday saved = holidays.save(holiday);
        return new HolidayResponse(saved.getHolidayDate(), saved.getName(), saved.getReviewStatus());
    }

    @Transactional
    public void deleteHoliday(LocalDate date) {
        RegionHoliday holiday = holidays.findByRegionCodeAndHolidayDate(properties.code(), date)
                .orElseThrow(() -> new ApiException(ErrorRegion.REG007_HOLIDAY_NOT_FOUND, date));
        holidays.delete(holiday);
    }

    // ------------------------------------------------------------------ internos

    private RegionProfileView require(String code) {
        return find(code).orElseThrow(() -> new ApiException(ErrorRegion.REG001_PROFILE_NOT_FOUND, code));
    }

    private Optional<RegionProfileView> load(String code) {
        return profiles.findById(code).map(RegionService::toView);
    }

    private void checkConsistency(RegionProfileRequest r) {
        r.supportedLocales().forEach(l -> require(Locales.isWellFormed(l), "invalid locale " + l));
        require(r.supportedLocales().contains(r.defaultLocale()), "defaultLocale must be in supportedLocales");
        r.allowedTimezones().forEach(this::requireZone);
        require(r.allowedTimezones().contains(r.defaultTimezone()), "defaultTimezone must be in allowedTimezones");
        try {
            Currency.getInstance(r.currency());
        } catch (IllegalArgumentException e) {
            invalid("unknown currency " + r.currency());
        }
        requireRegex(r.postalCodeRegex());
        r.addressSchema().forEach(f -> { if (f.regex() != null) requireRegex(f.regex()); });
        r.documentTypes().forEach(d -> {
            require(validators.exists(d.validator()), "unknown validator " + d.validator());
            if (d.regex() != null) requireRegex(d.regex());
        });
        requireUnique(r.addressSchema().stream().map(RegionProfileRequest.AddressFieldRequest::fieldKey).toList(), "address field");
        requireUnique(r.documentTypes().stream().map(d -> d.code() + "/" + d.kind()).toList(), "document type");
        requireUnique(r.paymentMethods().stream().map(RegionProfileRequest.PaymentMethodRequest::code).toList(), "payment method");
    }

    private void requireZone(String zone) {
        try {
            ZoneId.of(zone);
        } catch (DateTimeException e) {
            invalid("unknown timezone " + zone);
        }
    }

    private void requireRegex(String regex) {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            invalid("invalid regex " + regex);
        }
    }

    private void requireUnique(List<String> values, String what) {
        require(new HashSet<>(values).size() == values.size(), "duplicated " + what);
    }

    private void require(boolean condition, String message) {
        if (!condition) invalid(message);
    }

    private void invalid(String message) {
        throw new ApiException(ErrorRegion.REG006_INVALID_PROFILE, message);
    }

    private static void apply(RegionProfileRequest r, RegionProfile p) {
        p.setDefaultLocale(r.defaultLocale());
        p.setSupportedLocales(String.join(",", r.supportedLocales()));
        p.setCurrency(r.currency());
        p.setMoneyScale(r.moneyScale());
        p.setDefaultTimezone(r.defaultTimezone());
        p.setAllowedTimezones(String.join(",", r.allowedTimezones()));
        p.setExpectedGrouping(r.expectedGrouping());
        p.setExpectedDecimal(r.expectedDecimal());
        p.setSymbolPosition(r.symbolPosition());
        p.setDatePattern(r.datePattern());
        p.setHourCycle(r.hourCycle());
        p.setFirstDayOfWeek(r.firstDayOfWeek().name());
        p.setMeasurementSystem(r.measurementSystem());
        p.setPhonePrefix(r.phonePrefix());
        p.setPostalCodeRegex(r.postalCodeRegex());
        p.setPostalLabelKey(r.postalLabelKey());
        p.setNameOrder(r.nameOrder());
        p.setPricesIncludeTax(r.pricesIncludeTax());
        p.setReceiptType(r.receiptType());
        p.setReceiptPrefix(r.receiptPrefix());
        p.setDataProtectionLaw(r.dataProtectionLaw());
        p.setTermsVersion(r.termsVersion());
        p.setPrivacyVersion(r.privacyVersion());
        p.setTipMode(r.tipMode());
        p.setTipSuggestedPcts(r.tipSuggestedPercents().stream().map(String::valueOf).collect(Collectors.joining(",")));
        p.setCommissionBps(r.commissionBps());
        p.setTravelFeeMinor(r.travelFeeMinor());
        p.setReviewStatus(r.reviewStatus());
        p.setSource(r.source());

        p.getAddressFields().clear();
        r.addressSchema().forEach(f -> p.getAddressFields().add(
                new AddressField(f.fieldKey(), f.labelKey(), f.required(), blankToNull(f.regex()), f.maxLength())));
        p.getTaxRules().clear();
        r.taxRules().forEach(t -> p.getTaxRules().add(
                new TaxRule(t.name(), t.rateBps(), t.included(), t.source(), t.validFrom(), t.reviewStatus())));
        p.getDocumentTypes().clear();
        r.documentTypes().forEach(d -> p.getDocumentTypes().add(
                new DocumentType(d.code(), d.kind(), d.validator(), blankToNull(d.regex()))));
        p.getPaymentMethods().clear();
        r.paymentMethods().forEach(m -> p.getPaymentMethods().add(
                new PaymentMethod(m.code(), m.labelKey(), m.flow())));
    }

    static RegionProfileView toView(RegionProfile p) {
        return new RegionProfileView(
                p.getCode(),
                p.getDefaultLocale(),
                csv(p.getSupportedLocales()),
                p.getCurrency(),
                p.getMoneyScale(),
                p.getDefaultTimezone(),
                csv(p.getAllowedTimezones()),
                p.getExpectedGrouping(),
                p.getExpectedDecimal(),
                p.getSymbolPosition(),
                p.getDatePattern(),
                p.getHourCycle(),
                DayOfWeek.valueOf(p.getFirstDayOfWeek()),
                p.getMeasurementSystem(),
                p.getPhonePrefix(),
                p.getPostalCodeRegex(),
                p.getPostalLabelKey(),
                List.copyOf(p.getAddressFields()),
                p.getNameOrder(),
                List.copyOf(p.getTaxRules()),
                p.isPricesIncludeTax(),
                p.getReceiptType(),
                p.getReceiptPrefix(),
                List.copyOf(p.getDocumentTypes()),
                p.getDataProtectionLaw(),
                p.getTermsVersion(),
                p.getPrivacyVersion(),
                List.copyOf(p.getPaymentMethods()),
                p.getTipMode(),
                csv(p.getTipSuggestedPcts()).stream().map(Integer::valueOf).toList(),
                p.getCommissionBps(),
                p.getTravelFeeMinor(),
                p.getReviewStatus(),
                p.getSource(),
                p.getVersion(),
                p.getUpdatedAt());
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
