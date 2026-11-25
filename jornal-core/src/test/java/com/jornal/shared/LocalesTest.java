package com.jornal.shared;

import com.jornal.region.NameOrder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalesTest {

    @Test
    void resolvesExactTagThenLanguage() {
        assertThat(Locales.resolve("fr-FR")).contains("fr-FR");
        assertThat(Locales.resolve("pt")).contains("pt-BR");
        assertThat(Locales.resolve("ja;q=0.9, en;q=0.5")).contains("ja-JP");
        assertThat(Locales.resolve("es-MX,es;q=0.9")).contains("es-CO");
    }

    @Test
    void unsupportedOrMissingHeaderIsEmpty() {
        assertThat(Locales.resolve("it-IT")).isEmpty();
        assertThat(Locales.resolve(null)).isEmpty();
        assertThat(Locales.resolve("@@@")).isEmpty();
    }

    @Test
    void normalizeFallsBackToProfileThenEnglish() {
        assertThat(Locales.normalize("ja-JP", "es-CO")).isEqualTo("ja-JP");
        assertThat(Locales.normalize("it-IT", "pt-BR")).isEqualTo("pt-BR");
        assertThat(Locales.normalize(null, "xx-XX")).isEqualTo("en-US");
    }

    @Test
    void localesOfRegionProfilesAreRegistered() {
        Locales.register(java.util.List.of("sv-SE", "no-valido", "es-CO"));
        assertThat(Locales.isSupported("sv-SE")).isTrue();
        assertThat(Locales.resolve("sv")).contains("sv-SE");
        assertThat(Locales.supported()).containsAll(Locales.SUPPORTED).contains("sv-SE").doesNotContain("no-valido");
        assertThat(Locales.isWellFormed("de-DE")).isTrue();
        assertThat(Locales.isWellFormed("de")).isFalse();          // falta el país
        assertThat(Locales.isWellFormed("de_DE")).isFalse();
    }

    @Test
    void nameOrderComposesByStrategy() {
        assertThat(NameOrder.GIVEN_FAMILY.compose("Ana", "Pérez")).isEqualTo("Ana Pérez");
        assertThat(NameOrder.FAMILY_GIVEN.compose("太郎", "山田")).isEqualTo("山田 太郎");
        assertThat(NameOrder.FAMILY_GIVEN.compose("Taro", null)).isEqualTo("Taro");
    }
}
