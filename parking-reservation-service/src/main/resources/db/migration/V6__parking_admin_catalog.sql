BEGIN;

CREATE TABLE IF NOT EXISTS parking_lot (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    address TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS parking_zone (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lot_id BIGINT NOT NULL REFERENCES parking_lot(id) ON DELETE RESTRICT,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    level TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_parking_zone_lot_code UNIQUE (lot_id, code)
);

INSERT INTO parking_lot (code, name, address, active)
VALUES ('MAIN', 'Main Parking', 'Unknown', TRUE)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE parking_spot
    ADD COLUMN IF NOT EXISTS zone_id BIGINT;

WITH base_lot AS (
    SELECT id
    FROM parking_lot
    WHERE code = 'MAIN'
),
source_zones AS (
    SELECT DISTINCT
        COALESCE(NULLIF(BTRIM(zone), ''), 'GENERAL') AS code,
        COALESCE(NULLIF(BTRIM(zone), ''), 'General zone') AS name,
        NULLIF(BTRIM(level), '') AS level
    FROM parking_spot
)
INSERT INTO parking_zone (lot_id, code, name, level, active)
SELECT base_lot.id, source_zones.code, source_zones.name, source_zones.level, TRUE
FROM source_zones
CROSS JOIN base_lot
ON CONFLICT (lot_id, code) DO NOTHING;

UPDATE parking_spot ps
SET zone_id = pz.id,
    zone = pz.code,
    level = pz.level
FROM parking_zone pz
JOIN parking_lot pl ON pl.id = pz.lot_id
WHERE pl.code = 'MAIN'
  AND pz.code = COALESCE(NULLIF(BTRIM(ps.zone), ''), 'GENERAL')
  AND ps.zone_id IS NULL;

ALTER TABLE parking_spot
    ADD CONSTRAINT fk_parking_spot_zone
        FOREIGN KEY (zone_id) REFERENCES parking_zone(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_parking_spot_zone_id ON parking_spot(zone_id);
CREATE INDEX IF NOT EXISTS idx_parking_zone_lot_id ON parking_zone(lot_id);

COMMIT;
