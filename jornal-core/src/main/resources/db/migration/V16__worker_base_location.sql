-- F6: ubicación base del trabajador (desde dónde sale a trabajar). La posición en vivo va en Redis (dispatch).
ALTER TABLE worker_profiles
    ADD COLUMN base_latitude  DOUBLE PRECISION CHECK (base_latitude BETWEEN -90 AND 90),
    ADD COLUMN base_longitude DOUBLE PRECISION CHECK (base_longitude BETWEEN -180 AND 180);
