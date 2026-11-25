package jornal.common.events;

/**
 * Tipos de evento (atributo {@code type} de CloudEvents), versionados con {@code .vN}.
 * El prefijo indica quién lo emite: {@code jornal.request.*} y {@code jornal.region.*} el core,
 * {@code jornal.payment.*} pagos, {@code jornal.dispatch.*} asignación y {@code jornal.billing.*} facturación.
 */
public final class EventTypes {

    // ---- core
    public static final String REGION_PROFILE_UPDATED = "jornal.region.profile-updated.v1";
    /** Pedido creado: pagos autoriza o cobra. */
    public static final String REQUEST_CREATED = "jornal.request.created.v1";
    /** Pedido modificado antes de la asignación: pagos anula el intento anterior y autoriza el nuevo. */
    public static final String REQUEST_REPRICED = "jornal.request.repriced.v1";
    /** Comando para asignación: buscar trabajador. */
    public static final String REQUEST_ASSIGNMENT_REQUESTED = "jornal.request.assignment-requested.v1";
    /** Cambio de estado del pedido (notificaciones). */
    public static final String REQUEST_STATUS_CHANGED = "jornal.request.status-changed.v1";
    /** Comando para pagos: capturar el pago tras la confirmación. */
    public static final String REQUEST_CAPTURE_REQUESTED = "jornal.request.capture-requested.v1";
    /** Pedido cancelado: pagos anula o reembolsa y asignación libera la reserva. */
    public static final String REQUEST_CANCELLED = "jornal.request.cancelled.v1";

    /** Vacante publicada: lleva los trabajadores cercanos ({@code nearbyWorkers}) para el aviso emergente. */
    public static final String VACANCY_PUBLISHED = "jornal.vacancy.published.v1";
    /** Cambio de una postulación (elegido, confirmó, declinó, finalizado, cancelado...): notificaciones avisa. */
    public static final String VACANCY_APPLICATION_CHANGED = "jornal.vacancy.application-changed.v1";

    /** Comando para pagos: pagar al trabajador que el empleador finalizó (pago automático activo). */
    public static final String VACANCY_PAYOUT_REQUESTED = "jornal.vacancy.payout-requested.v1";

    /** Foto completa del trabajador (perfil, disponibilidad, elegibilidad) para la proyección de asignación. */
    public static final String WORKER_UPDATED = "jornal.worker.updated.v1";

    // ---- pagos
    public static final String PAYMENT_AUTHORIZED = "jornal.payment.authorized.v1";
    public static final String PAYMENT_FAILED = "jornal.payment.failed.v1";
    public static final String PAYMENT_CAPTURED = "jornal.payment.captured.v1";
    public static final String PAYMENT_VOIDED = "jornal.payment.voided.v1";
    public static final String PAYMENT_REFUNDED = "jornal.payment.refunded.v1";
    /** Pago automático al trabajador de una vacante: hecho o fallido. */
    public static final String PAYMENT_PAYOUT_COMPLETED = "jornal.payment.payout-completed.v1";
    public static final String PAYMENT_PAYOUT_FAILED = "jornal.payment.payout-failed.v1";

    // ---- asignación
    public static final String DISPATCH_WORKER_ASSIGNED = "jornal.dispatch.worker-assigned.v1";
    public static final String DISPATCH_ASSIGNMENT_FAILED = "jornal.dispatch.assignment-failed.v1";
    /** Se ofreció un pedido a un trabajador (notificaciones le avisa). */
    public static final String DISPATCH_OFFER_CREATED = "jornal.dispatch.offer-created.v1";
    /** Posición en vivo del trabajador. Va por el exchange no durable {@code jornal.live}. */
    public static final String DISPATCH_WORKER_LOCATION = "jornal.dispatch.worker-location.v1";

    // ---- facturación
    public static final String BILLING_RECEIPT_ISSUED = "jornal.billing.receipt-issued.v1";

    private EventTypes() {
    }
}
