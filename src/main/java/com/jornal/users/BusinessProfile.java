package com.jornal.users;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Perfil empresarial del rol BUSINESS: a su nombre e identificación fiscal se emiten los comprobantes. */
@Entity
@Table(name = "business_profiles")
@Getter
@Setter
@NoArgsConstructor
public class BusinessProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "legal_name", nullable = false, length = 160)
    private String legalName;

    @Column(name = "tax_id_type", nullable = false, length = 30)
    private String taxIdType;

    @Column(name = "tax_id_number", nullable = false, length = 30)
    private String taxIdNumber;

    @Column(name = "billing_address_id", nullable = false)
    private UUID billingAddressId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
