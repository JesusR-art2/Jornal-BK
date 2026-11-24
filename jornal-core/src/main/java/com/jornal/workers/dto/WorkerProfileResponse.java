package com.jornal.workers.dto;

import com.jornal.catalog.CategorySummary;
import com.jornal.workers.WorkerProfile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WorkerProfileResponse(UUID userId, String bio, int workRadiusKm,
                                    boolean available, BigDecimal ratingAvg,
                                    int ratingCount, List<CategorySummary> categories,
                                    Double baseLatitude, Double baseLongitude) {

    public static WorkerProfileResponse of(WorkerProfile p, List<CategorySummary> categories) {
        return new WorkerProfileResponse(p.getUserId(), p.getBio(), p.getWorkRadiusKm(),
                p.isAvailable(), p.getRatingAvg(), p.getRatingCount(), categories,
                p.getBaseLatitude(), p.getBaseLongitude());
    }
}
