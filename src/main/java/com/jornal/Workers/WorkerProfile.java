package com.jornal.Workers;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "worker_profiles")
@Getter
@Setter
@NoArgsConstructor
public class WorkerProfile {

    @Id
    @Column(name ="user_id")
    private UUID userId;

    @Column(length = 750)
    private String bio;

    @Column(nullable = false, name = "work_radius_km")
    private int workRadiusKm = 10;

    @Column(nullable = false)
    private boolean available = false;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount = 0;

    @ElementCollection
    @CollectionTable(name = "worker_profile_categories",
                     joinColumns = @JoinColumn(name ="user_id"))
    @Column(name = "category_id", nullable = false)
    private Set<UUID> categoryIds = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updateAt;
}
