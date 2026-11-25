package com.jornal.workers;


import com.jornal.catalog.CatalogApi;
import com.jornal.catalog.CategorySummary;
import com.jornal.shared.ApiException;
import com.jornal.users.Rol;
import com.jornal.users.UserApi;
import com.jornal.users.UserSummary;
import com.jornal.workers.dto.PublicWorkerResponse;
import com.jornal.workers.dto.WorkerProfileRequest;
import com.jornal.workers.dto.WorkerProfileResponse;
import com.jornal.workers.enums.ErrorWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkerService implements WorkerApi {

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public long countAvailable() {
        return repository.countByAvailableTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NearbyWorker> findNearby(double latitude, double longitude, int limit) {
        return repository.findWithinRadius(latitude, longitude, Math.clamp(limit, 1, 5000)).stream()
                .map(row -> new NearbyWorker(UUID.fromString(row[0].toString()),
                        Math.round(((Number) row[1]).doubleValue() / 100.0) / 10.0))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Double> distanceKm(UUID workerId, double latitude, double longitude) {
        return repository.findById(workerId)
                .filter(p -> p.getBaseLatitude() != null && p.getBaseLongitude() != null)
                .map(p -> haversineKm(p.getBaseLatitude(), p.getBaseLongitude(), latitude, longitude));
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        double km = 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(km * 10.0) / 10.0;
    }

    private final WorkerProfileRepository repository;
    private final WorkerProfileMapper mapper;
    private final UserApi userApi;
    private final CatalogApi catalogApi;
    private final WorkerSnapshots snapshots;


    @Transactional(readOnly = true)
    public WorkerProfileResponse getMine(UUID userId){
        requireWorker(userId);
        return toResponse(getOrThrow(userId));
    }

    @Transactional
    public WorkerProfileResponse upsert(UUID userId, WorkerProfileRequest request){
        requireWorker(userId);
        List<CategorySummary> categories = resolveCategories(request.categoryIds());

        WorkerProfile profile = repository.findById(userId).orElseGet(() -> {
            WorkerProfile created = new WorkerProfile();
            created.setUserId(userId);
            return created;
        });

        mapper.update(request, profile);
        profile.getCategoryIds().clear();
        categories.forEach(c -> profile.getCategoryIds().add(c.id()));

        WorkerProfile saved = repository.save(profile);
        snapshots.publish(saved);
        return WorkerProfileResponse.of(saved, categories);
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
            if (profile.getBaseLatitude() == null || profile.getBaseLongitude() == null) {
                throw new ApiException(ErrorWorker.WRK006_LOCATION_REQUIRED);
            }
        }
        profile.setAvailable(available);
        snapshots.publish(profile);
        return WorkerProfileResponse.of(profile, categories);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkerSummary> findSummaryById(UUID workerId) {
        Optional<UserSummary> user = userApi.findSummaryById(workerId)
                .filter(u -> u.rol() == Rol.WORKER);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        return repository.findById(workerId)
                .map(profile -> new WorkerSummary(
                        profile.getUserId(), user.get().name(), user.get().verified(),
                        profile.isAvailable(), profile.getWorkRadiusKm(),
                        Set.copyOf(profile.getCategoryIds()), profile.getRatingAvg(),
                        profile.getRatingCount(), user.get().countryCode()));
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
