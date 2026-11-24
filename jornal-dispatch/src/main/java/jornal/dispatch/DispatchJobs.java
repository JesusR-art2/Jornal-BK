package jornal.dispatch;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Vencimiento de ofertas y reintento de búsquedas sin candidatos, cada 2 s. */
@Component
public class DispatchJobs {

    private final DispatchService dispatch;

    public DispatchJobs(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @Scheduled(fixedDelayString = "${jornal.dispatch.check-every:2s}")
    public void run() {
        Instant now = Instant.now();
        dispatch.expireOffers(now);
        dispatch.retrySearches(now.minusSeconds(5));
    }
}
