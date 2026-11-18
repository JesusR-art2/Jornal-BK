package com.jornal.workers;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record WorkerSummary(UUID workerId, String name, boolean verified, boolean available,
                            int workRadiusKm, Set<UUID> categoryIds, BigDecimal ratingAvg,
                            int ratingCount, String countryCode) {
}
