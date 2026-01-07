-- =====================================================
-- RFID Inventory Management - Database Schema
-- =====================================================
-- Tabelle per gestione utenti, inventari e items scannerizzati
-- Creato: 2026-01-05
-- =====================================================

-- Tabella Users (autenticazione)
CREATE TABLE IF NOT EXISTS "Users" (
  user_id           VARCHAR(50)  PRIMARY KEY,
  user_password     TEXT         NOT NULL,           -- Plain text per semplicità
  user_name         TEXT,
  user_email        VARCHAR(255) UNIQUE,
  user_role         VARCHAR(50)  DEFAULT 'operator',
  user_active       BOOLEAN      DEFAULT true,
  usr_def_place     VARCHAR(50)  NOT NULL,           -- Place predefinito utente
  date_created      TIMESTAMP    DEFAULT NOW(),
  date_lastlogin    TIMESTAMP,
  CONSTRAINT fk_user_place FOREIGN KEY (usr_def_place) REFERENCES "Places"(place_id)
);

-- Tabella Inventories (sessioni inventario)
CREATE TABLE IF NOT EXISTS "Inventories" (
  inv_id            VARCHAR(50)  PRIMARY KEY,        -- Es: INV-2026-001
  inv_name          TEXT         NOT NULL,
  inv_note          TEXT,
  inv_state         VARCHAR(20)  DEFAULT 'open',     -- 'open', 'closed'
  inv_place_id      VARCHAR(50)  NOT NULL,
  inv_user          VARCHAR(50)  NOT NULL,
  inv_start_date    TIMESTAMP    DEFAULT NOW(),
  inv_end_date      TIMESTAMP,
  CONSTRAINT fk_inv_place FOREIGN KEY (inv_place_id) REFERENCES "Places"(place_id),
  CONSTRAINT fk_inv_user FOREIGN KEY (inv_user) REFERENCES "Users"(user_id)
);

-- Tabella Inventory_Items (tag scannerizzati per inventario)
CREATE TABLE IF NOT EXISTS "Inventory_Items" (
  invitem_id        SERIAL       PRIMARY KEY,
  inventory_id      VARCHAR(50)  NOT NULL,
  item_epc          VARCHAR(50)  NOT NULL,
  scan_timestamp    TIMESTAMP    DEFAULT NOW(),
  CONSTRAINT fk_invitem_inv FOREIGN KEY (inventory_id) REFERENCES "Inventories"(inv_id) ON DELETE CASCADE,
  CONSTRAINT fk_invitem_epc FOREIGN KEY (item_epc) REFERENCES "Items"(item_id),
  CONSTRAINT unique_inv_epc UNIQUE (inventory_id, item_epc)  -- Previene duplicati
);

-- Indexes per performance
CREATE INDEX IF NOT EXISTS idx_users_place ON "Users"(usr_def_place);
CREATE INDEX IF NOT EXISTS idx_inv_state_place ON "Inventories"(inv_state, inv_place_id);
CREATE INDEX IF NOT EXISTS idx_inv_user ON "Inventories"(inv_user);
CREATE INDEX IF NOT EXISTS idx_invitems_inv ON "Inventory_Items"(inventory_id);
CREATE INDEX IF NOT EXISTS idx_invitems_epc ON "Inventory_Items"(item_epc);

-- =====================================================
-- Fine Schema
-- =====================================================
