package com.jornal.users;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {

    boolean existsByTaxIdTypeAndTaxIdNumberAndUserIdNot(String taxIdType, String taxIdNumber, UUID userId);

    boolean existsByTaxIdTypeAndTaxIdNumber(String taxIdType, String taxIdNumber);

    boolean existsByBillingAddressId(UUID addressId);
}
