package jornal.payments.gateway;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** Ajustes vigentes del proveedor simulado (se inician desde la configuración y se cambian en caliente). */
@Component
public class Simulator {

    private final AtomicReference<SimulatorSettings> settings;

    public Simulator(SimulatorSettings initial) {
        this.settings = new AtomicReference<>(initial);
    }

    public SimulatorSettings settings() {
        return settings.get();
    }

    public SimulatorSettings update(SimulatorSettings next) {
        settings.set(next);
        return next;
    }
}
