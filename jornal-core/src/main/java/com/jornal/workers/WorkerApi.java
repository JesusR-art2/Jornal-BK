package com.jornal.workers;

import java.util.Optional;
import java.util.UUID;

public interface WorkerApi {
    Optional<WorkerSummary> findSummaryById(UUID workerId);
}
