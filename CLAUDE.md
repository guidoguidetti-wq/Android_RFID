# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Applicazione Android per lettura RFID con lettore Zebra RFD8500 connesso via Bluetooth, con backend Node.js/Express e database PostgreSQL **remoto già esistente**.

**Architettura**: Client-Server
- **Android App** (Kotlin): Legge tag RFID via Bluetooth (Zebra RFD8500) e invia dati al backend
- **Backend** (Node.js/Express): REST API per gestione tag, movimenti, luoghi e zone
- **Database** (PostgreSQL): Database remoto esistente con schema predefinito

**IMPORTANTE**: Il database è remoto e condiviso. Non modificare lo schema senza autorizzazione.

## Development Commands

### Backend (Node.js)

```bash
cd backend

# Install dependencies
npm install

# Development (con auto-reload)
npm run dev

# Production
npm start

# Test database connection and inspect schema
node inspect_db.js

# Direct database query (Windows)
PGPASSWORD='iniAD16Z77oS' psql -h 57.129.5.234 -p 5432 -U rfidmanager -d rfid_db -c "\dt"

# Run tests (if configured)
npm test
```

**NOTA**: Il file `.env` è già configurato con le credenziali del database remoto.

### Android App

```bash
cd android-app

# Build debug
./gradlew assembleDebug

# Build release
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test
```

**IMPORTANTE**: Prima del build, scaricare `RFIDAPI3.aar` da Zebra Support Portal e posizionarlo in `android-app/app/libs/`

## Database Schema (ESISTENTE - NON MODIFICARE)

**Host**: 57.129.5.234:5432
**Database**: rfid_db
**User**: rfidmanager
**Password**: iniAD16Z77oS

### Tabelle Principali

**Items** (33 rows): EPC censiti
- `item_id` (PK): VARCHAR(50) - EPC del tag RFID
- `tid`: VARCHAR(50) - TID (Tag Identifier) del chip
- `date_creation`: TIMESTAMP - Data prima registrazione
- `date_lastseen`: TIMESTAMP - Data ultima lettura
- `place_last`: VARCHAR(50) - Ultimo luogo fisico (FK -> Places)
- `zone_last`: VARCHAR(50) - Ultima zona logica (FK -> Zones)
- `item_product_id`: VARCHAR(50) - Prodotto associato (FK -> Products)
- `nfc_uid`: TEXT - UID NFC se presente

**Movements** (352+ rows): Storico completo letture RFID
- `mov_id` (PK): INTEGER - ID movimento (auto-increment)
- `mov_epc`: VARCHAR(50) - EPC letto
- `mov_dest_place`: VARCHAR(50) - Luogo di destinazione
- `mov_dest_zone`: VARCHAR(50) - Zona di destinazione
- `mov_timestamp`: TIMESTAMP - Timestamp lettura
- `mov_unexpected`: BOOLEAN - Lettura inaspettata (default: false)
- `mov_readscount`: INTEGER - Numero di letture consecutive
- `mov_rssiavg`: NUMERIC - RSSI medio
- `mov_user`: VARCHAR(80) - Utente che ha effettuato lettura
- `mov_reader`: VARCHAR - ID del lettore RFID
- `mov_readerpw`: VARCHAR - Potenza del lettore
- `mov_notes`: TEXT - Note
- `mov_ref`: VARCHAR(80) - Riferimento
- `mov_antpw1-4`: INTEGER - Potenza antenna 1-4
- `mov_antenna`: INTEGER - Antenna utilizzata

**Places** (2 rows): Luoghi fisici
- `place_id` (PK): VARCHAR(50) - Es: "WHS" (Warehouse), "EV1" (Event)
- `place_name`: TEXT - Nome del luogo
- `place_type`: VARCHAR(50) - Tipologia luogo

**Zones** (3 rows): Zone logiche
- `zone_id` (PK): VARCHAR(50) - Es: "ING" (Entrata), "STK" (Stock), "TST" (Test)
- `zone_name`: TEXT - Nome zona
- `zone_type`: VARCHAR(50) - Tipologia zona

**Products** (16 rows): Anagrafica prodotti
- `product_id` (PK): VARCHAR
- `fld01-fld10`: TEXT - Campi dati generici
- `fldd01-fldd10`: TEXT - Campi dati descrittivi

**Products_labels** (2 rows): Metadati campi prodotti
- `pr_fld` (PK): VARCHAR - Nome campo (es. "fld01")
- `pr_lab`: VARCHAR - Label visualizzata
- `pr_des`: TEXT - Descrizione campo

**people** (2 rows): Persone associate a tag
- `id_people` (PK): INTEGER
- `epc`: VARCHAR(50)
- `uid`: VARCHAR(50)
- `name`: TEXT
- `role`: TEXT
- `image`: TEXT
- `permission`: INTEGER (default: 0)

**NOTA IMPORTANTE**: I nomi delle tabelle usano PascalCase ("Items", "Movements", ecc.) quindi devono essere quotati nelle query: `"Items"`, `"Movements"`, ecc.

## Architecture & Key Files

### Backend Architecture

**Pattern**: MVC (Model-View-Controller) con PostgreSQL remoto

```
backend/src/
├── server.js                 # Entry point, middleware, route registration
├── models/
│   ├── Item.js              # Model per tabella Items
│   ├── Movement.js          # Model per tabella Movements
│   ├── Place.js             # Model per tabella Places
│   ├── Zone.js              # Model per tabella Zones
│   └── Product.js           # Model per tabella Products
├── controllers/
│   ├── rfidController.js    # Scansioni RFID e movimenti
│   ├── itemsController.js   # Gestione items
│   ├── placesController.js  # Gestione places
│   ├── zonesController.js   # Gestione zones
│   └── productsController.js # Gestione products
├── routes/
│   ├── rfid.js              # Route RFID
│   ├── items.js             # Route items
│   ├── places.js            # Route places
│   ├── zones.js             # Route zones
│   └── products.js          # Route products
└── db/
    └── config.js            # PostgreSQL connection pool
```

**Workflow Scansione RFID**:
1. App Android legge tag con RFD8500
2. POST `/api/rfid/scan` con EPC, place_id, zone_id, RSSI
3. Backend:
   - `Item.upsert()`: INSERT/UPDATE in Items (aggiorna date_lastseen, place_last, zone_last)
   - `Movement.create()`: INSERT in Movements (nuovo record storico)
4. Response con item e movement creati

**Models Pattern**:
- Metodi statici per query al database
- Uso di `pool.query()` con parametrized queries (protezione SQL injection)
- Quote table names: `"Items"`, `"Movements"` (PascalCase)

### Android Architecture

**Pattern**: MVVM (Model-View-ViewModel) con Kotlin Coroutines

```
android-app/app/src/main/java/com/rfid/reader/
├── MainActivity.kt              # UI principale
├── viewmodel/
│   └── RFIDViewModel.kt        # ViewModel con LiveData
│       - Gestisce Places/Zones selezionati
│       - Auto-send tag al backend durante scan
│       - Coordina RFIDManager + API calls
├── rfid/
│   └── RFIDManager.kt          # Wrapper Zebra RFID SDK
│       - StateFlow per connection state e tags
│       - Event handlers per tag reads
│       - Configurazione antenna (power 270, session S0)
├── network/
│   ├── ApiService.kt           # Retrofit interface + data classes
│   │   - Request: ScanRequest, BatchScanRequest
│   │   - Response: ItemResponse, MovementResponse, PlaceResponse, ZoneResponse
│   └── RetrofitClient.kt       # Retrofit singleton
└── res/
    ├── layout/activity_main.xml
    └── values/strings.xml, themes.xml
```

**Key Features**:
- ViewModel carica Places e Zones all'avvio
- Utente seleziona Place/Zone prima di scannerizzare
- Ogni tag letto viene inviato automaticamente al backend con place_id e zone_id
- Batch scan disponibile per invii multipli

### Data Flow

```
1. App avvia → ViewModel carica Places e Zones dal backend
2. Utente seleziona Place (es: "WHS") e Zone (es: "STK")
3. Utente connette RFD8500 via Bluetooth
4. Utente avvia scan
5. RFD8500 legge tag → Zebra SDK fires TagReadEventListener
6. RFIDManager aggiorna StateFlow<List<TagData>>
7. ViewModel osserva flow → per ogni nuovo tag:
   - Crea ScanRequest con epc, placeId, zoneId, rssi, antenna
   - POST /api/rfid/scan
8. Backend:
   - UPSERT Items (aggiorna last_seen, place_last, zone_last)
   - INSERT Movements (nuovo record storico)
9. Utente stoppa scan
```

## API Endpoints

### RFID
- `POST /api/rfid/scan` - Registra singola scansione (upsert Item + insert Movement)
- `POST /api/rfid/batch-scan` - Registra batch di scansioni
- `GET /api/rfid/movements?limit=100&offset=0` - Movimenti recenti
- `GET /api/rfid/movements/unexpected` - Movimenti inaspettati
- `GET /api/rfid/movements/date-range?startDate=...&endDate=...` - Movimenti per range date
- `GET /api/rfid/movements/:epc` - Storico movimenti per EPC

### Items
- `GET /api/items?limit=100&offset=0` - Tutti gli items
- `GET /api/items/:epc` - Item per EPC
- `GET /api/items/place/:placeId` - Items per place
- `GET /api/items/zone/:zoneId` - Items per zone
- `GET /api/items/product/:productId` - Items per product
- `POST /api/items` - Crea/aggiorna item

### Places
- `GET /api/places` - Tutti i places
- `GET /api/places/:id` - Place specifico
- `POST /api/places` - Crea place
- `PUT /api/places/:id` - Aggiorna place
- `DELETE /api/places/:id` - Elimina place

### Zones
- `GET /api/zones` - Tutte le zones
- `GET /api/zones/:id` - Zone specifica
- `POST /api/zones` - Crea zone
- `PUT /api/zones/:id` - Aggiorna zone
- `DELETE /api/zones/:id` - Elimina zone

### Products
- `GET /api/products` - Tutti i products
- `GET /api/products/labels` - Labels campi prodotto
- `GET /api/products/:id` - Product specifico
- `POST /api/products` - Crea product
- `PUT /api/products/:id` - Aggiorna product
- `DELETE /api/products/:id` - Elimina product

**GET /api** - Documentazione API completa

## Configuration

### Backend Environment Variables

File: `backend/.env` (già configurato)
```
PORT=3000
DB_HOST=57.129.5.234
DB_PORT=5432
DB_NAME=rfid_db
DB_USER=rfidmanager
DB_PASSWORD=iniAD16Z77oS
CORS_ORIGIN=*
READER_ID=RFD8500-DEFAULT
```

**NON committare `.env` su repository pubblici!**

### Android Network Configuration

File: `android-app/app/src/main/java/com/rfid/reader/network/RetrofitClient.kt`

Modificare `BASE_URL`:
- Emulatore: `http://10.0.2.2:3000/`
- Device fisico: `http://YOUR_COMPUTER_IP:3000/`

Per trovare IP del computer:
- Windows: `ipconfig`
- Linux/Mac: `ifconfig` o `ip addr`

### Android Manifest Permissions

File: `android-app/app/src/main/AndroidManifest.xml`
- Bluetooth: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`
- Location: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (richiesto per Bluetooth scan Android 12+)
- Network: `INTERNET`, `ACCESS_NETWORK_STATE`
- `usesCleartextTraffic=true` per HTTP in development

## Common Workflows

### Adding New Backend Endpoint

1. Aggiungi metodo in Model appropriato (`src/models/*.js`)
2. Crea handler in Controller (`src/controllers/*Controller.js`)
3. Registra route in Router (`src/routes/*.js`)
4. Testa con curl o Postman
5. Aggiungi corrispondente metodo in `ApiService.kt` (Android)

### Querying Database con Table Names PascalCase

```javascript
// CORRETTO - Quote table names
const result = await pool.query('SELECT * FROM "Items" WHERE item_id = $1', [epc]);

// SBAGLIATO - Case insensitive fallisce
const result = await pool.query('SELECT * FROM items WHERE item_id = $1', [epc]);
```

### Debugging RFID Issues

**Backend**:
- Check logs: `npm run dev` mostra tutte le query SQL
- Test endpoints: `curl http://localhost:3000/api/places`
- Verifica connessione DB: `cd backend && node inspect_db.js`
- Query diretta DB: `PGPASSWORD='iniAD16Z77oS' psql -h 57.129.5.234 -p 5432 -U rfidmanager -d rfid_db -c "\dt"`

**Android**:
- Check Bluetooth permissions in MainActivity
- Verify RFD8500 paired in Android Bluetooth settings
- Enable logging in RFIDManager
- Use Android Logcat: `adb logcat | grep RFID`
- Verify network: `ping YOUR_COMPUTER_IP` dal device
- Check RetrofitClient BASE_URL

**Database**:
- Verifica dati inseriti con `cd backend && node inspect_db.js`
- Query diretta: `PGPASSWORD='iniAD16Z77oS' psql -h 57.129.5.234 -p 5432 -U rfidmanager -d rfid_db`
- Check integrity: `SELECT COUNT(*) FROM "Movements"`
- Verifica Places/Zones esistono prima di scannerizzare

## Zebra RFD8500 Specifics

**Device**: Zebra RFD8500 Bluetooth RFID Sled Reader
- **Connection**: Bluetooth pairing richiesto prima dell'uso
- **SDK**: RFID API 3 for Android (file `RFIDAPI3.aar`)
- **Supported Tags**: UHF RFID Gen2 (ISO 18000-6C)

**Configuration in RFIDManager.kt**:
- Transmit Power Index: 270 (max power)
- RF Mode: Index 0
- Session: SESSION_S0
- Inventory State: INVENTORY_STATE_A
- SL Flag: SL_ALL

**Event Handling**:
- `setTagReadEventListener`: Riceve array di TagData (batch processing)
- `setReaderDisconnectEventListener`: Per gestire disconnessioni
- TagData contiene: tagID (EPC), peakRSSI, antennaID

## Important Notes

**Database**:
- Database è **remoto e condiviso** - non fare test invasivi
- Usa sempre parametrized queries per evitare SQL injection
- Quote table names: `"Items"`, `"Movements"`, ecc.
- Items.item_id è l'EPC (non auto-increment)
- Movements.mov_id è auto-increment

**Android**:
- Test RFID solo su device fisico (non emulatore)
- RFD8500 richiede pairing Bluetooth manuale
- `RFIDAPI3.aar` non committato (60+ MB) - download manuale richiesto
- Permissions Android 12+ richiedono runtime request

**Backend**:
- Porta 3000 deve essere aperta sul firewall per device Android
- CORS configurato per `*` (in production limitare)
- Health check: `GET /health`
- API docs: `GET /api`

## Dependencies

### Backend
- `express`: Web framework
- `pg`: PostgreSQL client con connection pooling
- `cors`, `helmet`, `morgan`, `body-parser`: Middleware

### Android
- `androidx.lifecycle`: ViewModel, LiveData
- `kotlinx-coroutines-android`: Async operations
- `retrofit2`: HTTP client + GSON converter
- `okhttp3:logging-interceptor`: Network debugging
- `RFIDAPI3.aar`: Zebra RFID SDK (manual download)

## Testing

**Backend**:
```bash
# Test health
curl http://localhost:3000/health

# Test places
curl http://localhost:3000/api/places

# Test scan
curl -X POST http://localhost:3000/api/rfid/scan \
  -H "Content-Type: application/json" \
  -d '{"epc":"TEST-001","placeId":"WHS","zoneId":"STK","rssi":-45}'
```

**Android**:
- Build e installa su device fisico
- Pair RFD8500 in Bluetooth settings
- Seleziona Place e Zone
- Connect → Start Scan → Verifica tag su backend

## Documentation Files

- `CLAUDE.md`: Questo file
- `README.md`: Project overview
- `docs/DATABASE_SCHEMA.md`: Schema dettagliato database
- `docs/SETUP_GUIDE.md`: Setup passo-passo
- `docs/ARCHITECTURE.md`: Diagrammi architettura
- `backend/README.md`: Backend specific docs
- `android-app/README.md`: Android specific docs
