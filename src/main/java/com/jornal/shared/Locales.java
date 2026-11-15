package com.jornal.shared;

import java.util.Locale;
import java.util.Set;

public final class Locales {
    public static final String DEFAULT = "es-CO";
    public static final Set<String> SUPPORTED = Set.of("es-CO", "en-US");

    private Locales() {}

    public static String normalize(String locale) {
        return locale != null && SUPPORTED.contains(locale) ? locale : DEFAULT;
    }

    /** Resuelve el header Accept-Language a un locale soportado. */
    public static String resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return DEFAULT;
        try {
            for (Locale.LanguageRange r : Locale.LanguageRange.parse(acceptLanguage)) {
                String tag = r.getRange().toLowerCase();
                String lang = tag.split("-")[0];
                for (String s : SUPPORTED) {
                    if (s.equalsIgnoreCase(tag) || s.toLowerCase().startsWith(lang + "-")) return s;
                }
            }
        } catch (IllegalArgumentException ignored) { }
        return DEFAULT;
    }
}
