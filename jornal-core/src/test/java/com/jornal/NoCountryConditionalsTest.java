package com.jornal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regla del plan: ninguna lógica depende de un país concreto. Toda diferencia vive en el perfil regional.
 * Esta prueba falla si el código de producción menciona un código de país como literal.
 */
class NoCountryConditionalsTest {

    private static final Pattern COUNTRY_LITERAL =
            Pattern.compile("\"(CO|BR|US|FR|JP|IT|DE)\"|'(CO|BR|US|FR|JP|IT|DE)'");

    @Test
    void productionCodeHasNoCountryLiterals() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (COUNTRY_LITERAL.matcher(lines.get(i)).find()) {
                        offenders.add(file + ":" + (i + 1) + "  " + lines.get(i).trim());
                    }
                }
            }
        }
        assertThat(offenders).as("Country-specific literals in production code").isEmpty();
    }
}
