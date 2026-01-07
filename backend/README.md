# RFID Backend - Node.js

Backend REST API per applicazione RFID Android con lettore Zebra RFD8500.

**Database**: PostgreSQL remoto esistente su 57.129.5.234:5432

## Quick Start

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Test database connection
node inspect_db.js
```

Il server sarà disponibile su `http://localhost:3000`

## API Endpoints

### Health Check
- `GET /health` - Verifica stato server e database
- `GET /api` - Documentazione API completa

### RFID
- `POST /api/rfid/scan` - Registra scansione (upsert Item + insert Movement)
- `POST /api/rfid/batch-scan` - Batch di scansioni
- `GET /api/rfid/movements` - Movimenti recenti
- `GET /api/rfid/movements/:epc` - Storico per EPC
- `GET /api/rfid/movements/unexpected` - Movimenti inaspettati

### Items (Tags RFID)
- `GET /api/items` - Lista items
- `GET /api/items/:epc` - Item per EPC
- `GET /api/items/place/:placeId` - Items per luogo
- `GET /api/items/zone/:zoneId` - Items per zona
- `POST /api/items` - Crea/aggiorna item

### Places (Luoghi Fisici)
- `GET /api/places` - Lista places
- `POST /api/places` - Crea place
- `PUT /api/places/:id` - Aggiorna place
- `DELETE /api/places/:id` - Elimina place

### Zones (Zone Logiche)
- `GET /api/zones` - Lista zones
- `POST /api/zones` - Crea zone
- `PUT /api/zones/:id` - Aggiorna zone
- `DELETE /api/zones/:id` - Elimina zone

### Products
- `GET /api/products` - Lista products
- `GET /api/products/labels` - Metadati campi prodotto

## Environment Variables

File `.env` (già configurato):
```
PORT=3000
DB_HOST=57.129.5.234
DB_PORT=5432
DB_NAME=rfid_db
DB_USER=rfidmanager
DB_PASSWORD=iniAD16Z77oS
READER_ID=RFD8500-DEFAULT
```

## Database Schema

**Tabelle Principali**:
- `Items` - EPC censiti (33 rows)
- `Movements` - Storico letture (352+ rows)
- `Places` - Luoghi fisici (2 rows: WHS, EV1)
- `Zones` - Zone logiche (3 rows: ING, STK, TST)
- `Products` - Anagrafica prodotti (16 rows)

**IMPORTANTE**: Nomi tabelle in PascalCase, quotare nelle query: `"Items"`, `"Movements"`, ecc.

## Example Requests

```bash
# Health check
curl http://localhost:3000/health

# Get places
curl http://localhost:3000/api/places

# Record RFID scan
curl -X POST http://localhost:3000/api/rfid/scan \
  -H "Content-Type: application/json" \
  -d '{
    "epc": "E2801170200050328216AD1C",
    "placeId": "WHS",
    "zoneId": "STK",
    "rssi": -45,
    "antenna": 1
  }'

# Get movements history for EPC
curl http://localhost:3000/api/rfid/movements/E2801170200050328216AD1C
```

## Project Structure

```
backend/
├── src/
│   ├── server.js              # Entry point
│   ├── models/                # Database models
│   │   ├── Item.js
│   │   ├── Movement.js
│   │   ├── Place.js
│   │   ├── Zone.js
│   │   └── Product.js
│   ├── controllers/           # Request handlers
│   │   ├── rfidController.js
│   │   ├── itemsController.js
│   │   ├── placesController.js
│   │   ├── zonesController.js
│   │   └── productsController.js
│   ├── routes/                # API routes
│   │   ├── rfid.js
│   │   ├── items.js
│   │   ├── places.js
│   │   ├── zones.js
│   │   └── products.js
│   └── db/
│       └── config.js          # PostgreSQL pool
├── inspect_db.js              # Database inspection tool
└── package.json
```

## Testing

```bash
# Start server
npm run dev

# In another terminal, test endpoints
curl http://localhost:3000/health
curl http://localhost:3000/api/places
curl http://localhost:3000/api/zones
curl http://localhost:3000/api/items
```

## Notes

- Database è remoto e condiviso - non fare modifiche allo schema
- CORS configurato per `*` (modificare in production)
- Logging attivo in dev mode (Morgan)
- Porta 3000 deve essere accessibile da device Android
