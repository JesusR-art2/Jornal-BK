package com.jornal.vacancies.controllers;

import com.jornal.shared.PageResponse;
import com.jornal.vacancies.ApplicationStatus;
import com.jornal.vacancies.PayoutStatus;
import com.jornal.vacancies.VacancyStatus;
import com.jornal.vacancies.dto.*;
import com.jornal.vacancies.services.VacancyService;
import com.jornal.vacancies.services.VacancyService.Caller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Vacantes de trabajo temporal. Empleador = APPLICANT o BUSINESS; trabajador = WORKER.
 */
@RestController
@RequestMapping("/vacancies")
@RequiredArgsConstructor
public class VacancyController {

    private static final String EMPLOYER = "hasAnyRole('APPLICANT','BUSINESS')";
    private static final String EMPLOYER_OR_ADMIN = "hasAnyRole('APPLICANT','BUSINESS','ADMIN')";
    private static final String WORKER = "hasRole('WORKER')";

    private final VacancyService vacancies;

    // ------------------------------------------------------------------ empleador

    /** Publica la vacante; los trabajadores cercanos reciben un aviso emergente. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EMPLOYER)
    public VacancyResponse publish(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VacancyDraft draft) {
        return vacancies.publish(caller(jwt), draft);
    }

    /** Vacantes propias. {@code pendingClosure=true}: ya terminadas, con personas por finalizar o cancelar. */
    @GetMapping("/mine")
    @PreAuthorize(EMPLOYER)
    public PageResponse<VacancyResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) VacancyStatus status,
                                              @RequestParam(defaultValue = "false") boolean pendingClosure,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return vacancies.mine(caller(jwt), status, pendingClosure, pageable(page, size, "endsAt"));
    }

    /** Candidatos ordenados por puntaje (mejor primero). {@code status=CONFIRMED}: quienes se comprometieron. */
    @GetMapping("/{id}/applications")
    @PreAuthorize(EMPLOYER_OR_ADMIN)
    public List<ApplicationResponse> candidates(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                @RequestParam(required = false) ApplicationStatus status) {
        return vacancies.candidates(caller(jwt), id, status);
    }

    /** Contrata a varias personas a la vez (hasta completar los cupos). */
    @PostMapping("/{id}/selections")
    @PreAuthorize(EMPLOYER)
    public List<ApplicationResponse> select(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                            @Valid @RequestBody SelectRequest body) {
        return vacancies.select(caller(jwt), id, body.applicationIds());
    }

    /** Botón «Finalizar»: la persona terminó su trabajo y pasa al formato de pago. */
    @PostMapping("/{id}/applications/{applicationId}/complete")
    @PreAuthorize(EMPLOYER)
    public ApplicationResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @PathVariable UUID applicationId) {
        return vacancies.complete(caller(jwt), id, applicationId);
    }

    /** Botón «Cancelar»: no fue o dejó el trabajo a medias; sin pago y con justificación. */
    @PostMapping("/{id}/applications/{applicationId}/cancel")
    @PreAuthorize(EMPLOYER)
    public ApplicationResponse cancelWork(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @PathVariable UUID applicationId,
                                          @Valid @RequestBody CancelWorkRequest body) {
        return vacancies.cancelWork(caller(jwt), id, applicationId, body);
    }

    /** Pago a mano: {@code {amountMinor, justification}}; la justificación es obligatoria si paga menos. */
    @PostMapping("/{id}/applications/{applicationId}/paid")
    @PreAuthorize(EMPLOYER)
    public ApplicationResponse markPaid(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @PathVariable UUID applicationId,
                                        @Valid @RequestBody ManualPaymentRequest body) {
        return vacancies.markPaid(caller(jwt), id, applicationId, body);
    }

    /** Configuración del empleador: pago automático al finalizar (activo) o a mano (inactivo). */
    @GetMapping("/settings")
    @PreAuthorize(EMPLOYER)
    public EmployerSettingsDto settings(@AuthenticationPrincipal Jwt jwt) {
        return vacancies.settings(caller(jwt));
    }

    @PutMapping("/settings")
    @PreAuthorize(EMPLOYER)
    public EmployerSettingsDto updateSettings(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody EmployerSettingsDto body) {
        return vacancies.updateSettings(caller(jwt), body);
    }

    /** Retira la vacante antes de que empiece. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize(EMPLOYER)
    public VacancyResponse cancelVacancy(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return vacancies.cancelVacancy(caller(jwt), id);
    }

    /** Formato de pago: personas que terminaron trabajos de mis vacantes ({@code status} PENDING o PAID). */
    @GetMapping("/payouts")
    @PreAuthorize(EMPLOYER)
    public PageResponse<ApplicationResponse> payouts(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(required = false) PayoutStatus status,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return vacancies.payouts(caller(jwt), status, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)));
    }

    // ------------------------------------------------------------------ trabajador

    /** Listado de vacantes abiertas, la más próxima a empezar primero; {@code maxDistanceKm} filtra por cercanía. */
    @GetMapping
    public PageResponse<VacancyResponse> open(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) Double maxDistanceKm,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return vacancies.open(caller(jwt), maxDistanceKm,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("startsAt").ascending()));
    }

    /** Detalle: lugar, implementos, día y hora y qué hay que hacer. */
    @GetMapping("/{id}")
    public VacancyResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return vacancies.get(caller(jwt), id);
    }

    @PostMapping("/{id}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WORKER)
    public ApplicationResponse apply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody(required = false) ApplyRequest body) {
        return vacancies.apply(caller(jwt), id, body == null ? null : body.message());
    }

    /** Botón del aviso «¿Puedes asistir?». */
    @PostMapping("/{id}/attendance")
    @PreAuthorize(WORKER)
    public ApplicationResponse attendance(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody AttendanceRequest body) {
        return vacancies.attendance(caller(jwt), id, body.attending());
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize(WORKER)
    public ApplicationResponse withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return vacancies.withdraw(caller(jwt), id);
    }

    /** Mis postulaciones (a dónde debo ir y el estado de mi pago). */
    @GetMapping("/applications/mine")
    @PreAuthorize(WORKER)
    public PageResponse<ApplicationResponse> myApplications(@AuthenticationPrincipal Jwt jwt,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return vacancies.myApplications(caller(jwt), pageable(page, size, "createdAt"));
    }

    static Caller caller(Jwt jwt) {
        return new Caller(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"));
    }

    static PageRequest pageable(int page, int size, String sortBy) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by(sortBy).descending());
    }
}
