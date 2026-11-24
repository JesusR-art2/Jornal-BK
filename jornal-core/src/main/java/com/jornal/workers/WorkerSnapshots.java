package com.jornal.workers;

import com.jornal.shared.IntegrationEvents;
import com.jornal.users.Rol;
import com.jornal.users.UserApi;
import com.jornal.users.UserSummary;
import jornal.common.events.EventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Publica la foto completa del trabajador ({@code jornal.worker.updated.v1}) cada vez que cambia algo que importa
 * para la asignación: perfil, disponibilidad, verificación o estado de la cuenta. Asignación guarda la última
 * versión (la versión es el instante en milisegundos).
 */
@Component
@RequiredArgsConstructor
public class WorkerSnapshots {

    private final IntegrationEvents events;
    private final UserApi userApi;
    private final Clock clock;

    public void publish(WorkerProfile profile) {
        Optional<UserSummary> user = userApi.findSummaryById(profile.getUserId());
        boolean eligible = user.map(u -> u.rol() == Rol.WORKER && u.verified()).orElse(false);

        Map<String, Object> data = new HashMap<>();
        data.put("workerId", profile.getUserId().toString());
        data.put("name", user.map(UserSummary::name).orElse(null));
        data.put("eligible", eligible);
        data.put("available", profile.isAvailable() && eligible);
        data.put("categoryIds", profile.getCategoryIds().stream().map(Object::toString).toList());
        data.put("workRadiusKm", profile.getWorkRadiusKm());
        data.put("baseLatitude", profile.getBaseLatitude());
        data.put("baseLongitude", profile.getBaseLongitude());
        data.put("ratingAvg", profile.getRatingAvg().doubleValue());
        data.put("ratingCount", profile.getRatingCount());
        events.publish(EventTypes.WORKER_UPDATED, profile.getUserId().toString(), clock.millis(), data, null);
    }
}
