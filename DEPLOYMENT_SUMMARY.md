# Deployment Configuration Summary

## Production Backend: Vercel

**URL**: https://android-rfid.vercel.app
**Status**: ✅ Attivo e funzionante

### Configurazione Corretta (NON MODIFICARE)

#### Dashboard Vercel Settings
- **Root Directory**: `backend` ⚠️ IMPORTANTE
- **Framework Preset**: Other
- **Build Command**: (vuoto)
- **Output Directory**: (vuoto)
- **Install Command**: npm install

#### Environment Variables (Production)
```
DB_HOST=57.129.5.234
DB_PORT=5432
DB_NAME=rfid_db
DB_USER=rfidmanager
DB_PASSWORD=iniAD16Z77oS
CORS_ORIGIN=*
NODE_ENV=production
READER_ID=RFD8500-DEFAULT
```

### Struttura File Deployment

```
Android_RFID/                    ← Repository root
├── vercel.json.backup           ← Backup, NON usato (gitignored)
├── .vercelignore                ← Esclude android-app, docs
└── backend/                     ← ROOT DIRECTORY Vercel ⚠️
    ├── vercel.json              ← Rewrites config (ATTIVO)
    ├── package.json             ← Dependencies
    ├── api/
    │   └── index.js             ← Serverless entry point
    └── src/
        └── server.js            ← Express app
```

### File Chiave

**backend/vercel.json** (ATTIVO):
```json
{
  "version": 2,
  "rewrites": [
    {
      "source": "/(.*)",
      "destination": "/api"
    }
  ]
}
```

**backend/api/index.js**:
```javascript
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });
const app = require(path.join(__dirname, '../src/server'));
module.exports = app;
```

### Come Deployare

#### Metodo 1: CLI
```bash
cd backend
vercel --prod
```

#### Metodo 2: Git Push
```bash
git add .
git commit -m "Update"
git push  # Auto-deploy se Git collegato
```

### Test Endpoints

```bash
# Health
curl https://android-rfid.vercel.app/health

# API Docs
curl https://android-rfid.vercel.app/api

# Places
curl https://android-rfid.vercel.app/api/places

# Scan RFID
curl -X POST https://android-rfid.vercel.app/api/rfid/scan \
  -H "Content-Type: application/json" \
  -d '{"epc":"TEST","placeId":"WHS","zoneId":"STK","rssi":-45}'
```

### Troubleshooting

#### 404 su tutti gli endpoint
→ Verifica Root Directory = `backend` nel dashboard Vercel

#### 500 Internal Server Error
→ Controlla Environment Variables nel dashboard
→ Verifica logs: `vercel logs`

#### Module not found
→ Verifica path assoluti in `api/index.js`
→ Controlla dipendenze in `package.json`

### Documentazione Completa

- **Deployment Vercel**: `backend/VERCEL_DEPLOYMENT.md`
- **Backend API**: `backend/README.md`
- **Progetto**: `CLAUDE.md`
- **Database Schema**: `docs/DATABASE_SCHEMA.md`

### Configurazione Android App

Per connettere l'app Android al backend production, aggiorna:

**android-app/app/src/main/java/com/rfid/reader/network/RetrofitClient.kt**:
```kotlin
private const val BASE_URL = "https://android-rfid.vercel.app/"
```

### Note Importanti

1. ⚠️ NON modificare Root Directory nel dashboard (deve rimanere `backend`)
2. ⚠️ NON committare `.env` o `vercel.json.backup`
3. ⚠️ Database è remoto e condiviso - non modificare schema
4. ✅ Vercel auto-deploys su push se Git collegato
5. ✅ Logs visibili con `vercel logs` o nel dashboard
6. ✅ Rollback disponibile dal dashboard (Deployments → Promote)

### Limitazioni Vercel (Hobby Plan)

- **Timeout**: 10 secondi max per request
- **Memory**: 1024 MB
- **Payload**: 4.5 MB max request body
- **Cold Start**: 1-2 secondi dopo inattività

### Contatti

Per problemi deployment:
- Vercel Support: https://vercel.com/support
- Documentazione: https://vercel.com/docs

---

**Ultima modifica**: 2026-01-19
**Versione Backend**: 2.2.2
**Status**: ✅ Production Ready
