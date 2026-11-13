package com.jornal.Workers;


import com.jornal.Catalog.CatalogApi;
import com.jornal.Catalog.CategorySummary;
import com.jornal.Shared.ApiException;
import com.jornal.Users.Rol;
import com.jornal.Users.UserApi;
import com.jornal.Users.UserSummary;
import com.jornal.Workers.DTO.PublicWorkerResponse;
import com.jornal.Workers.DTO.WorkerProfileRequest;
import com.jornal.Workers.DTO.WorkerProfileResponse;
import com.jornal.Workers.enums.ErrorWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkerService {

    private final WorkerProfileRepository repository;
    private final WorkerProfileMapper mapper;
    private final UserApi userApi;
    private final CatalogApi catalogApi;


    @Transactional(readOnly = true)
    public WorkerProfileResponse getMine(UUID userId){
        requireWorker(userId);
        return toResponse(getOrThrow(userId));
    }

    @Transactional
    public WorkerProfileResponse upsert(UUID userId, WorkerProfileRequest request){
        requireWorker(userId);
        List<CategorySummary> categories = resolveCategories(request.categorysIds());

        WorkerProfile profile = repository.findById(userId).orElseGet(() -> {
            WorkerProfile created = new WorkerProfile();
            created.setUserId(userId);
            return created;
        });

        mapper.update(request, profile);
        profile.getCategoryIds().clear();
        categories.forEach(c -> profile.getCategoryIds().add(c.id()));

        return WorkerProfileResponse.of(repository.save(profile), categories);
    }

    @Transactional
    public WorkerProfileResponse setAvailability(UUID userId, boolean available){
        UserSummary user = requireWorker(userId);
        WorkerProfile profile = getOrThrow(userId);
        List<CategorySummary> categories = catalogApi.findActiveByIds(profile.getCategoryIds());

        if (available){
            if(!user.verified()){
                throw new ApiException(ErrorWorker.WRK004_NOT_VERIFIED);
            }
            if(categories.isEmpty()) {
                throw new ApiException(ErrorWorker.WRK005_CATEGORY_REQUIRED);
            }
        }
        profile.setAvailable(available);
        return WorkerProfileResponse.of(profile, categories);
    }

    @Transactional(readOnly = true)
    public PublicWorkerResponse getPublic(UUID userId) {
        // Solo se muestran trabajadores activos y verificados
        UserSummary user = userApi.findSummaryById(userId)
                .filter(u -> u.rol() == Rol.WORKER && u.verified())
                .orElseThrow(() -> new ApiException(ErrorWorker.WRK001_PROFILE_NOT_FOUND, userId));
        WorkerProfile profile = getOrThrow(userId);

        return PublicWorkerResponse.of(user, profile,
                catalogApi.findActiveByIds(profile.getCategoryIds()));
    }

    private UserSummary requireWorker(UUID userId) {
        return userApi.findSummaryById(userId)
                .filter(u -> u.rol() == Rol.WORKER)
                .orElseThrow(() -> new ApiException(ErrorWorker.WRK002_NOT_A_WORKER));
    }

    private WorkerProfile getOrThrow(UUID userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorWorker.WRK001_PROFILE_NOT_FOUND, userId));
    }

    private List<CategorySummary> resolveCategories(Set<UUID> ids) {
        List<CategorySummary> found = catalogApi.findActiveByIds(ids);
        if (found.size() != ids.size()) {
            throw new ApiException(ErrorWorker.WRK003_INVALID_CATEGORIES);
        }
        return found;
    }

    private WorkerProfileResponse toResponse(WorkerProfile profile) {
        return WorkerProfileResponse.of(profile,
                catalogApi.findActiveByIds(profile.getCategoryIds()));
    }
}
