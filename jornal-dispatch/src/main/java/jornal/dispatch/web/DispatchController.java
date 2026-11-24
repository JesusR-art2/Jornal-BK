package jornal.dispatch.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jornal.dispatch.DispatchService;
import jornal.dispatch.LiveLocations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * API de asignación para las apps (por el gateway: {@code /subsystems/dispatch/...}), con el JWT del core.
 * El trabajador ve y responde ofertas y envía su posición; el cliente sigue a su trabajador.
 */
@RestController
public class DispatchController {

    private final DispatchService dispatch;

    public DispatchController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    public record LocationRequest(@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
                                  @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {
    }

    /** Ofertas pendientes del trabajador, con su plazo (cuenta regresiva en la app). */
    @GetMapping("/offers/me")
    @PreAuthorize("hasRole('WORKER')")
    public List<Map<String, Object>> myOffers(@AuthenticationPrincipal Jwt jwt) {
        return dispatch.offersOf(userId(jwt));
    }

    @PostMapping("/offers/{offerId}/accept")
    @PreAuthorize("hasRole('WORKER')")
    public Map<String, Object> accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID offerId) {
        return dispatch.accept(userId(jwt), offerId);
    }

    @PostMapping("/offers/{offerId}/reject")
    @PreAuthorize("hasRole('WORKER')")
    public Map<String, Object> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID offerId) {
        dispatch.reject(userId(jwt), offerId);
        return Map.of("offerId", offerId, "status", "REJECTED");
    }

    /** Posición en vivo del trabajador (cada 5 a 10 s desde la app). */
    @PutMapping("/locations/me")
    @PreAuthorize("hasRole('WORKER')")
    public LiveLocations.Position location(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LocationRequest body) {
        return dispatch.updateLocation(region(jwt), userId(jwt), body.latitude(), body.longitude());
    }

    /** Seguimiento del pedido: posición del trabajador y distancia al lugar (cliente o trabajador del pedido). */
    @GetMapping("/tracking/{requestId}")
    public Map<String, Object> tracking(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        return dispatch.tracking(userId(jwt), requestId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static String region(Jwt jwt) {
        return jwt.getClaimAsString("region").toLowerCase(Locale.ROOT);
    }
}
