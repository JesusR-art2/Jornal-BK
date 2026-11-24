package com.jornal.catalog;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "category_translations")
@Getter
@Setter
@NoArgsConstructor
public class CategoryTranslation {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

}
