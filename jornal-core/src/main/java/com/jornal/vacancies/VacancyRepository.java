package com.jornal.vacancies;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface VacancyRepository extends JpaRepository<Vacancy, UUID> {

    Page<Vacancy> findByEmployerId(UUID employerId, Pageable pageable);

    Page<Vacancy> findByEmployerIdAndStatusIn(UUID employerId, Collection<VacancyStatus> statuses, Pageable pageable);

    /** Vacantes que aún reciben postulaciones (abiertas y sin empezar). */
    @Query("select v from Vacancy v where v.status = com.jornal.vacancies.VacancyStatus.OPEN and v.startsAt > :now")
    Page<Vacancy> findOpen(@Param("now") Instant now, Pageable pageable);

    /** Vacantes del empleador cuyo trabajo ya terminó y tienen personas por cerrar (finalizar o cancelar). */
    @Query("""
            select v from Vacancy v
             where v.employerId = :employer and v.endsAt <= :now
               and exists (select a.id from VacancyApplication a
                            where a.vacancyId = v.id
                              and a.status in (com.jornal.vacancies.ApplicationStatus.SELECTED,
                                               com.jornal.vacancies.ApplicationStatus.CONFIRMED))
            """)
    Page<Vacancy> findPendingClosure(@Param("employer") UUID employer, @Param("now") Instant now, Pageable pageable);
}
