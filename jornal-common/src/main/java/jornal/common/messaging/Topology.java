package jornal.common.messaging;

import org.springframework.amqp.core.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Topología de RabbitMQ versionada en el código. La declaran todos los procesos (es idempotente), así las colas
 * existen aunque el consumidor esté caído y los mensajes esperan en ellas.
 *
 * <ul>
 *   <li>Exchange topic durable {@code jornal.events}; clave {@code <región>.<type>}.</li>
 *   <li>Una cola durable por consumidor y región ({@code payments.co}, {@code core.co}...), para que el atraso
 *       de un país no frene a los demás. Cada consumidor se enlaza solo a los prefijos que le interesan.</li>
 *   <li>Mensajes rechazados tras los reintentos van a {@code jornal.dead-letter}.</li>
 * </ul>
 */
public final class Topology {

    public static final String EXCHANGE = "jornal.events";
    public static final String DEAD_LETTER_EXCHANGE = "jornal.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "jornal.dead-letter";
    /** Exchange topic NO durable para datos efímeros (posición en vivo): gana el último valor, perder uno no importa. */
    public static final String LIVE_EXCHANGE = "jornal.live";
    public static final String CORE = "core";
    public static final List<String> SUBSYSTEMS = List.of("payments", "dispatch", "notifications", "billing");

    /** Prefijos de tipo que consume cada proceso (sin región). "#" = todo. */
    private static final Map<String, List<String>> SUBSCRIPTIONS = Map.of(
            CORE, List.of("jornal.payment.#", "jornal.dispatch.#", "jornal.billing.#"),
            "payments", List.of("jornal.request.#", "jornal.region.#", "jornal.vacancy.payout-requested.#"),
            "dispatch", List.of("jornal.request.#", "jornal.region.#", "jornal.worker.#"),
            "notifications", List.of("#"),
            "billing", List.of("jornal.payment.#", "jornal.region.#"));

    private Topology() {
    }

    public static String queue(String consumer, String region) {
        return consumer + "." + region.toLowerCase(Locale.ROOT);
    }

    public static List<String> queuesOf(String consumer, List<String> regions) {
        return regions.stream().map(r -> queue(consumer, r)).toList();
    }

    public static Declarables declarables(List<String> regions) {
        return declarables(regions, List.of());
    }

    /** Topología de las regiones dadas; {@code consumers} vacío declara las colas de todos los procesos. */
    public static Declarables declarables(List<String> regions, List<String> consumers) {
        List<Declarable> all = new ArrayList<>();
        TopicExchange events = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        FanoutExchange dlx = ExchangeBuilder.fanoutExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
        Queue deadLetters = QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
        all.add(events);
        all.add(dlx);
        all.add(deadLetters);
        all.add(BindingBuilder.bind(deadLetters).to(dlx));
        all.add(ExchangeBuilder.topicExchange(LIVE_EXCHANGE).durable(false).build());

        SUBSCRIPTIONS.forEach((consumer, patterns) -> {
            if (!consumers.isEmpty() && !consumers.contains(consumer)) return;
            for (String region : regions) {
                Queue queue = QueueBuilder.durable(queue(consumer, region))
                        .withArguments(Map.of("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE))
                        .build();
                all.add(queue);
                String prefix = region.toLowerCase(Locale.ROOT) + ".";
                for (String pattern : patterns) {
                    all.add(BindingBuilder.bind(queue).to(events).with(prefix + pattern));
                }
            }
        });
        return new Declarables(all);
    }
}
