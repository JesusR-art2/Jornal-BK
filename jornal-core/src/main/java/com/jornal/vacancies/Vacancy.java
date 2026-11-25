package com.jornal.vacancies;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Vacante de trabajo temporal publicada por una empresa o empleador. Pago por persona en unidad menor (D8). */
@Entity
@Table(name = "vacancies")
@Getter
@Setter
@NoArgsConstructor
public class Vacancy {

    @Id
    private UUID id;

    @Column(name = "employer_id", nullable = false)
    private UUID employerId;

    @Column(name = "employer_role", nullable = false)
    private String employerRole;

    @Column(name = "region_code", nullable = false, length = 2)
    private String regionCode;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "address_text", nullable = false)
    private String addressText;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private String timezone;

    /** Idioma en que el empleador escribió la vacante (BCP 47); el contenido se traduce al idioma de quien la ve. */
    @Column(name = "source_locale")
    private String sourceLocale;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int positions;

    @Column(name = "pay_minor", nullable = false)
    private long payMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "money_scale", nullable = false)
    private int moneyScale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VacancyStatus status;

    @Column(name = "event_seq", nullable = false)
    private long eventSeq;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    /** Implementos que debe llevar el trabajador. */
    @ElementCollection
    @CollectionTable(name = "vacancy_items", joinColumns = @JoinColumn(name = "vacancy_id"))
    @OrderColumn(name = "position")
    @Column(name = "item", nullable = false)
    private List<String> items = new ArrayList<>();

    public long nextEventSeq() {
        return ++eventSeq;
    }

    /** El trabajo ya terminó (hora de fin puesta por el empleador). */
    public boolean hasEnded(Instant now) {
        return !now.isBefore(endsAt);
    }
}
