-- =====================================================
-- RFID Inventory Management - Seed Data
-- =====================================================
-- Dati iniziali per testing
-- Creato: 2026-01-05
-- =====================================================

-- Inserire utenti di test
INSERT INTO "Users" (user_id, user_password, user_name, user_email, usr_def_place) VALUES
('admin', 'admin123', 'Administrator', 'admin@rfid.com', 'WHS'),
('operator1', 'pass123', 'Operator One', 'op1@rfid.com', 'WHS')
ON CONFLICT (user_id) DO NOTHING;

-- Inserire inventario di test
INSERT INTO "Inventories" (inv_id, inv_name, inv_note, inv_state, inv_place_id, inv_user) VALUES
('INV-2026-001', 'Test Pic 1', 'Inventario di prova', 'open', 'WHS', 'admin')
ON CONFLICT (inv_id) DO NOTHING;

-- =====================================================
-- Fine Seed Data
-- =====================================================
