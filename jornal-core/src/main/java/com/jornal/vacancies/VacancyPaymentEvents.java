package com.jornal.vacancies;

import com.jornal.vacancies.services.VacancyService;
import jornal.common.events.EventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** Contrato público del módulo: respuestas de pagos al pago automático del trabajador de una vacante. */
@Component
@RequiredArgsConstructor
public class VacancyPaymentEvents {

    private static final Set<String> TYPES = Set.of(EventTypes.PAYMENT_PAYOUT_COMPLETED,
            EventTypes.PAYMENT_PAYOUT_FAILED);

    private final VacancyService service;

    public boolean supports(String type) {
        return TYPES.contains(type);
    }

    public void handle(String type, Map<String, Object> data) {
        service.onPayoutResult(type, data == null ? Map.of() : data);
    }
}
