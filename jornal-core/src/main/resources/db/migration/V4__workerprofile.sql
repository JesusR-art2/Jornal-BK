CREATE TABLE worker_profiles(
    user_id          UUID PRIMARY KEY,
    bio              VARCHAR(750),
    work_radius_km   INTEGER        NOT NULL DEFAULT 10 CHECK ( work_radius_km between 1 and 100),
    available        BOOLEAN        NOT NULL DEFAULT FALSE,
    rating_avg       NUMERIC(3,2)   NOT NULL DEFAULT 0,
    rating_count     INTEGER        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE worker_profile_categories (
                                           user_id     UUID NOT NULL REFERENCES worker_profiles (user_id) ON DELETE CASCADE,
                                           category_id UUID NOT NULL,                        -- sin FK (módulo Catalog)
                                           PRIMARY KEY (user_id, category_id)
);

CREATE INDEX idx_wpc_category ON worker_profile_categories (category_id);