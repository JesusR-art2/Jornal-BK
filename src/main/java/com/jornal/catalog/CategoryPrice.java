package com.jornal.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Precio de una categoría en una región, en la unidad menor de la moneda (D8). */
@Entity
@Table(name = "category_prices")
@IdClass(CategoryPrice.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class CategoryPrice {

    @Id
    @Column(name = "category_id")
    private UUID categoryId;

    @Id
    @Column(name = "region_code", length = 2)
    private String regionCode;

    @Column(nullable = false, length = 10)
    private String unit;

    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public record Key(UUID categoryId, String regionCode) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
