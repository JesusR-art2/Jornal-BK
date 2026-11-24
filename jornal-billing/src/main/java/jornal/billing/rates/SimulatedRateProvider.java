package jornal.billing.rates;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Proveedor simulado: tasas fijas de referencia contra USD. VERIFICAR antes de usarlas para algo real. */
@Component
public class SimulatedRateProvider implements RateProvider {

    public static final String SOURCE = "Proveedor simulado (valores de referencia, VERIFICAR)";

    private static final Map<String, String> TO_USD = Map.of(
            "COP", "0.00025",     // 1 COP = 0,00025 USD (~4000 COP/USD)
            "BRL", "0.18",
            "EUR", "1.08",
            "JPY", "0.0067");

    @Override
    public String name() {
        return SOURCE;
    }

    @Override
    public List<ExchangeRateRepository.Rate> fetch(LocalDate day) {
        return TO_USD.entrySet().stream()
                .map(e -> new ExchangeRateRepository.Rate(e.getKey(), "USD", new BigDecimal(e.getValue()), SOURCE, day))
                .toList();
    }
}
