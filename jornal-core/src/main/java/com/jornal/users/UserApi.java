package com.jornal.users;

import java.util.Optional;
import java.util.UUID;

public interface UserApi {
    Optional<UserSummary> findSummaryById(UUID id);

    /** Nombre e identificación fiscal (solo comercios) para el comprobante. */
    Optional<FiscalIdentity> fiscalIdentityOf(UUID id);
}
