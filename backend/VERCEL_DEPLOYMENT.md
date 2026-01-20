# Vercel Deployment Guide

Questa guida descrive come deployare il backend RFID su Vercel come funzione serverless.

## Configurazione Iniziale

### 1. Configurazione Dashboard Vercel

Vai su [Vercel Dashboard](https://vercel.com/dashboard) e configura:

#### Root Directory
- Settings → General → Root Directory
- Imposta: **`backend`**
- Salva

#### Environment Variables
- Settings → Environment Variables
- Aggiungi le seguenti variabili (Production):

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

## Struttura File

```
backend/
├── vercel.json              # Configurazione Vercel
├── package.json             # Dipendenze
├── .env                     # Variabili locali (non committato)
├── .env.example             # Template variabili
├── api/
│   └── index.js            # Entry point serverless
└── src/
    ├── server.js           # Express app
    ├── routes/             # Route handlers
    ├── controllers/        # Business logic
    ├── models/             # Database models
    └── db/
        └── config.js       # PostgreSQL connection
```

## File di Configurazione

### vercel.json

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

Questa configurazione:
- Reindirizza TUTTE le richieste a `/api/index.js`
- `/api/index.js` carica l'app Express che gestisce tutte le route

### api/index.js

```javascript
const path = require('path');

// Load environment variables
require('dotenv').config({ path: path.join(__dirname, '../.env') });

// Load Express app with absolute path
const app = require(path.join(__dirname, '../src/server'));

// Export for Vercel serverless
module.exports = app;
```

**Nota**: Usa `path.join(__dirname, ...)` per path assoluti, evitando errori di risoluzione moduli.

## Deployment

### Metodo 1: CLI (Raccomandato per test)

```bash
cd backend
vercel --prod
```

### Metodo 2: Git Push (Auto-deploy)

Se hai collegato il repository Git a Vercel:

```bash
cd C:\_RFID\Android_RFID
git add .
git commit -m "Update backend"
git push
```

Vercel farà automaticamente il deploy quando rileva push su `main`.

## Test Post-Deployment

### 1. Health Check
```bash
curl https://android-rfid.vercel.app/health
```

Risposta attesa:
```json
{
  "status": "OK",
  "timestamp": "2026-01-19T...",
  "database": {
    "host": "57.129.5.234",
    "database": "rfid_db"
  }
}
```

### 2. API Documentation
```bash
curl https://android-rfid.vercel.app/api
```

Risposta: JSON con tutti gli endpoints disponibili.

### 3. Places List
```bash
curl https://android-rfid.vercel.app/api/places
```

Risposta: Array di luoghi configurati.

### 4. Scan RFID (POST)
```bash
curl -X POST https://android-rfid.vercel.app/api/rfid/scan \
  -H "Content-Type: application/json" \
  -d '{
    "epc": "TEST-001",
    "placeId": "WHS",
    "zoneId": "STK",
    "rssi": -45,
    "antenna": 1
  }'
```

## Monitoring

### View Logs

#### CLI
```bash
vercel logs
```

#### Dashboard
1. Vai su Vercel Dashboard
2. Seleziona il progetto `android-rfid`
3. Vai su **Deployments**
4. Clicca sul deployment più recente
5. Vai su **Function Logs**

### Metrics

Nel dashboard Vercel puoi vedere:
- Request count
- Response time
- Error rate
- Bandwidth usage

## Troubleshooting

### 404 Not Found su tutti gli endpoints

**Causa**: Root Directory non configurato correttamente.

**Soluzione**:
1. Vai su Settings → General → Root Directory
2. Imposta: `backend`
3. Salva e redeploy

### 500 Internal Server Error

**Causa**: Environment variables mancanti o errori di database.

**Debug**:
1. Controlla logs: `vercel logs`
2. Verifica Environment Variables nel dashboard
3. Testa connessione database locale: `node inspect_db.js`

**Note**: Vercel potrebbe avere IP dinamici. Se il database ha firewall, potrebbe bloccare le connessioni.

### Module not found

**Causa**: Path relativi non risolti correttamente.

**Soluzione**:
- Usa sempre `path.join(__dirname, ...)` per require() in `api/index.js`
- Verifica che tutte le dipendenze siano in `package.json` (non `devDependencies`)

### No function logs

**Causa**: La funzione serverless non viene eseguita.

**Debug**:
1. Verifica che `api/index.js` esista
2. Controlla che Root Directory = `backend`
3. Verifica `vercel.json` abbia il rewrite corretto
4. Prova a rimuovere e riconnettere il progetto su Vercel

### Database connection timeout

**Causa**: Firewall PostgreSQL blocca IP Vercel.

**Soluzioni**:
1. Se possibile, apri il firewall per tutti gli IP Vercel (vedi [Vercel IP ranges](https://vercel.com/docs/concepts/edge-network/regions))
2. Usa un database con connessione TLS/SSL aperta
3. Considera l'uso di connection pooling (già implementato con `pg.Pool`)

## Limitazioni Vercel

### Serverless Functions

- **Timeout**: 10 secondi (Hobby plan) / 60 secondi (Pro plan)
- **Memory**: 1024 MB
- **Payload**: 4.5 MB request body max

### Configurazione Timeout

In `vercel.json` puoi aumentare il timeout (max in base al piano):

```json
{
  "functions": {
    "api/index.js": {
      "maxDuration": 10
    }
  }
}
```

### Cold Start

Le funzioni serverless hanno un "cold start" di 1-2 secondi se non usate da un po'. È normale.

## Best Practices

1. **Environment Variables**: Non committare mai `.env`. Usa sempre il dashboard Vercel per configurarle.

2. **Logs**: Usa `console.log()` e `console.error()` - Vercel li cattura automaticamente.

3. **Error Handling**: Implementa sempre try-catch nei controllers per errori database.

4. **Connection Pooling**: Usa sempre `pg.Pool` (già implementato) invece di `pg.Client` per evitare esaurimento connessioni.

5. **CORS**: Configurato per `*` in development. In production, limita a domini specifici.

## Rollback

Se un deployment ha problemi:

1. Vai su Dashboard → Deployments
2. Trova un deployment funzionante precedente
3. Clicca sui 3 puntini → **Promote to Production**

## Updates

Per aggiornare il backend:

```bash
# 1. Modifica codice locale
# 2. Test locale
npm run dev

# 3. Commit e deploy
git add .
git commit -m "Descrizione modifiche"
git push  # Auto-deploy

# Oppure CLI
cd backend
vercel --prod
```

## Support

- [Vercel Documentation](https://vercel.com/docs)
- [Vercel Node.js Runtime](https://vercel.com/docs/runtimes#official-runtimes/node-js)
- [Vercel Serverless Functions](https://vercel.com/docs/concepts/functions/serverless-functions)
