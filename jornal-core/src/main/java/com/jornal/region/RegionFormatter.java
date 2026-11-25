package com.jornal.region;

import com.jornal.shared.Locales;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

/**
 * Formatos del país con CLDR (los datos de locale del JDK) y las anulaciones del perfil regional:
 * decimales de la moneda ({@code moneyScale}), patrón de fecha y ciclo de hora. Es lo mismo que hace el
 * frontend con {@code Intl}; la prueba de localización lo usa para comprobar que CLDR coincide con el perfil.
 */
@Component
public class RegionFormatter {

    /** Separadores que da CLDR para el idioma del perfil. */
    public record Separators(String grouping, String decimal) {
        /** {@code true} si coinciden con los esperados del perfil ({@code expectedGrouping} y {@code expectedDecimal}). */
        public boolean match(RegionProfileView profile) {
            return grouping.equals(profile.expectedGrouping()) && decimal.equals(profile.expectedDecimal());
        }
    }

    public Locale locale(RegionProfileView profile) {
        return Locales.toLocale(profile.defaultLocale());
    }

    /** Monto en unidad menor con el símbolo y los separadores del país, por ejemplo {@code 1.234,56 €}. */
    public String money(RegionProfileView profile, long minor) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale(profile));
        format.setCurrency(Currency.getInstance(profile.currency()));
        format.setMinimumFractionDigits(profile.moneyScale());
        format.setMaximumFractionDigits(profile.moneyScale());
        return format.format(BigDecimal.valueOf(minor, profile.moneyScale()));
    }

    /** Fecha y hora en la zona indicada con el patrón de fecha y el ciclo de hora del perfil. */
    public String dateTime(RegionProfileView profile, Instant at, ZoneId zone) {
        String time = "h12".equals(profile.hourCycle()) ? "h:mm a" : "HH:mm";
        return DateTimeFormatter.ofPattern(profile.datePattern() + " " + time, locale(profile))
                .format(at.atZone(zone));
    }

    public Separators separators(RegionProfileView profile) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale(profile));
        return new Separators(String.valueOf(symbols.getGroupingSeparator()),
                String.valueOf(symbols.getDecimalSeparator()));
    }
}
