package com.jornal.shared;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Idiomas que soporta la plataforma. El respaldo final es en-US.
 * <ul>
 *   <li>{@link #SUPPORTED}: idiomas con todos los textos traducidos en el código (mensajes, plantillas).</li>
 *   <li>Además, cualquier idioma que declare un perfil regional ({@code supported_locales}) se registra al arrancar
 *       y al editar el perfil ({@link #register}). Así un país nuevo es solo datos: sus categorías se traducen en la
 *       base y los textos que falten salen del respaldo (en-US).</li>
 * </ul>
 */
public final class Locales {

    public static final String FALLBACK = "en-US";
    public static final List<String> SUPPORTED = List.of("es-CO", "pt-BR", "en-US", "fr-FR", "ja-JP");

    /** Idiomas que vienen de los perfiles regionales y no están en {@link #SUPPORTED}. */
    private static volatile List<String> fromProfiles = List.of();

    private Locales() {}

    /** Todos los idiomas aceptados: los de la plataforma y los de los perfiles regionales. */
    public static List<String> supported() {
        List<String> all = new ArrayList<>(SUPPORTED);
        all.addAll(fromProfiles);
        return all;
    }

    /** Registra los idiomas de los perfiles regionales (etiquetas BCP 47 bien formadas). */
    public static synchronized void register(Collection<String> locales) {
        LinkedHashSet<String> extra = new LinkedHashSet<>(fromProfiles);
        for (String tag : locales) {
            if (isWellFormed(tag) && !SUPPORTED.contains(tag)) extra.add(tag);
        }
        fromProfiles = List.copyOf(extra);
    }

    /** Etiqueta BCP 47 con idioma y país, por ejemplo {@code de-DE}. */
    public static boolean isWellFormed(String tag) {
        if (tag == null || tag.isBlank()) return false;
        Locale locale = Locale.forLanguageTag(tag);
        return !locale.getLanguage().isEmpty() && !locale.getCountry().isEmpty()
                && locale.toLanguageTag().equals(tag);
    }

    public static boolean isSupported(String locale) {
        return locale != null && supported().contains(locale);
    }

    /** Devuelve el locale si está soportado; si no, el respaldo indicado (o en-US). */
    public static String normalize(String locale, String fallback) {
        if (isSupported(locale)) return locale;
        return isSupported(fallback) ? fallback : FALLBACK;
    }

    /** Resuelve un header Accept-Language al primer idioma soportado, comparando por etiqueta y luego por idioma. */
    public static Optional<String> resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return Optional.empty();
        List<String> supported = supported();
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(acceptLanguage)) {
                String tag = range.getRange();
                if ("*".equals(tag)) continue;
                String lang = tag.split("-")[0].toLowerCase(Locale.ROOT);
                for (String s : supported) {
                    if (s.equalsIgnoreCase(tag)) return Optional.of(s);
                }
                for (String s : supported) {
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
