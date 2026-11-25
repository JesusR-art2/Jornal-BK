package com.jornal.vacancies.services;

import com.jornal.region.RegionApi;
import com.jornal.region.RegionProfileView;
import com.jornal.shared.ApiException;
import com.jornal.shared.IntegrationEvents;
import com.jornal.shared.PageResponse;
import com.jornal.users.UserApi;
import com.jornal.users.UserSummary;
import com.jornal.vacancies.*;
import com.jornal.vacancies.dto.*;
import com.jornal.vacancies.enums.ErrorVacancy;
import com.jornal.vacancies.translation.VacancyTranslations;
import org.springframework.context.i18n.LocaleContextHolder;
import com.jornal.workers.NearbyWorker;
import com.jornal.workers.WorkerApi;
import com.jornal.workers.WorkerSummary;
import jornal.common.events.EventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Flujo de vacantes de trabajo temporal:
 * <ol>
 *   <li>La empresa publica la vacante; los trabajadores cuyo radio alcanza el lugar reciben un aviso emergente.</li>
 *   <li>Los trabajadores ven el listado y se postulan.</li>
 *   <li>La empresa ve los candidatos ordenados por puntaje y elige varios, hasta los cupos que necesita.</li>
 *   <li>Cada elegido recibe un aviso con día, hora, lugar e implementos y un botón para confirmar asistencia.</li>
 *   <li>Tras la hora de fin, la empresa cierra a cada persona: finalizado (pasa al formato de pago) o
 *       cancelado con justificación (no fue o dejó el trabajo a medias; sin pago).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class VacancyService {

    /** Máximo de trabajadores cercanos que reciben el aviso de una vacante nueva. */
    static final int MAX_NEARBY_NOTIFIED = 500;

    private static final Set<ApplicationStatus> SLOT = EnumSet.of(
            ApplicationStatus.SELECTED, ApplicationStatus.CONFIRMED, ApplicationStatus.COMPLETED);
    /** Personas que deben cerrarse (finalizar o cancelar) cuando termina el trabajo. */
    private static final Set<ApplicationStatus> TO_CLOSE = EnumSet.of(
            ApplicationStatus.SELECTED, ApplicationStatus.CONFIRMED);

    private final VacancyRepository vacancies;
    private final VacancyApplicationRepository applications;
    private final EmployerSettingsRepository settings;
    private final VacancyTranslations translations;
    private final RegionApi regionApi;
    private final UserApi userApi;
    private final WorkerApi workerApi;
    private final IntegrationEvents events;
    private final Clock clock;

    /** Quién llama: id y rol del JWT. */
    public record Caller(UUID id, String role) {
        boolean isEmployer() { return "APPLICANT".equals(role) || "BUSINESS".equals(role); }
        boolean isWorker() { return "WORKER".equals(role); }
        boolean isAdmin() { return "ADMIN".equals(role); }
    }

    // ================================================================== empleador

    @Transactional
    public VacancyResponse publish(Caller caller, VacancyDraft draft) {
        if (!caller.isEmployer()) throw new ApiException(ErrorVacancy.VAC004_NOT_ALLOWED);
        RegionProfileView profile = regionApi.current();
        String zone = draft.timezone() == null || draft.timezone().isBlank() ? profile.defaultTimezone() : draft.timezone();
        if (!profile.allowedTimezones().contains(zone)) {
            throw new ApiException(ErrorVacancy.VAC003_TIMEZONE_NOT_ALLOWED, zone);
        }
        Instant now = clock.instant();
        Instant startsAt = at(draft.startLocal(), zone);
        Instant endsAt = at(draft.endLocal(), zone);
        if (!startsAt.isAfter(now) || !endsAt.isAfter(startsAt)) {
            throw new ApiException(ErrorVacancy.VAC002_INVALID_SCHEDULE);
        }
        Map<String, String> address = regionApi.validateAddress(profile.code(), draft.address());

        Vacancy v = new Vacancy();
        v.setId(UUID.randomUUID());
        v.setEmployerId(caller.id());
        v.setEmployerRole(caller.role());
        v.setRegionCode(profile.code());
        v.setTitle(draft.title().trim());
        v.setDescription(draft.description().trim());
        v.setCategoryId(draft.categoryId());
        if (draft.items() != null) draft.items().forEach(i -> v.getItems().add(i.trim()));
        v.setAddressText(regionApi.formatAddress(profile.code(), address));
        v.setLatitude(draft.latitude());
        v.setLongitude(draft.longitude());
        v.setTimezone(zone);
        // Idioma en que el empleador escribe (el de su petición); de ahí se traduce para cada lector.
        v.setSourceLocale(LocaleContextHolder.getLocale().toLanguageTag());
        v.setStartsAt(startsAt);
        v.setEndsAt(endsAt);
        v.setPositions(draft.positions());
        v.setPayMinor(draft.payMinor());
        v.setCurrency(profile.currency());
        v.setMoneyScale(profile.moneyScale());
        v.setStatus(VacancyStatus.OPEN);
        v.setCreatedAt(now);
        v.setUpdatedAt(now);
        vacancies.save(v);

        // Aviso emergente solo a los trabajadores cuyo radio de trabajo alcanza el lugar
        List<NearbyWorker> nearby = workerApi.findNearby(v.getLatitude(), v.getLongitude(), MAX_NEARBY_NOTIFIED);
        Map<String, Object> data = vacancyData(v);
        data.put("nearbyWorkers", nearby.stream()
                .map(n -> Map.<String, Object>of("workerId", n.workerId().toString(), "distanceKm", n.distanceKm()))
                .toList());
        events.publish(EventTypes.VACANCY_PUBLISHED, v.getId().toString(), v.nextEventSeq(), data, v.getId().toString());
        return toResponse(v, caller);
    }

    /** Vacantes propias. {@code pendingClosure}: solo las ya terminadas con personas por finalizar o cancelar. */
    @Transactional(readOnly = true)
    public PageResponse<VacancyResponse> mine(Caller caller, VacancyStatus status, boolean pendingClosure,
                                              Pageable pageable) {
        if (!caller.isEmployer()) throw new ApiException(ErrorVacancy.VAC004_NOT_ALLOWED);
        Page<Vacancy> page = pendingClosure
                ? vacancies.findPendingClosure(caller.id(), clock.instant(), pageable)
                : status == null ? vacancies.findByEmployerId(caller.id(), pageable)
                : vacancies.findByEmployerIdAndStatusIn(caller.id(), List.of(status), pageable);
        return PageResponse.from(page.map(v -> toResponse(v, caller)));
    }

    /** Candidatos de la vacante, el de mayor puntaje primero. {@code status} filtra (por ejemplo, CONFIRMED). */
    @Transactional(readOnly = true)
    public List<ApplicationResponse> candidates(Caller caller, UUID vacancyId, ApplicationStatus status) {
        Vacancy v = owned(caller, vacancyId);
        List<VacancyApplication> list = status == null
                ? applications.findByVacancyId(vacancyId)
                : applications.findByVacancyIdAndStatusIn(vacancyId, List.of(status));
        return list.stream()
                .map(a -> toResponse(a, v))
                .sorted(Comparator.comparingDouble(ApplicationResponse::score).reversed()
                        .thenComparing(ApplicationResponse::createdAt))
                .toList();
    }

    /** Contrata a varias personas a la vez; cada una recibe el aviso con el botón para confirmar asistencia. */
    @Transactional
    public List<ApplicationResponse> select(Caller caller, UUID vacancyId, Set<UUID> applicationIds) {
        Vacancy v = owned(caller, vacancyId);
        Instant now = clock.instant();
        if (v.getStatus() != VacancyStatus.OPEN && v.getStatus() != VacancyStatus.FILLED) {
            throw new ApiException(ErrorVacancy.VAC005_NOT_OPEN);
        }
        if (!now.isBefore(v.getStartsAt())) throw new ApiException(ErrorVacancy.VAC011_ALREADY_STARTED);
        long free = v.getPositions() - applications.countByVacancyIdAndStatusIn(vacancyId, SLOT);
        if (applicationIds.size() > free) throw new ApiException(ErrorVacancy.VAC009_NO_SLOTS, Math.max(free, 0));

        List<VacancyApplication> chosen = new ArrayList<>();
        for (UUID id : applicationIds) {
            VacancyApplication a = application(v, id);
            move(a, ApplicationStatus.APPLIED, ApplicationStatus.SELECTED, now);
            a.setSelectedAt(now);
            chosen.add(a);
        }
        applications.saveAll(chosen);
        chosen.forEach(a -> publishChange(v, a, a.getStatus().name()));
        refreshFilled(v, now);
        return chosen.stream().map(a -> toResponse(a, v)).toList();
    }

    /** Tras la hora de fin: la persona terminó su trabajo y pasa al formato de pago. */
    @Transactional
    public ApplicationResponse complete(Caller caller, UUID vacancyId, UUID applicationId) {
        Vacancy v = owned(caller, vacancyId);
        Instant now = clock.instant();
        if (!v.hasEnded(now)) throw new ApiException(ErrorVacancy.VAC010_NOT_ENDED);
        VacancyApplication a = application(v, applicationId);
        moveFrom(a, TO_CLOSE, ApplicationStatus.COMPLETED, now);
        a.setClosedAt(now);
        a.setPayoutMinor(v.getPayMinor());
        boolean auto = settingsOf(v.getEmployerId()).isAutoPayout() && v.getPayMinor() > 0;
        a.setPayoutMode(auto ? PayoutMode.AUTO : PayoutMode.MANUAL);
        a.setPayoutStatus(auto ? PayoutStatus.PROCESSING : PayoutStatus.PENDING);
        applications.save(a);
        publishChange(v, a, a.getStatus().name());
        if (auto) {
            // Solo «Finalizar» dispara el pago automático: el trabajador se presentó y terminó
            Map<String, Object> data = new HashMap<>();
            data.put("applicationId", a.getId().toString());
            data.put("vacancyId", v.getId().toString());
            data.put("workerId", a.getWorkerId().toString());
            data.put("employerId", v.getEmployerId().toString());
            data.put("amountMinor", a.getPayoutMinor());
            data.put("currency", v.getCurrency());
            events.publish(EventTypes.VACANCY_PAYOUT_REQUESTED, a.getId().toString(), v.nextEventSeq(), data,
                    v.getId().toString());
        }
        closeIfDone(v, now);
        return toResponse(a, v);
    }

    // ------------------------------------------------------------------ configuración de pago

    @Transactional(readOnly = true)
    public EmployerSettingsDto settings(Caller caller) {
        if (!caller.isEmployer()) throw new ApiException(ErrorVacancy.VAC004_NOT_ALLOWED);
        return new EmployerSettingsDto(settingsOf(caller.id()).isAutoPayout());
    }

    /** Botón activo/inactivo: pago automático al finalizar o pago a mano. Aplica a lo que se finalice después. */
    @Transactional
    public EmployerSettingsDto updateSettings(Caller caller, EmployerSettingsDto body) {
        if (!caller.isEmployer()) throw new ApiException(ErrorVacancy.VAC004_NOT_ALLOWED);
        EmployerSettings s = settingsOf(caller.id());
        s.setAutoPayout(body.autoPayout());
        s.setUpdatedAt(clock.instant());
        settings.save(s);
        return new EmployerSettingsDto(s.isAutoPayout());
    }

    private EmployerSettings settingsOf(UUID employerId) {
        return settings.findById(employerId).orElseGet(() -> {
            EmployerSettings s = new EmployerSettings();
            s.setEmployerId(employerId);
            s.setAutoPayout(false);
            s.setUpdatedAt(clock.instant());
            return s;
        });
    }

    /** Respuesta del subsistema de pagos al pago automático. Duplicados o tardíos se ignoran. */
    @Transactional
    public void onPayoutResult(String type, Map<String, Object> data) {
        UUID applicationId = UUID.fromString(String.valueOf(data.get("applicationId")));
        Optional<VacancyApplication> found = applications.findById(applicationId);
        if (found.isEmpty() || found.get().getPayoutStatus() != PayoutStatus.PROCESSING) return;
        VacancyApplication a = found.get();
        Vacancy v = find(a.getVacancyId());
        Instant now = clock.instant();
        if (EventTypes.PAYMENT_PAYOUT_COMPLETED.equals(type)) {
            a.setPayoutStatus(PayoutStatus.PAID);
            a.setPaidAmountMinor(((Number) data.get("amountMinor")).longValue());
            a.setPayoutRef(data.get("providerRef") == null ? null : data.get("providerRef").toString());
            a.setPaidAt(now);
            publishChange(v, a, "PAID");
        } else {
            a.setPayoutStatus(PayoutStatus.FAILED);
            a.setPayoutFailure(String.valueOf(data.getOrDefault("reason", "UNKNOWN")));
            publishChange(v, a, "PAYOUT_FAILED");
        }
        a.setUpdatedAt(now);
        applications.save(a);
    }

    /** Tras la hora de fin: la persona no fue o dejó el trabajo a medias. No se le paga; queda la justificación. */
    @Transactional
    public ApplicationResponse cancelWork(Caller caller, UUID vacancyId, UUID applicationId, CancelWorkRequest body) {
        Vacancy v = owned(caller, vacancyId);
        Instant now = clock.instant();
        if (!v.hasEnded(now)) throw new ApiException(ErrorVacancy.VAC010_NOT_ENDED);
        VacancyApplication a = application(v, applicationId);
        moveFrom(a, TO_CLOSE, ApplicationStatus.CANCELLED, now);
        a.setClosedAt(now);
        a.setCloseReason(body.reason());
        a.setJustification(body.justification().trim());
        applications.save(a);
        publishChange(v, a, a.getStatus().name());
        closeIfDone(v, now);
        return toResponse(a, v);
    }

    /** Retira la vacante antes de que empiece; se avisa a quienes estaban elegidos. */
    @Transactional
    public VacancyResponse cancelVacancy(Caller caller, UUID vacancyId) {
        Vacancy v = owned(caller, vacancyId);
        Instant now = clock.instant();
        if (v.getStatus() != VacancyStatus.OPEN && v.getStatus() != VacancyStatus.FILLED) {
            throw new ApiException(ErrorVacancy.VAC005_NOT_OPEN);
        }
        if (!now.isBefore(v.getStartsAt())) throw new ApiException(ErrorVacancy.VAC011_ALREADY_STARTED);
        v.setStatus(VacancyStatus.CANCELLED);
        v.setUpdatedAt(now);
        for (VacancyApplication a : applications.findByVacancyIdAndStatusIn(v.getId(),
                EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.SELECTED, ApplicationStatus.CONFIRMED))) {
            boolean wasHired = a.getStatus() != ApplicationStatus.APPLIED;
            a.setStatus(ApplicationStatus.NOT_SELECTED);
            a.setUpdatedAt(now);
            publishChange(v, a, wasHired ? "VACANCY_CANCELLED" : ApplicationStatus.NOT_SELECTED.name());
        }
        return toResponse(v, caller);
    }

    /** Formato de pago: personas que terminaron trabajos de este empleador. {@code status} PENDING o PAID. */
    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> payouts(Caller caller, PayoutStatus status, Pageable pageable) {
        if (!caller.isEmployer()) throw new ApiException(ErrorVacancy.VAC004_NOT_ALLOWED);
        Page<VacancyApplication> page = applications.findPayouts(caller.id(), status, pageable);
        Map<UUID, Vacancy> cache = new HashMap<>();
        return PageResponse.from(page.map(a ->
                toResponse(a, cache.computeIfAbsent(a.getVacancyId(), id -> vacancies.findById(id).orElseThrow()))));
    }

    /**
     * Pago a mano: el empleador registra cuánto pagó. Si es menos de lo que dice la vacante, la justificación
     * es obligatoria. También sirve para pagar a mano un pago automático que falló.
     */
    @Transactional
    public ApplicationResponse markPaid(Caller caller, UUID vacancyId, UUID applicationId, ManualPaymentRequest body) {
        Vacancy v = owned(caller, vacancyId);
        VacancyApplication a = application(v, applicationId);
        if (a.getPayoutStatus() == PayoutStatus.PROCESSING) {
            throw new ApiException(ErrorVacancy.VAC015_AUTO_PAYOUT_IN_PROGRESS);
        }
        if (a.getStatus() != ApplicationStatus.COMPLETED
                || (a.getPayoutStatus() != PayoutStatus.PENDING && a.getPayoutStatus() != PayoutStatus.FAILED)) {
            throw new ApiException(ErrorVacancy.VAC013_ALREADY_PAID);
        }
        String note = body.justification() == null || body.justification().isBlank() ? null : body.justification().trim();
        if (body.amountMinor() < a.getPayoutMinor() && note == null) {
            throw new ApiException(ErrorVacancy.VAC014_JUSTIFICATION_REQUIRED, a.getPayoutMinor());
        }
        Instant now = clock.instant();
        a.setPayoutMode(PayoutMode.MANUAL);
        a.setPaidAmountMinor(body.amountMinor());
        a.setPaymentNote(note);
        a.setPayoutStatus(PayoutStatus.PAID);
        a.setPaidAt(now);
        a.setUpdatedAt(now);
        applications.save(a);
        publishChange(v, a, "PAID");
        return toResponse(a, v);
    }

    // ================================================================== trabajador

    /** Listado de vacantes que reciben postulaciones, con la distancia desde la ubicación del trabajador. */
    @Transactional(readOnly = true)
    public PageResponse<VacancyResponse> open(Caller caller, Double maxDistanceKm, Pageable pageable) {
        Page<Vacancy> page = vacancies.findOpen(clock.instant(), pageable);
        List<VacancyResponse> items = page.getContent().stream()
                .map(v -> toResponse(v, caller))
                .filter(r -> maxDistanceKm == null || (r.distanceKm() != null && r.distanceKm() <= maxDistanceKm))
                .toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public VacancyResponse get(Caller caller, UUID vacancyId) {
        Vacancy v = find(vacancyId);
        if (caller.isEmployer() && !v.getEmployerId().equals(caller.id())) {
            throw new ApiException(ErrorVacancy.VAC001_NOT_FOUND, vacancyId);
        }
        return toResponse(v, caller);
    }

    @Transactional
    public ApplicationResponse apply(Caller caller, UUID vacancyId, String message) {
        if (!caller.isWorker()) throw new ApiException(ErrorVacancy.VAC012_NOT_WORKER);
        Vacancy v = find(vacancyId);
        Instant now = clock.instant();
        if (v.getStatus() != VacancyStatus.OPEN || !now.isBefore(v.getStartsAt())) {
            throw new ApiException(ErrorVacancy.VAC005_NOT_OPEN);
        }
        Optional<VacancyApplication> existing = applications.findByVacancyIdAndWorkerId(vacancyId, caller.id());
        VacancyApplication a;
        if (existing.isPresent()) {
            a = existing.get();
            // Quien retiró su postulación puede volver a postularse mientras la vacante siga abierta
            if (a.getStatus() != ApplicationStatus.WITHDRAWN) throw new ApiException(ErrorVacancy.VAC006_ALREADY_APPLIED);
            a.setStatus(ApplicationStatus.APPLIED);
            a.setSelectedAt(null);
            a.setRespondedAt(null);
        } else {
            a = new VacancyApplication();
            a.setId(UUID.randomUUID());
            a.setVacancyId(vacancyId);
            a.setWorkerId(caller.id());
            a.setStatus(ApplicationStatus.APPLIED);
            a.setCreatedAt(now);
        }
        a.setMessage(message == null || message.isBlank() ? null : message.trim());
        a.setDistanceKm(workerApi.distanceKm(caller.id(), v.getLatitude(), v.getLongitude()).orElse(null));
        a.setUpdatedAt(now);
        applications.save(a);
        publishChange(v, a, a.getStatus().name());
        return toResponse(a, v);
    }

    /** Botón «¿Puedes asistir?»: confirma o declina. Declinar libera el cupo y reabre la vacante. */
    @Transactional
    public ApplicationResponse attendance(Caller caller, UUID vacancyId, boolean attending) {
        Vacancy v = find(vacancyId);
        VacancyApplication a = mine(caller, vacancyId);
        Instant now = clock.instant();
        if (!now.isBefore(v.getStartsAt())) throw new ApiException(ErrorVacancy.VAC011_ALREADY_STARTED);
        move(a, ApplicationStatus.SELECTED, attending ? ApplicationStatus.CONFIRMED : ApplicationStatus.DECLINED, now);
        a.setRespondedAt(now);
        applications.save(a);
        publishChange(v, a, a.getStatus().name());
        refreshFilled(v, now);
        return toResponse(a, v);
    }

    /** El trabajador retira su postulación (o su compromiso) antes de que empiece el trabajo. */
    @Transactional
    public ApplicationResponse withdraw(Caller caller, UUID vacancyId) {
        Vacancy v = find(vacancyId);
        VacancyApplication a = mine(caller, vacancyId);
        Instant now = clock.instant();
        if (!now.isBefore(v.getStartsAt())) throw new ApiException(ErrorVacancy.VAC011_ALREADY_STARTED);
        moveFrom(a, EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.SELECTED, ApplicationStatus.CONFIRMED),
                ApplicationStatus.WITHDRAWN, now);
        applications.save(a);
        publishChange(v, a, a.getStatus().name());
        refreshFilled(v, now);
        return toResponse(a, v);
    }

    /** Postulaciones del trabajador (incluye a qué vacantes debe ir y su estado de pago). */
    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> myApplications(Caller caller, Pageable pageable) {
        if (!caller.isWorker()) throw new ApiException(ErrorVacancy.VAC012_NOT_WORKER);
        Map<UUID, Vacancy> cache = new HashMap<>();
        return PageResponse.from(applications.findByWorkerId(caller.id(), pageable).map(a ->
                toResponse(a, cache.computeIfAbsent(a.getVacancyId(), id -> vacancies.findById(id).orElseThrow()))));
    }

    // ================================================================== reglas internas

    /** OPEN ↔ FILLED según los cupos ocupados. */
    private void refreshFilled(Vacancy v, Instant now) {
        if (v.getStatus() != VacancyStatus.OPEN && v.getStatus() != VacancyStatus.FILLED) return;
        long taken = applications.countByVacancyIdAndStatusIn(v.getId(), SLOT);
        VacancyStatus next = taken >= v.getPositions() ? VacancyStatus.FILLED : VacancyStatus.OPEN;
        if (next != v.getStatus()) {
            v.setStatus(next);
            v.setUpdatedAt(now);
        }
    }

    /** Cuando ya no quedan personas por cerrar, la vacante se cierra y los no elegidos se enteran. */
    private void closeIfDone(Vacancy v, Instant now) {
        if (applications.countByVacancyIdAndStatusIn(v.getId(), TO_CLOSE) > 0) return;
        v.setStatus(VacancyStatus.CLOSED);
        v.setUpdatedAt(now);
        for (VacancyApplication a : applications.findByVacancyIdAndStatusIn(v.getId(), List.of(ApplicationStatus.APPLIED))) {
            a.setStatus(ApplicationStatus.NOT_SELECTED);
            a.setUpdatedAt(now);
            publishChange(v, a, a.getStatus().name());
        }
    }

    private void move(VacancyApplication a, ApplicationStatus from, ApplicationStatus to, Instant now) {
        moveFrom(a, EnumSet.of(from), to, now);
    }

    private void moveFrom(VacancyApplication a, Set<ApplicationStatus> from, ApplicationStatus to, Instant now) {
        if (!from.contains(a.getStatus())) {
            throw new ApiException(ErrorVacancy.VAC008_INVALID_TRANSITION, a.getStatus(), to);
        }
        a.setStatus(to);
        a.setUpdatedAt(now);
    }

    private Vacancy find(UUID id) {
        return vacancies.findById(id).orElseThrow(() -> new ApiException(ErrorVacancy.VAC001_NOT_FOUND, id));
    }

    /** Vacante del empleador que llama (el administrador puede ver cualquiera). */
    private Vacancy owned(Caller caller, UUID id) {
        Vacancy v = find(id);
        if (!caller.isAdmin() && !v.getEmployerId().equals(caller.id())) {
            throw new ApiException(ErrorVacancy.VAC001_NOT_FOUND, id);
        }
        return v;
    }

    private VacancyApplication application(Vacancy v, UUID applicationId) {
        return applications.findById(applicationId)
                .filter(a -> a.getVacancyId().equals(v.getId()))
                .orElseThrow(() -> new ApiException(ErrorVacancy.VAC007_APPLICATION_NOT_FOUND, applicationId));
    }

    private VacancyApplication mine(Caller caller, UUID vacancyId) {
        if (!caller.isWorker()) throw new ApiException(ErrorVacancy.VAC012_NOT_WORKER);
        return applications.findByVacancyIdAndWorkerId(vacancyId, caller.id())
                .orElseThrow(() -> new ApiException(ErrorVacancy.VAC007_APPLICATION_NOT_FOUND, vacancyId));
    }

    private static Instant at(LocalDateTime local, String zone) {
        try {
            return local.atZone(ZoneId.of(zone)).toInstant();
        } catch (DateTimeException e) {
            throw new ApiException(ErrorVacancy.VAC003_TIMEZONE_NOT_ALLOWED, zone);
        }
    }

    // ================================================================== mensajes

    private Map<String, Object> vacancyData(Vacancy v) {
        Map<String, Object> data = new HashMap<>();
        data.put("vacancyId", v.getId().toString());
        data.put("employerId", v.getEmployerId().toString());
        data.put("title", v.getTitle());
        data.put("addressText", v.getAddressText());
        data.put("latitude", v.getLatitude());
        data.put("longitude", v.getLongitude());
        data.put("startsAt", v.getStartsAt().toString());
        data.put("endsAt", v.getEndsAt().toString());
        data.put("timezone", v.getTimezone());
        data.put("items", String.join(", ", v.getItems()));
        data.put("positions", v.getPositions());
        data.put("payMinor", v.getPayMinor());
        data.put("currency", v.getCurrency());
        data.put("moneyScale", v.getMoneyScale());
        return data;
    }

    /** {@code status} es la plantilla del aviso ({@code vacancy.<status>.<worker|employer>}). */
    private void publishChange(Vacancy v, VacancyApplication a, String status) {
        Map<String, Object> data = vacancyData(v);
        data.put("applicationId", a.getId().toString());
        data.put("workerId", a.getWorkerId().toString());
        data.put("workerName", userApi.findSummaryById(a.getWorkerId()).map(UserSummary::name).orElse(""));
        data.put("status", status);
        if (a.getCloseReason() != null) data.put("reason", a.getCloseReason().name());
        if (a.getJustification() != null) data.put("justification", a.getJustification());
        if (a.getPayoutMinor() != null) data.put("payoutMinor", a.getPayoutMinor());
        if (a.getPaidAmountMinor() != null) data.put("paidAmountMinor", a.getPaidAmountMinor());
        if (a.getPaymentNote() != null) data.put("paymentNote", a.getPaymentNote());
        events.publish(EventTypes.VACANCY_APPLICATION_CHANGED, v.getId().toString(), v.nextEventSeq(), data,
                v.getId().toString());
    }

    // ================================================================== respuestas

    private VacancyResponse toResponse(Vacancy v, Caller caller) {
        boolean owner = v.getEmployerId().equals(caller.id()) || caller.isAdmin();
        Double distance = null;
        ApplicationStatus mine = null;
        if (caller.isWorker()) {
            distance = workerApi.distanceKm(caller.id(), v.getLatitude(), v.getLongitude()).orElse(null);
            mine = applications.findByVacancyIdAndWorkerId(v.getId(), caller.id())
                    .map(VacancyApplication::getStatus).orElse(null);
        }
        VacancyTranslations.Content c = content(v);
        return new VacancyResponse(v.getId(), v.getEmployerId(),
                userApi.findSummaryById(v.getEmployerId()).map(UserSummary::name).orElse(null),
                c.title(), c.description(), v.getCategoryId(), c.items(), v.getAddressText(),
                v.getLatitude(), v.getLongitude(), v.getTimezone(), v.getStartsAt(), v.getEndsAt(), v.getPositions(),
                applications.countByVacancyIdAndStatusIn(v.getId(), SLOT), v.getPayMinor(), v.getCurrency(),
                v.getMoneyScale(), v.getStatus(), distance, mine,
                owner ? (Long) (long) applications.findByVacancyId(v.getId()).size() : null, v.getCreatedAt(),
                c.sourceLanguage(), c.translated());
    }

    /** Título, descripción e implementos en el idioma de la petición (traducidos si hace falta). */
    private VacancyTranslations.Content content(Vacancy v) {
        return translations.contentOf(v.getId(), v.getTitle(), v.getDescription(), v.getItems(), v.getSourceLocale(),
                LocaleContextHolder.getLocale());
    }

    private ApplicationResponse toResponse(VacancyApplication a, Vacancy v) {
        Optional<WorkerSummary> worker = workerApi.findSummaryById(a.getWorkerId());
        Optional<UserSummary> user = userApi.findSummaryById(a.getWorkerId());
        BigDecimal ratingAvg = worker.map(WorkerSummary::ratingAvg).orElse(BigDecimal.ZERO);
        int ratingCount = worker.map(WorkerSummary::ratingCount).orElse(0);
        boolean verified = user.map(UserSummary::verified).orElse(false);
        long completed = applications.countByWorkerIdAndStatus(a.getWorkerId(), ApplicationStatus.COMPLETED);
        double score = CandidateRanking.score(ratingAvg, ratingCount, completed, a.getDistanceKm(), verified);
        return new ApplicationResponse(a.getId(), v.getId(), content(v).title(), a.getWorkerId(),
                user.map(UserSummary::name).orElse(null), verified, ratingAvg, ratingCount, completed,
                a.getDistanceKm(), score, a.getStatus(), a.getMessage(), a.getSelectedAt(), a.getRespondedAt(),
                a.getClosedAt(), a.getCloseReason(), a.getJustification(), a.getPayoutMinor(), v.getCurrency(),
                v.getMoneyScale(), a.getPayoutStatus(), a.getPayoutMode(), a.getPaidAmountMinor(), a.getPaymentNote(),
                a.getPayoutRef(), a.getPayoutFailure(), a.getPaidAt(), v.getStartsAt(), v.getEndsAt(),
                a.getCreatedAt());
    }
}
