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
 *   <li>Exchange topic durable {@code jornal.events}.</li>
 *   <li>Una cola durable por subsistema y región ({@code payments.co}, {@code payments.br}...), para que el atraso
 *       de un país no frene a los demás. Se enlaza con {@code <región>.#}.</li>
 *   <li>Mensajes rechazados tras los reintentos van a {@code jornal.dead-letter}.</li>
 * </ul>
 */
public final class Topology {

    public static final String EXCHANGE = "jornal.events";
    public static final String DEAD_LETTER_EXCHANGE = "jornal.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "jornal.dead-letter";
    public static final List<String> SUBSYSTEMS = List.of("payments", "dispatch", "notifications", "billing");

    private Topology() {
    }

    public static String queue(String subsystem, String region) {
        return subsystem + "." + region.toLowerCase(Locale.ROOT);
    }

    public static List<String> queuesOf(String subsystem, List<String> regions) {
        return regions.stream().map(r -> queue(subsystem, r)).toList();
    }

    public static Declarables declarables(List<String> regions) {
        List<Declarable> all = new ArrayList<>();
        TopicExchange events = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        FanoutExchange dlx = ExchangeBuilder.fanoutExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
        Queue deadLetters = QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
        all.add(events);
        all.add(dlx);
        all.add(deadLetters);
        all.add(BindingBuilder.bind(deadLetters).to(dlx));

        for (String subsystem : SUBSYSTEMS) {
            for (String region : regions) {
                Queue queue = QueueBuilder.durable(queue(subsystem, region))
                        .withArguments(Map.of("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE))
                        .build();
                all.add(queue);
                all.add(BindingBuilder.bind(queue).to(events).with(region.toLowerCase(Locale.ROOT) + ".#"));
            }
        }
        return new Declarables(all);
    }
}
