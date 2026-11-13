package com.jornal.Workers.dto;

import com.jornal.Catalog.CategorySummary;
import com.jornal.Workers.WorkerProfile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WorkerProfileResponse(UUID userId, String bio, int workRadiusKm,
                                    boolean available, BigDecimal ratingAvg,
                                    int ratingCount, List<CategorySummary> categories) {

    public static WorkerProfileResponse of(WorkerProfile p, List<CategorySummary> categories) {
        return new WorkerProfileResponse(p.getUserId(), p.getBio(), p.getWorkRadiusKm(),
                p.isAvailable(), p.getRatingAvg(), p.getRatingCount(), categories);
    }

}
