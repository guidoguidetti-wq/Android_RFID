# Guida Setup Completo

Guida passo-passo per configurare l'ambiente di sviluppo.

## 1. Prerequisiti

- Node.js 16+ e npm
- PostgreSQL 13+ (o Docker)
- Android Studio Arctic Fox+
- JDK 17
- Device Android con Bluetooth (per testing RFID)
- Zebra RFD8500 (lettore RFID)

## 2. Setup Database PostgreSQL

### Opzione A: Docker (Consigliato)

```bash
# Avvia PostgreSQL con Docker Compose
docker-compose up -d

# Verifica container attivo
docker ps
```

Il database sarà disponibile su `localhost:5432` con:
- Database: `rfid_db`
- User: `postgres`
- Password: `postgres`

Lo schema viene applicato automaticamente all'avvio.

### Opzione B: Installazione Locale

```bash
# Installa PostgreSQL (esempio Ubuntu)
sudo apt install postgresql postgresql-contrib

# Avvia servizio
sudo systemctl start postgresql

# Crea database
sudo -u postgres psql -c "CREATE DATABASE rfid_db;"

# Applica schema
sudo -u postgres psql -d rfid_db -f backend/src/db/schema.sql
```

## 3. Setup Backend

```bash
cd backend

# Installa dipendenze
npm install

# Configura environment
cp .env.example .env
nano .env  # Modifica credenziali database se necessario

# Avvia in development mode
npm run dev
```

Verifica funzionamento:
```bash
curl http://localhost:3000/health
# Output: {"status":"OK","timestamp":"..."}
```

## 4. Setup Android App

### 4.1 Download Zebra RFID SDK

1. Vai su [Zebra Support Portal](https://www.zebra.com/us/en/support-downloads/software/developer-tools/rfid-sdk-android.html)
2. Registrati/Login
3. Scarica **RFID SDK for Android** (ultima versione)
4. Estrai il file `RFIDAPI3.aar`
5. Copia in `android-app/app/libs/RFIDAPI3.aar`

### 4.2 Configurazione Android Studio

1. Apri Android Studio
2. File → Open → Seleziona cartella `android-app/`
3. Attendi sync Gradle (può richiedere tempo al primo avvio)

### 4.3 Configurazione Network

Modifica `android-app/app/src/main/java/com/rfid/reader/network/RetrofitClient.kt`:

```kotlin
// Per emulatore Android
private const val BASE_URL = "http://10.0.2.2:3000/"

// Per device fisico, usa IP del tuo computer
// Trova IP con: ipconfig (Windows) o ifconfig (Linux/Mac)
private const val BASE_URL = "http://192.168.1.XXX:3000/"
```

### 4.4 Build e Installazione

```bash
cd android-app

# Build debug APK
./gradlew assembleDebug

# Installa su device connesso via USB
./gradlew installDebug
```

## 5. Pairing Zebra RFD8500

1. Accendi Zebra RFD8500 (pulsante power)
2. Sul device Android:
   - Impostazioni → Bluetooth
   - Scansiona dispositivi
   - Seleziona "RFD8500-XXXXX"
   - Pair (PIN di default: 0000 o 1234)

## 6. Test Applicazione

1. Avvia backend: `cd backend && npm run dev`
2. Avvia app Android su device
3. Tocca "Connetti Lettore"
4. Verifica status: "Connesso"
5. Tocca "Avvia Scansione"
6. Avvicina tag RFID al lettore
7. Verifica tag visualizzati nell'app

## 7. Verifica Database

```bash
# Connetti a PostgreSQL
psql -U postgres -d rfid_db

# Query tag letti
SELECT * FROM rfid_tags ORDER BY last_seen DESC LIMIT 10;

# Query sessioni
SELECT * FROM read_sessions ORDER BY start_time DESC LIMIT 5;
```

## Troubleshooting

### Backend non si avvia
- Verifica PostgreSQL attivo: `pg_isready`
- Controlla credenziali in `.env`
- Verifica porta 3000 libera: `lsof -i :3000`

### Android build fallisce
- Verifica `RFIDAPI3.aar` in `app/libs/`
- Sync Gradle: File → Sync Project with Gradle Files
- Invalidate Caches: File → Invalidate Caches / Restart

### App non connette al backend
- Verifica backend attivo: `curl http://localhost:3000/health`
- Controlla URL in `RetrofitClient.kt`
- Per device fisico, verifica firewall su porta 3000
- Verifica device e computer sulla stessa rete WiFi

### RFD8500 non si connette
- Verifica pairing Bluetooth
- Check permessi Location nell'app (richiesto per Bluetooth scan)
- Riavvia RFD8500
- Riavvia app Android

### Tag non vengono letti
- Verifica RFD8500 connesso (LED verde)
- Check potenza antenna in `RFIDManager.kt`
- Usa tag UHF Gen2 compatibili
- Verifica distanza tag-antenna (< 3 metri)
