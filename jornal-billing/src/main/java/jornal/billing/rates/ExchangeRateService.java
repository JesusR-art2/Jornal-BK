package jornal.billing.rates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Tasas de cambio con par, tasa, fuente y fecha: carga diaria del proveedor (simulado) y carga manual del
 * administrador. Solo sirven para reportes; los cobros siempre van en la moneda local.
 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private final ExchangeRateRepository rates;
    private final RateProvider provider;
    private final Clock clock;

    public ExchangeRateService(ExchangeRateRepository rates, RateProvider provider, Clock clock) {
        this.rates = rates;
        this.provider = provider;
        this.clock = clock;
    }

    /** Tasa usada en una conversión, con su origen (directa, inversa o cruzada por USD). */
    public record AppliedRate(String pair, BigDecimal rate, String source, LocalDate asOf, String method) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadIfEmpty() {
        if (rates.count() == 0) refreshFromProvider();
    }

    @Scheduled(cron = "${jornal.billing.rates.refresh-cron:0 0 6 * * *}", zone = "UTC")
    public void scheduledRefresh() {
        refreshFromProvider();
    }

    public List<ExchangeRateRepository.Rate> refreshFromProvider() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        List<ExchangeRateRepository.Rate> fetched = provider.fetch(today);
        fetched.forEach(r -> rates.save(r, clock.instant()));
        log.info("Loaded {} exchange rates from {} for {}", fetched.size(), provider.name(), today);
        return fetched;
    }

    public ExchangeRateRepository.Rate manual(String base, String quote, BigDecimal rate, String source, LocalDate asOf) {
        ExchangeRateRepository.Rate r = new ExchangeRateRepository.Rate(base.toUpperCase(), quote.toUpperCase(), rate,
                source, asOf == null ? LocalDate.now(clock.withZone(ZoneOffset.UTC)) : asOf);
        rates.save(r, clock.instant());
        return r;
    }

    public List<ExchangeRateRepository.Rate> recent(int limit) {
        return rates.recent(limit);
    }

    /** Tasa para convertir {@code from} a {@code to} vigente el día {@code on}. */
    public Optional<AppliedRate> rate(String from, String to, LocalDate on) {
        if (from.equals(to)) {
            return Optional.of(new AppliedRate(from + "/" + to, BigDecimal.ONE, "-", on, "SAME"));
        }
        Optional<ExchangeRateRepository.Rate> direct = rates.latest(from, to, on);
        if (direct.isPresent()) {
            ExchangeRateRepository.Rate r = direct.get();
            return Optional.of(new AppliedRate(from + "/" + to, r.rate(), r.source(), r.asOf(), "DIRECT"));
        }
        Optional<ExchangeRateRepository.Rate> inverse = rates.latest(to, from, on);
        if (inverse.isPresent()) {
            ExchangeRateRepository.Rate r = inverse.get();
            return Optional.of(new AppliedRate(from + "/" + to, BigDecimal.ONE.divide(r.rate(), PRECISION),
                    r.source(), r.asOf(), "INVERSE"));
        }
        if (!"USD".equals(from) && !"USD".equals(to)) {
            Optional<AppliedRate> a = rate(from, "USD", on);
            Optional<AppliedRate> b = rate("USD", to, on);
            if (a.isPresent() && b.isPresent()) {
                LocalDate asOf = a.get().asOf().isBefore(b.get().asOf()) ? a.get().asOf() : b.get().asOf();
                String source = a.get().source().equals(b.get().source()) ? a.get().source()
                        : a.get().source() + " + " + b.get().source();
                return Optional.of(new AppliedRate(from + "/" + to, a.get().rate().multiply(b.get().rate(), PRECISION),
                        source, asOf, "CROSS_USD"));
            }
        }
        return Optional.empty();
    }
}
