package com.jornal.Users;

import java.util.Optional;
import java.util.UUID;

public interface UserApi {
    Optional<UserSummary> findSummaryById(UUID id);
}
