-- V1__init.sql
-- Инициализация БД для parking-reservation-service (status хранится как TEXT)
-- PostgreSQL

BEGIN;



-- 1) Таблица парковочных мест (parking_spot) с полями level и price
CREATE TABLE IF NOT EXISTS parking_spot (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    occupied BOOLEAN NOT NULL DEFAULT FALSE,
    zone TEXT,
    level TEXT,                                -- уровень/этаж/доп.инфо
    price NUMERIC(10,2) NOT NULL DEFAULT 0.00,-- цена за место
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2) Таблица бронирований (reservation) — статус как TEXT с CHECK
CREATE TABLE IF NOT EXISTS reservation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    spot_id BIGINT NOT NULL REFERENCES parking_spot(id) ON DELETE RESTRICT,
    spot_code TEXT NOT NULL,
    user_email TEXT NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE', -- хранится как текст
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_reservation_time CHECK (end_time > start_time),
    CONSTRAINT chk_reservation_status_values CHECK (status IN ('ACTIVE','CANCELLED','EXPIRED'))
);

-- 3) Индексы для ускорения выборок
CREATE INDEX IF NOT EXISTS idx_parking_spot_zone ON parking_spot(zone);
CREATE INDEX IF NOT EXISTS idx_parking_spot_level ON parking_spot(level);
CREATE INDEX IF NOT EXISTS idx_parking_spot_price ON parking_spot(price);
CREATE INDEX IF NOT EXISTS idx_reservation_user_email ON reservation(user_email);
CREATE INDEX IF NOT EXISTS idx_reservation_spot_id ON reservation(spot_id);
CREATE INDEX IF NOT EXISTS idx_reservation_status ON reservation(status);
CREATE INDEX IF NOT EXISTS idx_reservation_times ON reservation(start_time, end_time);

-- 4) Тестовые данные: парковочные места (с level и price)
INSERT INTO parking_spot (code, occupied, zone, level, price) VALUES
('A-101', false, 'A', 'L1', 100.00),
('A-102', false, 'A', 'L1', 110.00),
('A-103', false, 'A', 'L1', 90.00),
('A-104', false, 'A', 'L1', 95.00),
('B-201', false, 'B', 'L2', 120.00),
('B-202', false, 'B', 'L2', 120.00),
('B-203', false, 'B', 'L2', 115.00),
('C-301', false, 'C', 'L3', 80.00),
('C-302', false, 'C', 'L3', 85.00),
('D-401', false, 'D', 'L4', 130.00)
ON CONFLICT (code) DO NOTHING;

-- 5) Тестовые данные: бронирования
-- Явно приводим литералы времени к timestamptz (для ясности)
INSERT INTO reservation (spot_id, spot_code, user_email, start_time, end_time, status)
VALUES
(
  (SELECT id FROM parking_spot WHERE code='A-102'),
  'A-102',
  'alice@example.com',
  TIMESTAMPTZ '2025-09-21T09:00:00+00',
  TIMESTAMPTZ '2025-09-21T12:00:00+00',
  'ACTIVE'
),
(
  (SELECT id FROM parking_spot WHERE code='B-201'),
  'B-201',
  'bob@example.com',
  TIMESTAMPTZ '2025-09-21T08:00:00+00',
  TIMESTAMPTZ '2025-09-21T10:30:00+00',
  'ACTIVE'
),
(
  (SELECT id FROM parking_spot WHERE code='A-103'),
  'A-103',
  'carol@example.com',
  TIMESTAMPTZ '2025-09-20T14:00:00+00',
  TIMESTAMPTZ '2025-09-20T15:00:00+00',
  'EXPIRED'
)
ON CONFLICT DO NOTHING; -- безопасная вставка, если запись дубль (опционально)

-- 6) Обновление occupied для мест с ACTIVE бронированиями
UPDATE parking_spot
SET occupied = TRUE,
    updated_at = now()
WHERE id IN (SELECT spot_id FROM reservation WHERE status = 'ACTIVE');

COMMIT;
