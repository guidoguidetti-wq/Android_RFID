-- Migration: Aggiunge nuovi campi alla tabella inventories
-- Data: 2026-01-16
-- Descrizione: Supporto per inventario da checklist e da giacenza RFID

-- Colonne per zone multiple (separate da virgola)
ALTER TABLE inventories ADD COLUMN IF NOT EXISTS inv_zones VARCHAR(200);

-- Colonne per Transfer Results - Detected Items
ALTER TABLE inventories ADD COLUMN IF NOT EXISTS inv_det_place VARCHAR(50);
ALTER TABLE inventories ADD COLUMN IF NOT EXISTS inv_det_zone VARCHAR(50);

-- Colonne per Transfer Results - Missed Items
ALTER TABLE inventories ADD COLUMN IF NOT EXISTS inv_mis_place VARCHAR(50);
ALTER TABLE inventories ADD COLUMN IF NOT EXISTS inv_mis_zone VARCHAR(50);

-- Verifica che inv_chk_id esista (dovrebbe già esistere)
ALTER TABLE inventories ADD COLUMN IF NOT EXISTS inv_chk_id INTEGER DEFAULT 0;

-- Colonne per Inventario da Giacenza RFID
ALTER TABLE inventories ADD COLUMN IF NOT EXISTS inv_last BOOLEAN DEFAULT false;
ALTER TABLE inventories ADD COLUMN IF NOT EXISTS inv_last_place VARCHAR(50);
ALTER TABLE inventories ADD COLUMN IF NOT EXISTS inv_last_zones VARCHAR(200);
