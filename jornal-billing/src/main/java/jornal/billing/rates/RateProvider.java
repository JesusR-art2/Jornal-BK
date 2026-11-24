package jornal.billing.rates;

import java.time.LocalDate;
import java.util.List;

/**
 * Proveedor de tasas de cambio. Un adaptador real (por ejemplo el Banco de la República, el BCE o un servicio
 * comercial) implementa este método con su API; el resto de facturación no cambia.
 */
public interface RateProvider {

    String name();

    /** Tasas del día contra el dólar: 1 moneda = rate USD. */
    List<ExchangeRateRepository.Rate> fetch(LocalDate day);
}
