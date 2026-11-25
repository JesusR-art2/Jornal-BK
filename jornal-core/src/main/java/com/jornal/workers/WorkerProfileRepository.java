package com.jornal.workers;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkerProfileRepository extends JpaRepository<WorkerProfile, UUID> {

    long countByAvailableTrue();

    /**
     * Trabajadores con ubicación base cuyo radio de trabajo alcanza el punto, el más cercano primero.
     * Cada fila: id del trabajador y distancia en metros (PostGIS).
     */
    @Query(value = """
            SELECT CAST(p.user_id AS VARCHAR) AS id,
                   ST_Distance(ST_SetSRID(ST_MakePoint(p.base_longitude, p.base_latitude), 4326)::geography,
                               ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) AS meters
              FROM worker_profiles p
             WHERE p.base_latitude IS NOT NULL AND p.base_longitude IS NOT NULL
               AND ST_DWithin(ST_SetSRID(ST_MakePoint(p.base_longitude, p.base_latitude), 4326)::geography,
                              ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                              p.work_radius_km * 1000.0)
             ORDER BY meters
             LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findWithinRadius(@Param("lat") double lat, @Param("lon") double lon, @Param("limit") int limit);
}
