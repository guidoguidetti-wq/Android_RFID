# Database Schema - RFID Application

## Database Info
- Host: 57.129.5.234:5432
- Database: rfid_db
- User: rfidmanager

## Tables Overview

### Items (33 rows) - EPC censiti
Contiene l'elenco di tutti gli EPC (tag RFID) censiti nel sistema.

```sql
item_id         VARCHAR(50)  PRIMARY KEY  -- EPC del tag RFID
tid             VARCHAR(50)               -- TID (Tag Identifier) del chip
date_creation   TIMESTAMP                 -- Data prima registrazione
date_lastseen   TIMESTAMP                 -- Data ultima lettura
place_last      VARCHAR(50)               -- Ultimo luogo fisico (FK -> Places)
zone_last       VARCHAR(50)               -- Ultima zona logica (FK -> Zones)
item_product_id VARCHAR(50)               -- Prodotto associato (FK -> Products)
nfc_uid         TEXT                      -- UID NFC se presente
```

### Movements (352 rows) - Storico letture
Registra tutte le letture RFID eseguite su ogni tag.

```sql
mov_id          INTEGER      PRIMARY KEY  -- ID movimento
mov_epc         VARCHAR(50)  NOT NULL     -- EPC letto
mov_dest_place  VARCHAR(50)               -- Luogo di destinazione
mov_dest_zone   VARCHAR(50)               -- Zona di destinazione
mov_timestamp   TIMESTAMP                 -- Timestamp lettura
mov_unexpected  BOOLEAN      DEFAULT false -- Lettura inaspettata
mov_readscount  INTEGER                   -- Numero di letture consecutive
mov_rssiavg     NUMERIC                   -- RSSI medio
mov_user        VARCHAR(80)               -- Utente che ha effettuato lettura
mov_reader      VARCHAR                   -- ID del lettore RFID
mov_readerpw    VARCHAR                   -- Potenza del lettore
mov_notes       TEXT                      -- Note
mov_ref         VARCHAR(80)               -- Riferimento
mov_antpw1      INTEGER                   -- Potenza antenna 1
mov_antpw2      INTEGER                   -- Potenza antenna 2
mov_antpw3      INTEGER                   -- Potenza antenna 3
mov_antpw4      INTEGER                   -- Potenza antenna 4
mov_antenna     INTEGER                   -- Antenna utilizzata
```

### Places (2 rows) - Luoghi fisici
Definisce i luoghi fisici in cui vengono letti i tag.

```sql
place_id        VARCHAR(50)  PRIMARY KEY  -- ID luogo
place_name      TEXT                      -- Nome del luogo
place_type      VARCHAR(50)               -- Tipologia luogo
```

### Zones (3 rows) - Zone logiche
Definisce le zone logiche di classificazione.

```sql
zone_id         VARCHAR(50)  PRIMARY KEY  -- ID zona
zone_name       TEXT                      -- Nome zona
zone_type       VARCHAR(50)               -- Tipologia zona
```

### Products (16 rows) - Anagrafica prodotti
Anagrafica dei prodotti a cui gli items sono associati.

```sql
product_id      VARCHAR      PRIMARY KEY  -- ID prodotto
fld01-fld10     TEXT                      -- Campi dati generici 1-10
fldd01-fldd10   TEXT                      -- Campi dati descrittivi 1-10
```

### Products_labels (2 rows) - Etichette campi prodotti
Metadati che descrivono i campi della tabella Products.

```sql
pr_fld          VARCHAR      PRIMARY KEY  -- Nome campo (es. "fld01")
pr_lab          VARCHAR                   -- Label visualizzata
pr_des          TEXT                      -- Descrizione campo
```

### people (2 rows) - Persone
Registro persone associate a tag RFID/NFC.

```sql
id_people       INTEGER      PRIMARY KEY  -- ID persona
epc             VARCHAR(50)               -- EPC associato
uid             VARCHAR(50)               -- UID NFC
name            TEXT                      -- Nome
role            TEXT                      -- Ruolo
image           TEXT                      -- Path immagine
permission      INTEGER      DEFAULT 0    -- Livello permessi
```

## Relationships

```
Products (product_id)
    ↑
    |
Items (item_product_id) ← Places (place_id) ← Movements (mov_dest_place)
    ↑                     ← Zones (zone_id)  ← Movements (mov_dest_zone)
    |
Movements (mov_epc)
```

## Workflow Tipico RFID

1. **Setup iniziale**:
   - Configurare Places (luoghi fisici)
   - Configurare Zones (zone logiche)
   - Caricare Products (anagrafica prodotti)

2. **Registrazione Item**:
   - Lettura nuovo EPC
   - INSERT in Items con item_id = EPC letto
   - Associare item_product_id

3. **Lettura RFID**:
   - Lettura EPC con RFD8500
   - INSERT in Movements con tutti i dettagli
   - UPDATE Items: date_lastseen, place_last, zone_last

4. **Query**:
   - Storico movimento item: SELECT * FROM Movements WHERE mov_epc = ?
   - Ultima posizione: SELECT place_last, zone_last FROM Items WHERE item_id = ?
   - Prodotto associato: JOIN Items-Products
