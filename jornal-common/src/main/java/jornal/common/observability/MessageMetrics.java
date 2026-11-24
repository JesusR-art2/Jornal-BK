package jornal.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import jornal.common.events.CloudEvent;

/**
 * Métrica {@code jornal_messages_total{consumer, type, region, outcome}}: mensajes procesados ({@code ok}),
 * descartados por el inbox ({@code duplicate}) y fallidos ({@code error}: cada reintento cuenta).
 */
public final class MessageMetrics {

    private MessageMetrics() {
    }

    public static void record(MeterRegistry registry, String consumer, CloudEvent event, String outcome) {
        MeterRegistry target = registry == null ? Metrics.globalRegistry : registry;
        target.counter("jornal.messages", "consumer", consumer, "type", String.valueOf(event.type()),
                "region", String.valueOf(event.region()), "outcome", outcome).increment();
    }
}
