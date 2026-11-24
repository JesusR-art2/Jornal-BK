package com.jornal.workers.dto;

import com.jornal.catalog.CategorySummary;
import com.jornal.users.UserSummary;
import com.jornal.workers.WorkerProfile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicWorkerResponse(UUID userId, String name, String bio,
                                   BigDecimal ratingAvg, int ratingCount,
                                   List<CategorySummary> categories) {

    public static PublicWorkerResponse of(UserSummary user, WorkerProfile p,
                                          List<CategorySummary> categories) {
        return new PublicWorkerResponse(p.getUserId(), user.name(), p.getBio(),
                p.getRatingAvg(), p.getRatingCount(), categories);
    }

}
