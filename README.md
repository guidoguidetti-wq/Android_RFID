# Android RFID Application

Applicazione completa per lettura RFID con Zebra RFD8500, backend Node.js e database PostgreSQL.

## Struttura Progetto

```
Android_RFID/
├── backend/              # Backend Node.js + Express
│   ├── src/
│   │   ├── controllers/  # Controller API
│   │   ├── routes/       # Route API
│   │   ├── db/          # Database config e schema
│   │   └── server.js    # Entry point
│   └── package.json
│
├── android-app/         # App Android
│   └── app/
│       └── src/main/
│           └── java/com/rfid/reader/
│               ├── MainActivity.kt
│               ├── viewmodel/    # ViewModel MVVM
│               ├── network/      # API client
│               └── rfid/         # Zebra RFID manager
│
└── docs/               # Documentazione
```

## Quick Start

### 1. Database PostgreSQL

```bash
# Crea database
psql -U postgres -c "CREATE DATABASE rfid_db;"

# Importa schema
psql -U postgres -d rfid_db -f backend/src/db/schema.sql
```

### 2. Backend

```bash
cd backend
npm install
cp .env.example .env
# Modifica .env con credenziali database
npm run dev
```

Il server sarà disponibile su `http://localhost:3000`

### 3. Android App

1. Scarica Zebra RFID SDK da [Zebra Support Portal](https://www.zebra.com/us/en/support-downloads/software/developer-tools/rfid-sdk-android.html)
2. Posiziona `RFIDAPI3.aar` in `android-app/app/libs/`
3. Apri `android-app/` in Android Studio
4. Modifica URL backend in `RetrofitClient.kt`
5. Build e installa su device

## Tecnologie

- **Backend**: Node.js, Express, PostgreSQL
- **Android**: Kotlin, MVVM, Retrofit, Zebra RFID SDK
- **Database**: PostgreSQL

## API Endpoints

- `POST /api/rfid/scan` - Registra scansione tag
- `POST /api/rfid/sessions/start` - Avvia sessione
- `POST /api/rfid/sessions/:id/end` - Termina sessione
- `GET /api/rfid/sessions` - Lista sessioni
- `GET /api/tags` - Lista tutti i tag
- `GET /api/tags/:epc` - Dettagli tag

## Documentazione

Vedi [CLAUDE.md](CLAUDE.md) per dettagli architetturali e guida sviluppo.
