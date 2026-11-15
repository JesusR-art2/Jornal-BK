package com.jornal.users;

import com.jornal.users.enums.VerificationState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash",nullable = false)
    private String passwordHash;

    @Column(nullable= false)
    private String name;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_state", nullable = false)
    private VerificationState verificationState = VerificationState.PENDING;

    @Column(nullable= false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_in", nullable = false, updatable = false)
    private Instant createdIn;

    @UpdateTimestamp
    @Column(name = "updated_in", nullable = false)
    private Instant updatedIn;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(name = "terms_version")
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "privacy_version")
    private String privacyVersion;

    @Column(name = "privacy_accepted_at")
    private Instant privacyAcceptedAt;



}
