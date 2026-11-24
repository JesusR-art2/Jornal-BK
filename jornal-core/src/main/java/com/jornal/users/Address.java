package com.jornal.users;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Dirección de un usuario. Los campos dependen del país y se validan con el address_schema del perfil. */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(length = 40)
    private String label;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @ElementCollection
    @CollectionTable(name = "address_fields", joinColumns = @JoinColumn(name = "address_id"))
    @MapKeyColumn(name = "field_key")
    @Column(name = "value", nullable = false)
    private Map<String, String> fields = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
