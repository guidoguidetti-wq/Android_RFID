# RFID Reader - Android App

Applicazione Android per leggere tag RFID con Zebra RFD8500 via Bluetooth.

## Prerequisiti

1. **Zebra RFID SDK**
   - Scarica RFID SDK da [Zebra Support Portal](https://www.zebra.com/us/en/support-downloads/software/developer-tools/rfid-sdk-android.html)
   - Copia il file `RFIDAPI3.aar` nella cartella `app/libs/`

2. **Android Studio**
   - Arctic Fox o superiore
   - JDK 17

## Setup

1. Apri il progetto in Android Studio
2. Scarica e posiziona `RFIDAPI3.aar` in `app/libs/`
3. Modifica l'URL del backend in `RetrofitClient.kt`:
   - Per emulatore: `http://10.0.2.2:3000/`
   - Per device fisico: `http://YOUR_COMPUTER_IP:3000/`
4. Sincronizza Gradle

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

## Installazione

```bash
# Installa su device connesso
./gradlew installDebug
```

## Permessi Richiesti

- Bluetooth (BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
- Location (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
- Internet (INTERNET, ACCESS_NETWORK_STATE)

## Architettura

- **MVVM pattern** con ViewModel e LiveData
- **Retrofit** per chiamate API REST
- **Coroutines** per operazioni asincrone
- **Zebra RFID SDK** per comunicazione con RFD8500
