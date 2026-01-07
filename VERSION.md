# RFID Android App - Version History

## v0.1.0 - Working Hardware Integration (2026-01-05)

### Status: ✅ FUNZIONANTE

### Features
- ✅ Connessione Bluetooth al reader Zebra RFD8500
- ✅ Lettura tag RFID UHF con beep di conferma
- ✅ Aggiornamento contatore tag in tempo reale
- ✅ Invio automatico tag al backend Node.js
- ✅ Salvataggio in database PostgreSQL (Items + Movements)
- ✅ Selezione Place e Zone dall'app

### Technical Details
- **SDK**: Zebra RFID API 3 SDK 2.0.5.226
- **Event Model**: `setTagReadEvent(true)` + `Actions.getReadTags(1000)`
- **Database**:
  - Items: 77 tag registrati
  - Movements: 639 movimenti storici
- **Backend Performance**: ~45-50ms per richiesta POST /api/rfid/scan

### Files Modificati
- `android-app/app/src/main/java/com/rfid/reader/MainActivity.kt`
  - Aggiunto observer `isConnected` per abilitare pulsante scansione
  - Aggiunto observer `isScanning` per cambio testo pulsante
- `android-app/app/src/main/java/com/rfid/reader/rfid/RFIDManager.kt`
  - Corretto gestione eventi SDK 2.0 (`ReadEventData` → `Actions.getReadTags()`)
  - Abilitazione esplicita eventi: `events.setTagReadEvent(true)`
  - Rimossi riferimenti a `statusCode` (non esistente in SDK 2.0)

### Known Issues
- UI minimale (da migliorare)
- Nessuna visualizzazione lista tag scannerizzati
- Manca selezione Place/Zone (hardcoded o default)

### Backup
- APK funzionante: `backups/app-debug-working-20260105.apk`

---

## Next Steps: UI & Backend Improvements
- [ ] Migliorare UI Android (RecyclerView per lista tag, spinner Place/Zone)
- [ ] Aggiungere visualizzazione dettagli tag
- [ ] Ottimizzare backend (batch insert, caching)
- [ ] Aggiungere API per statistiche e report
- [ ] Implementare filtri e ricerca nel database
