package com.jornal.vacancies;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmployerSettingsRepository extends JpaRepository<EmployerSettings, UUID> {
}
