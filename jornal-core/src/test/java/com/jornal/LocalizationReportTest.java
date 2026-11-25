package com.jornal;

import com.jornal.catalog.CategoryPriceSummary;
import com.jornal.region.*;
import com.jornal.requests.RequestLine;
import com.jornal.requests.services.PricingService;
import com.jornal.shared.Locales;
import com.jornal.users.services.PhoneNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba obligatoria de localización (sección 8 del enunciado, F11). El pedido de prueba D4 (2 jornadas de
 * bodega + desplazamiento + propina) en los 5 países, con el reloj fijo a las 20:30 locales del 15/11/2026.
 * Usa los perfiles reales de la base (migraciones), {@link PricingService}, los validadores de dirección y
 * teléfono y los formatos CLDR. Escribe el informe en {@code docs/evidencias/prueba-localizacion.{md,csv}}.
 *
 * <p>Las direcciones y teléfonos son ficticios. Se ejecuta con {@code scripts/prueba-localizacion.sh}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"jornal.region.code=CO", "jornal.test.context=localization-report"})
class LocalizationReportTest {

    static final LocalDateTime LOCAL_TIME = LocalDateTime.of(2026, 11, 15, 20, 30);
    static final int UNITS = 2;

    /** Datos de entrada por país y lo que se espera ver. */
    record Sample(String code, Map<String, String> address, String phone,
                  String expectedShown, String expectedUtc, long expectedTotal) {
    }

    static final List<Sample> SAMPLES = List.of(
            new Sample("CO", ordered("street_address", "Calle 26 # 68-35", "neighborhood", "Salitre",
                    "city", "Bogotá", "department", "Cundinamarca", "postal_code", "111321"),
                    "300 123 4567", "15/11/2026 8:30 p. m.", "2026-11-16T01:30:00Z", 195_000),
            new Sample("BR", ordered("street", "Rua Augusta", "number", "1500", "neighborhood", "Consolação",
                    "city", "São Paulo", "state", "SP", "postal_code", "01304-001"),
                    "(11) 91234-5678", "15/11/2026 20:30", "2026-11-15T23:30:00Z", 38_400),
            new Sample("US", ordered("line1", "350 5th Ave", "line2", "Suite 100", "city", "New York",
                    "state", "NY", "postal_code", "10118"),
                    "(212) 736-3100", "11/15/2026 8:30 PM", "2026-11-16T01:30:00Z", 36_740),
            new Sample("FR", ordered("street_address", "8 rue de Rivoli", "postal_code", "75004", "city", "Paris"),
                    "06 12 34 56 78", "15/11/2026 20:30", "2026-11-15T19:30:00Z", 28_100),
            new Sample("JP", ordered("postal_code", "100-0001", "prefecture", "東京都", "city", "千代田区",
                    "town", "千代田", "block", "1-1"),
                    "090-1234-5678", "2026/11/15 20:30", "2026-11-15T11:30:00Z", 29_000));

    @Autowired RegionApi regionApi;
    @Autowired RegionFormatter formatter;
    @Autowired RegionHolidayRepository holidays;
    @Autowired PricingService pricing;
    @Autowired PhoneNormalizer phones;
    @Autowired MessageSource messages;
    @Autowired JdbcTemplate jdbc;

    /** Una fila del informe: los 7 elementos de la sección 8 de un país. */
    record Row(String code, String locale, String category, String message,
               String subtotal, String travel, String tax, String tip, String total,
               String shown, String zone, String utc, String address, String phone,
               String taxAndReceipt, String paymentMethods, String tipPolicy,
               String separators, String holiday) {
    }

    @Test
    void d4OrderInTheFiveCountries() throws IOException {
        List<Row> rows = new ArrayList<>();
        for (Sample s : SAMPLES) {
            RegionProfileView profile = regionApi.find(s.code()).orElseThrow();
            Locale locale = formatter.locale(profile);
            ZoneId zone = ZoneId.of(profile.defaultTimezone());

            // Fecha y hora: el reloj fijo a las 20:30 locales; se guarda en UTC y se muestra en la zona del lugar
            Clock clock = Clock.fixed(LOCAL_TIME.atZone(zone).toInstant(), zone);
            Instant now = clock.instant();
            String shown = formatter.dateTime(profile, now, zone);
            assertThat(now).as("UTC %s", s.code()).hasToString(s.expectedUtc());
            assertThat(plainSpaces(shown)).as("hora mostrada %s", s.code()).isEqualTo(s.expectedShown());

            // Montos: precio de la base, PricingService y formato del país
            CategoryPriceSummary price = warehousePrice(s.code(), profile.defaultLocale());
            Integer tipPercent = profile.tipMode() == TipMode.HIDDEN || profile.tipSuggestedPercents().isEmpty()
                    ? null : profile.tipSuggestedPercents().getFirst();
            PricingService.Quote q = pricing.quote(profile, price, UNITS, null, tipPercent);
            assertThat(q.totalMinor()).as("total %s", s.code()).isEqualTo(s.expectedTotal());
            assertThat(q.currency()).isEqualTo(profile.currency());
            assertThat(q.lines()).extracting(RequestLine::kind)
                    .contains(RequestLine.SERVICE, RequestLine.TRAVEL_FEE, RequestLine.TAX);

            // Separadores CLDR iguales a los esperados del perfil
            RegionFormatter.Separators separators = formatter.separators(profile);
            assertThat(separators.match(profile)).as("separadores %s: %s", s.code(), separators).isTrue();

            // Dirección y teléfono validados con las reglas del país
            Map<String, String> address = regionApi.validateAddress(s.code(), s.address());
            String phone = phones.toE164(s.phone(), s.code());
            assertThat(phone).startsWith(profile.phonePrefix());

            // Textos en el idioma del país
            String message = messages.getMessage("error.REQ003_SCHEDULE_IN_PAST", null, locale);
            assertThat(message).isNotBlank();

            String holiday = holidays.findByRegionCodeAndHolidayDate(s.code(), LOCAL_TIME.toLocalDate())
                    .map(h -> "sí: " + h.getName() + " (sin recargo por festivo configurado)").orElse("no");

            rows.add(new Row(s.code(), profile.defaultLocale(), price.name(), message,
                    formatter.money(profile, q.subtotalMinor()), formatter.money(profile, q.travelFeeMinor()),
                    formatter.money(profile, q.taxMinor()) + (q.taxIncluded() ? " (incluido)" : " (se suma)"),
                    formatter.money(profile, q.tipMinor()), formatter.money(profile, q.totalMinor()),
                    shown, zone.getId(), now.toString(),
                    regionApi.formatAddress(s.code(), address), phone,
                    taxes(profile) + "; comprobante " + profile.receiptType() + " (" + profile.receiptPrefix() + ")",
                    profile.paymentMethods().stream().map(m -> m.code() + " (" + m.flow() + ")")
                            .collect(Collectors.joining(", ")),
                    tipPolicy(profile, tipPercent),
                    "CLDR «" + visible(separators.grouping()) + "» y «" + visible(separators.decimal())
                            + "» = perfil", holiday));
        }

        // Colombia y EE. UU. cruzan la medianoche en UTC: el instante guardado es del día siguiente
        assertThat(rows.stream().filter(r -> r.utc().startsWith("2026-11-16")).map(Row::code))
                .containsExactlyInAnyOrder("CO", "US");
        assertThat(rows.stream().filter(r -> r.code().equals("BR")).findFirst().orElseThrow().holiday())
                .startsWith("sí");

        Path dir = evidenceDir();
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("prueba-localizacion.md"), markdown(rows), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("prueba-localizacion.csv"), csv(rows), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ datos

    private CategoryPriceSummary warehousePrice(String region, String locale) {
        return jdbc.queryForObject("""
                SELECT c.id, c.slug, COALESCE(t.name, c.name) AS name, p.unit, p.price_minor, p.currency
                  FROM categories c
                  JOIN category_prices p ON p.category_id = c.id AND p.region_code = ?
                  LEFT JOIN category_translations t ON t.category_id = c.id AND t.locale = ?
                 WHERE c.slug = 'warehouse'
                """, (rs, i) -> new CategoryPriceSummary(rs.getObject("id", UUID.class), rs.getString("slug"),
                rs.getString("name"), rs.getString("unit"), rs.getLong("price_minor"), rs.getString("currency")),
                region, locale);
    }

    private static String taxes(RegionProfileView p) {
        return p.taxRules().stream()
                .map(t -> t.name() + " " + java.math.BigDecimal.valueOf(t.rateBps(), 2).stripTrailingZeros()
                        .toPlainString() + " % " + (t.included() ? "incluido" : "se suma"))
                .collect(Collectors.joining(", "));
    }

    private static String tipPolicy(RegionProfileView p, Integer percent) {
        return switch (p.tipMode()) {
            case PROMPT -> "se pide; sugeridas " + p.tipSuggestedPercents() + " %, se usó " + percent + " %";
            case OPTIONAL -> "opcional; sugerida " + p.tipSuggestedPercents() + " %, se usó " + percent + " %";
            case HIDDEN -> "ausente (no se muestra ni se acepta)";
        };
    }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    /** CLDR usa espacios finos o duros (U+00A0, U+202F) entre la hora y «p. m.»/«PM». */
    private static String plainSpaces(String s) {
        return s.replace(' ', ' ').replace(' ', ' ');
    }

    private static String visible(String separator) {
        return switch (separator) {
            case " " -> "U+202F espacio fino";
            case " " -> "U+00A0 espacio duro";
            default -> separator;
        };
    }

    // ------------------------------------------------------------------ informe

    private static String markdown(List<Row> rows) {
        StringBuilder md = new StringBuilder();
        md.append("# Prueba de localización (sección 8)\n\n");
        md.append("- Fecha de la corrida: ").append(Instant.now()).append('\n');
        md.append("- Versión: ").append(System.getProperty("jornal.version", "ver `git log`")).append('\n');
        md.append("- Prueba: `jornal-core/src/test/java/com/jornal/LocalizationReportTest.java` ")
                .append("(script `scripts/prueba-localizacion.sh`)\n");
        md.append("- Pedido D4: ").append(UNITS).append(" jornadas de bodega + 1 recargo de desplazamiento + propina ")
                .append("según el país. Reloj fijo a las 20:30 locales del 15/11/2026.\n");
        md.append("- Valores de impuestos, precios y tarifas: de los perfiles regionales, marcados VERIFICAR.\n\n");

        md.append("## Resumen\n\n");
        md.append("| País | Idioma | Hora mostrada | Instante UTC guardado | Total | Propina |\n");
        md.append("| --- | --- | --- | --- | --- | --- |\n");
        for (Row r : rows) {
            md.append("| ").append(r.code()).append(" | ").append(r.locale()).append(" | ").append(r.shown())
                    .append(" | ").append(r.utc()).append(" | ").append(r.total()).append(" | ").append(r.tip())
                    .append(" |\n");
        }
        md.append("\nColombia y EE. UU. cruzan la medianoche en UTC: el pedido de las 20:30 del 15/11 queda guardado ")
                .append("el 16/11 en UTC. El 15/11 es feriado nacional en Brasil; hoy no hay recargo por festivo, ")
                .append("así que el precio no cambia.\n");

        for (Row r : rows) {
            md.append("\n## ").append(r.code()).append("\n\n");
            md.append("| Elemento | Resultado |\n| --- | --- |\n");
            item(md, "1. Idioma y textos", r.locale() + "; categoría «" + r.category() + "»; mensaje «" + r.message() + "»");
            item(md, "2. Montos", "subtotal " + r.subtotal() + "; desplazamiento " + r.travel() + "; impuesto "
                    + r.tax() + "; propina " + r.tip() + "; **total " + r.total() + "**");
            item(md, "3. Fecha y hora", r.shown() + " (" + r.zone() + "); guardado " + r.utc());
            item(md, "4. Dirección y teléfono", r.address() + "; " + r.phone());
            item(md, "5. Impuesto y comprobante", r.taxAndReceipt());
            item(md, "6. Medios de pago", r.paymentMethods());
            item(md, "7. Propina", r.tipPolicy());
            item(md, "Separadores", r.separators());
            item(md, "Festivo el 15/11/2026", r.holiday());
        }
        md.append("\n**Resultado:** los 5 países pasan: hora local y UTC, totales, separadores CLDR iguales al perfil, ")
                .append("dirección y teléfono válidos con las reglas de cada país.\n");
        return md.toString();
    }

    private static void item(StringBuilder md, String name, String value) {
        md.append("| ").append(name).append(" | ").append(value.replace("|", "\\|")).append(" |\n");
    }

    private static String csv(List<Row> rows) {
        String header = "pais,idioma,categoria,mensaje,subtotal,desplazamiento,impuesto,propina,total,hora_mostrada,"
                + "zona,utc,direccion,telefono,impuesto_y_comprobante,medios_de_pago,propina_politica,separadores,festivo";
        return header + "\n" + rows.stream().map(r -> String.join(",", cell(r.code()), cell(r.locale()),
                cell(r.category()), cell(r.message()), cell(r.subtotal()), cell(r.travel()), cell(r.tax()),
                cell(r.tip()), cell(r.total()), cell(r.shown()), cell(r.zone()), cell(r.utc()), cell(r.address()),
                cell(r.phone()), cell(r.taxAndReceipt()), cell(r.paymentMethods()), cell(r.tipPolicy()),
                cell(r.separators()), cell(r.holiday()))).collect(Collectors.joining("\n")) + "\n";
    }

    private static String cell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** {@code -Djornal.evidence.dir=...} o la carpeta {@code docs/evidencias} de la raíz del repositorio. */
    private static Path evidenceDir() {
        String configured = System.getProperty("jornal.evidence.dir");
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("docs"))) dir = dir.getParent();
        return (dir == null ? Path.of("") : dir).resolve("docs").resolve("evidencias");
    }

    static {
        // Nombres de locales soportados: si cambia la lista, el informe debe cubrirlos todos
        assertThat(Locales.SUPPORTED).hasSize(SAMPLES.size());
    }
}
