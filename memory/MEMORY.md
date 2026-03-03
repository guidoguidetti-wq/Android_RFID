# Android RFID App — Memory

## Build
- Java 17 required: `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.17.10-hotspot"`
- Default Java on PATH is Java 8 (Corretto) — always override JAVA_HOME for Gradle
- APK output: `android-app/app/build/outputs/apk/debug/app-debug.apk`
- Build command: `cd android-app && JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.17.10-hotspot" ./gradlew assembleDebug`

## Architecture
- Android (Kotlin) + Node.js/Express backend + PostgreSQL (Neon, remote)
- Backend deployed on Vercel: https://android-rfid.vercel.app
- RFIDManager is a singleton shared across all activities
- Reader must be connected before scan features work (connect from TagInfo or other scan screens)

## Key Patterns
- Table names are PascalCase in DB → must be quoted in SQL: `"Items"`, `"Movements"`
- viewModelScope uses Dispatchers.Main; Retrofit calls suspend on Main, run on IO internally
- `isFlushing` flag in TagInfoViewModel prevents debounceJob cancellation mid-fetch

## Milestones

### v1.0 — 2026-03-03 — commit bb9e84c
**All features working and verified by user.**

#### Tag Info
- Real-time EPC counter (immediate, before DB check)
- Parallel batch fetch: items + products in 2 parallel bursts
- Only registered items (present in `Items` table) shown in list; unregistered = Ignored counter
- Fixed: `isFlushing` flag prevents debounce job cancellation during `flushPendingBatch()`
- Fixed: flush immediately on stopScan() — no extra debounce delay after user stops
- Default debounce delay: 300ms (configurable in Settings)
- Settings button removed from TagInfo header (accessible from main menu only)

#### Settings
- Power (10–300, step 5) and Min RSSI (−70 to −10, step 1): replaced seekbars with +/− buttons
- Long-press on +/− = auto-repeat (400ms initial delay, 80ms interval)
- Tap on value label = AlertDialog with numeric EditText for direct input
- **Bug fixed**: `RFIDManager.configureReader()` was hardcoding power=270; now reads `settingsManager.getReaderPower()` on every connect

#### Inventory Details
- Tapping any item opens `RssiMonitorActivity` with `TARGET_EPC` + `AUTO_START=true`
- Scan starts automatically; ▶/⏸ button still available for manual control
