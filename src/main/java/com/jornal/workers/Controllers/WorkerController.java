package com.jornal.workers.Controllers;

import com.jornal.workers.dto.AvailabilityRequest;
import com.jornal.workers.dto.PublicWorkerResponse;
import com.jornal.workers.dto.WorkerProfileRequest;
import com.jornal.workers.dto.WorkerProfileResponse;
import com.jornal.workers.WorkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/workers")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    @GetMapping
    @PreAuthorize("hasRole('WORKER')")
    public WorkerProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return workerService.getMine(userId(jwt));
    }

    @PutMapping
    @PreAuthorize("hasRole('WORKER')")
    public WorkerProfileResponse upsert(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody WorkerProfileRequest request){
        return workerService.upsert(userId(jwt), request);
    }

    @PatchMapping
    @PreAuthorize("hasRole('WORKER')")
    public WorkerProfileResponse availability(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody AvailabilityRequest request){
        return workerService.setAvailability(userId(jwt), request.available());
    }

    @GetMapping("/{userId}")
    public PublicWorkerResponse publicProfile(@PathVariable UUID userId){
        return workerService.getPublic(userId);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

}
