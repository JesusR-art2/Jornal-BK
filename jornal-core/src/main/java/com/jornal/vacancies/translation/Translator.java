package com.jornal.vacancies.translation;

import java.util.List;

/** Puerto hacia un servicio de traducción automática (LibreTranslate en Docker; reemplazable). */
public interface Translator {

    /** Traduce los textos del idioma {@code source} al {@code target} (códigos ISO 639-1), en el mismo orden. */
    List<String> translate(List<String> texts, String source, String target);

    /** Hay un servicio configurado. */
    boolean enabled();
}
