package com.jornal.Workers.Listeners;

import com.jornal.Users.UserAccessChangedEvent;
import com.jornal.Workers.WorkerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WorkerEventListener {

    private final WorkerProfileRepository repository;

    @EventListener
    @Transactional
    public void onUserAccessChanged(UserAccessChangedEvent event){
        if (!event.eligible()) {
            repository.findById(event.userId())
                    .ifPresent(profile -> profile.setAvailable(false));
        }
    }
}
