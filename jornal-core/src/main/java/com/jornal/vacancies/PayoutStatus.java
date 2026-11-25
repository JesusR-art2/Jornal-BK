package com.jornal.vacancies;

/**
 * Estado del pago al trabajador que terminó el trabajo.
 * PENDING: a mano, falta que el empleador lo registre. PROCESSING: automático, enviado al subsistema de pagos.
 * PAID: pagado. FAILED: el pago automático falló; el empleador puede pagarlo a mano.
 */
public enum PayoutStatus {
    PENDING,
    PROCESSING,
    PAID,
    FAILED
}
