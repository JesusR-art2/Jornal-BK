package jornal.common.subsystem;

import jornal.common.events.CloudEvent;

/**
 * Lógica de un subsistema para un tipo de evento. Corre dentro de la transacción del inbox:
 * si falla, el mensaje se reintenta y el inbox no queda marcado.
 */
public interface CloudEventHandler {

    boolean supports(String type);

    void handle(CloudEvent event);
}
