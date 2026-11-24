package com.jornal.workers.listeners;

import com.jornal.users.UserAccessChangedEvent;
import com.jornal.workers.WorkerProfileRepository;
import com.jornal.workers.WorkerSnapshots;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Si la cuenta deja de ser elegible (desverificada, desactivada o eliminada) se apaga la disponibilidad.
 * En cualquier cambio de acceso se publica la foto del trabajador para que asignación la actualice.
 */
@Component
@RequiredArgsConstructor
public class WorkerEventListener {

    private final WorkerProfileRepository repository;
    private final WorkerSnapshots snapshots;

    @EventListener
    @Transactional
    public void onUserAccessChanged(UserAccessChangedEvent event) {
        repository.findById(event.userId()).ifPresent(profile -> {
            if (!event.eligible()) {
                profile.setAvailable(false);
            }
            snapshots.publish(profile);
        });
    }
}
