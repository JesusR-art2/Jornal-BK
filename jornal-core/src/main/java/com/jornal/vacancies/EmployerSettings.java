package com.jornal.vacancies;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Configuración del empleador. {@code autoPayout}: el pago al trabajador se hace solo al presionar «Finalizar». */
@Entity
@Table(name = "employer_settings")
@Getter
@Setter
@NoArgsConstructor
public class EmployerSettings {

    @Id
    @Column(name = "employer_id")
    private UUID employerId;

    @Column(name = "auto_payout", nullable = false)
    private boolean autoPayout;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
