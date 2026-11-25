package com.jornal.vacancies;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VacancyApplicationRepository extends JpaRepository<VacancyApplication, UUID> {

    List<VacancyApplication> findByVacancyId(UUID vacancyId);

    List<VacancyApplication> findByVacancyIdAndStatusIn(UUID vacancyId, Collection<ApplicationStatus> statuses);

    Optional<VacancyApplication> findByVacancyIdAndWorkerId(UUID vacancyId, UUID workerId);

    Page<VacancyApplication> findByWorkerId(UUID workerId, Pageable pageable);

    long countByVacancyIdAndStatusIn(UUID vacancyId, Collection<ApplicationStatus> statuses);

    /** Trabajos que el trabajador terminó y finalizó el empleador (historial para el ranking). */
    long countByWorkerIdAndStatus(UUID workerId, ApplicationStatus status);

    /** Formato de pago: personas que terminaron trabajos de este empleador, por estado de pago. */
    @Query("""
            select a from VacancyApplication a, Vacancy v
             where a.vacancyId = v.id and v.employerId = :employer
               and a.status = com.jornal.vacancies.ApplicationStatus.COMPLETED
               and (:status is null or a.payoutStatus = :status)
             order by a.closedAt desc
            """)
    Page<VacancyApplication> findPayouts(@Param("employer") UUID employer, @Param("status") PayoutStatus status,
                                         Pageable pageable);
}
