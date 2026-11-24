package com.jornal.shared;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Idiomas que soporta la plataforma. El respaldo final es en-US. */
public final class Locales {

    public static final String FALLBACK = "en-US";
    public static final List<String> SUPPORTED = List.of("es-CO", "pt-BR", "en-US", "fr-FR", "ja-JP");

    private Locales() {}

    public static boolean isSupported(String locale) {
        return locale != null && SUPPORTED.contains(locale);
    }

    /** Devuelve el locale si está soportado; si no, el respaldo indicado (o en-US). */
    public static String normalize(String locale, String fallback) {
        if (isSupported(locale)) return locale;
        return isSupported(fallback) ? fallback : FALLBACK;
    }

    /** Resuelve un header Accept-Language al primer idioma soportado, comparando por etiqueta y luego por idioma. */
    public static Optional<String> resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return Optional.empty();
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(acceptLanguage)) {
                String tag = range.getRange();
                if ("*".equals(tag)) continue;
                String lang = tag.split("-")[0].toLowerCase(Locale.ROOT);
                for (String s : SUPPORTED) {
                    if (s.equalsIgnoreCase(tag)) return Optional.of(s);
                }
                for (String s : SUPPORTED) {
                    if (s.toLowerCase(Locale.ROOT).startsWith(lang + "-")) return Optional.of(s);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // header mal formado: se usa el respaldo
        }
        return Optional.empty();
    }

    public static Locale toLocale(String tag) {
        return Locale.forLanguageTag(tag);
    }
}
