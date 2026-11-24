package com.jornal.region.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Números de prueba públicos o calculados a mano; no corresponden a personas reales. */
class DocumentValidatorsTest {

    final DocumentValidators validators = new DocumentValidators();

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            // NIT con dígito de verificación (ejemplo público: 800197268-4)
            "nit-dv, 800197268-4, 8001972684",
            "nit-dv, 800.197.268-4, 8001972684",
            // CPF y CNPJ de ejemplo usados en documentación técnica
            "cpf, 529.982.247-25, 52998224725",
            "cnpj, 11.222.333/0001-81, 11222333000181",
            // Luhn: número clásico de ejemplo
            "luhn, 79927398713, 79927398713",
            // Japón: My Number y número corporativo con dígito de control calculado
            "jp-mynumber, 123456789018, 123456789018",
            "jp-corporate, 8700110005901, 8700110005901",
    })
    void acceptsValidNumbers(String validator, String number, String normalized) {
        assertThat(validators.validate(validator, null, number)).isEqualTo(normalized);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "nit-dv, 800197268-5",
            "cpf, 529.982.247-26",
            "cpf, 111.111.111-11",
            "cnpj, 11.222.333/0001-82",
            "luhn, 79927398710",
            "jp-mynumber, 123456789010",
            "jp-corporate, 1700110005901",
            "cpf, ABC",
    })
    void rejectsInvalidNumbers(String validator, String number) {
        assertThat(validators.validate(validator, null, number)).isNull();
    }

    @Test
    void regexValidatorUsesTheProfileRegex() {
        assertThat(validators.validate("regex", "^\\d{6,10}$", " 1020304050 ")).isEqualTo("1020304050");
        assertThat(validators.validate("regex", "^\\d{6,10}$", "12AB")).isNull();
    }

    @Test
    void checksumValidatorAlsoAppliesTheRegex() {
        // SIRET: Luhn y 14 dígitos
        assertThat(validators.validate("luhn", "^\\d{14}$", "79927398713")).isNull();
    }

    @Test
    void unknownValidatorRejects() {
        assertThat(validators.exists("nope")).isFalse();
        assertThat(validators.validate("nope", null, "123")).isNull();
    }
}
