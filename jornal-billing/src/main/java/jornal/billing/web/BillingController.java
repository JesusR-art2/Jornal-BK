package jornal.billing.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jornal.billing.Receipt;
import jornal.billing.ReceiptRenderer;
import jornal.billing.ReceiptRepository;
import jornal.billing.rates.ExchangeRateRepository;
import jornal.billing.rates.ExchangeRateService;
import jornal.billing.rates.RevenueReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Comprobantes (cliente, trabajador del pedido o administrador), tasas de cambio y reporte (administrador). */
@RestController
public class BillingController {

    private final ReceiptRepository receipts;
    private final ReceiptRenderer renderer;
    private final ExchangeRateService rates;
    private final RevenueReport report;
    private final Clock clock;

    public BillingController(ReceiptRepository receipts, ReceiptRenderer renderer, ExchangeRateService rates,
                             RevenueReport report, Clock clock) {
        this.receipts = receipts;
        this.renderer = renderer;
        this.rates = rates;
        this.report = report;
        this.clock = clock;
    }

    public record ManualRate(@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String base,
                             @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String quote,
                             @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal rate,
                             @NotBlank @Size(max = 160) String source, LocalDate asOf) {
    }

    /** Comprobante en JSON con los valores ya formateados en el idioma del país (o {@code lang}). */
    @GetMapping("/receipts/{requestId}")
    public ReceiptRenderer.View receipt(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId,
                                        @RequestParam(required = false) String lang) {
        return renderer.view(allowed(jwt, requestId), lang);
    }

    /** Comprobante en HTML imprimible. */
    @GetMapping(value = "/receipts/{requestId}/html", produces = MediaType.TEXT_HTML_VALUE)
    public String receiptHtml(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId,
                              @RequestParam(required = false) String lang) {
        return renderer.html(allowed(jwt, requestId), lang);
    }

    @GetMapping("/admin/receipts")
    public List<Receipt> list(@RequestParam(required = false) String region,
                              @RequestParam(defaultValue = "50") int limit) {
        return receipts.list(region == null ? null : region.toLowerCase(Locale.ROOT), Math.clamp(limit, 1, 200));
    }

    @GetMapping("/admin/rates")
    public List<ExchangeRateRepository.Rate> rates(@RequestParam(defaultValue = "50") int limit) {
        return rates.recent(Math.clamp(limit, 1, 500));
    }

    /** Carga las tasas del día desde el proveedor (simulado). */
    @PostMapping("/admin/rates/refresh")
    public List<ExchangeRateRepository.Rate> refresh() {
        return rates.refreshFromProvider();
    }

    /** Carga manual de una tasa (por ejemplo la oficial publicada por el banco central). */
    @PostMapping("/admin/rates")
    @ResponseStatus(HttpStatus.CREATED)
    public ExchangeRateRepository.Rate manual(@Valid @RequestBody ManualRate body) {
        return rates.manual(body.base(), body.quote(), body.rate(), body.source(), body.asOf());
    }

    /** Ingresos de todas las regiones en la moneda elegida, con la tasa usada y su fecha. */
    @GetMapping("/admin/reports/revenue")
    public Map<String, Object> revenue(@RequestParam(defaultValue = "USD") String currency,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        try {
            java.util.Currency.getInstance(currency.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown currency " + currency);
        }
        return report.build(currency, from == null ? today.withDayOfMonth(1) : from, to == null ? today : to);
    }

    private Receipt allowed(Jwt jwt, UUID requestId) {
        Receipt r = receipts.findByRequest(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        UUID user = UUID.fromString(jwt.getSubject());
        boolean admin = "ADMIN".equals(jwt.getClaimAsString("role"));
        if (!admin && !user.equals(r.clientId()) && !user.equals(r.workerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return r;
    }
}
