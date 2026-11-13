package com.jornal.Workers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkerProfileRepository extends JpaRepository<WorkerProfile, UUID> {
}
