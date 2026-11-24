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
        assertThat(Locales.resolve("de-DE")).isEmpty();
        assertThat(Locales.resolve(null)).isEmpty();
        assertThat(Locales.resolve("@@@")).isEmpty();
    }

    @Test
    void normalizeFallsBackToProfileThenEnglish() {
        assertThat(Locales.normalize("ja-JP", "es-CO")).isEqualTo("ja-JP");
        assertThat(Locales.normalize("de-DE", "pt-BR")).isEqualTo("pt-BR");
        assertThat(Locales.normalize(null, "xx-XX")).isEqualTo("en-US");
    }

    @Test
    void nameOrderComposesByStrategy() {
        assertThat(NameOrder.GIVEN_FAMILY.compose("Ana", "Pérez")).isEqualTo("Ana Pérez");
        assertThat(NameOrder.FAMILY_GIVEN.compose("太郎", "山田")).isEqualTo("山田 太郎");
        assertThat(NameOrder.FAMILY_GIVEN.compose("Taro", null)).isEqualTo("Taro");
    }
}
