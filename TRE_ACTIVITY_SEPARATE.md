# TRE ACTIVITY SEPARATE PER INVENTARIO

## ✅ COMPLETATO

Ho creato tre Activity completamente separate per le tre modalità di inventario, con UI, layout e comportamenti distinti.

---

## 📱 NUOVE ACTIVITY CREATE

### 1. InventoryScanNormalActivity

**File**:
- `android-app/app/src/main/java/com/rfid/reader/InventoryScanNormalActivity.kt`
- `android-app/app/src/main/res/layout/activity_inventory_scan_normal.xml`

**UI Caratteristiche**:
- **Badge**: Grigio "Normal"
- **Counter Visibili**:
  - Principale (grande, verde)
  - **Validated** (40sp, NERO) ← Unico sub-counter visibile
- **Counter Nascosti**: Unexpected, Lost

**Logica**:
- Rispetta setting "Tutti" (mode_b) o "Solo Censiti" (mode_a)
- Tutti i tag vengono marcati come `inv_expected = true`
- Counter "Validated" = expectedCount dal backend

**Colori**:
- Badge: `#9E9E9E` (grigio)
- Counter principale: `#4CAF50` (verde)
- Validated: `#000000` (nero, bold)

---

### 2. InventoryScanChecklistActivity

**File**:
- `android-app/app/src/main/java/com/rfid/reader/InventoryScanChecklistActivity.kt`
- `android-app/app/src/main/res/layout/activity_inventory_scan_checklist.xml`

**UI Caratteristiche**:
- **Badge**: Verde "Checklist" con totale attesi `(8)`
- **Counter Visibili**:
  - Principale (grande, verde)
  - **Expected** (32sp, verde)
  - **Unexpected** (32sp, arancione)
  - **Lost** (32sp, rosso)

**Logica**:
- Ignora setting mode (sempre Solo Censiti)
- Incrementa `ckp_qta_exp` nel database per ogni tag expected
- Expected se ` ckp_qta_exp + 1 <= ckp_qta`
- Unexpected se supera quantità o non in checklist
- Lost = totalExpected - expected (calcolato dal backend)

**Colori**:
- Badge: `#4CAF50` (verde)
- Expected: `#4CAF50` (verde)
- Unexpected: `#FF9800` (arancione)
- Lost: `#F44336` (rosso)

---

### 3. InventoryScanStockActivity

**File**:
- `android-app/app/src/main/java/com/rfid/reader/InventoryScanStockActivity.kt`
- `android-app/app/src/main/res/layout/activity_inventory_scan_stock.xml`

**UI Caratteristiche**:
- **Badge**: Blu "Stock" con totale attesi `(15)`
- **Counter Visibili**:
  - Principale (grande, BLU)
  - **Expected** (32sp, verde)
  - **Unexpected** (32sp, arancione)
  - **Lost** (32sp, rosso)

**Logica**:
- Rispetta setting mode
- Expected se EPC era in `place_last` / `zone_last` specificati
- Unexpected se EPC da altro place/zone
- Lost pre-popolato all'apertura dal backend

**Colori**:
- Badge: `#2196F3` (blu)
- Counter principale: `#2196F3` (blu invece di verde)
- Expected: `#4CAF50` (verde)
- Unexpected: `#FF9800` (arancione)
- Lost: `#F44336` (rosso)

---

## 🔀 ROUTING AUTOMATICO

**Modifiche in `InventoryListActivity.kt`**:

Quando l'utente clicca su un inventario, il sistema determina automaticamente quale Activity aprire:

```kotlin
val isChecklistMode = inventory.inv_chk_id != null && inventory.inv_chk_id != 0
val isStockMode = inventory.inv_last == true

val activityClass = when {
    isChecklistMode -> InventoryScanChecklistActivity::class.java
    isStockMode -> InventoryScanStockActivity::class.java
    else -> InventoryScanNormalActivity::class.java
}
```

**Criterio di routing**:
1. Se `inv_chk_id != 0` → **InventoryScanChecklistActivity**
2. Else if `inv_last = true` → **InventoryScanStockActivity**
3. Else → **InventoryScanNormalActivity**

---

## 📋 MODIFICHE AI MODELS

**ApiService.kt - InventoryResponse**:

Aggiunti campi per determinare modalità:
```kotlin
data class InventoryResponse(
    val inv_id: Int,
    val inv_name: String,
    // ... campi esistenti ...
    val inv_chk_id: Int? = 0,      // ✅ NUOVO
    val inv_last: Boolean? = false  // ✅ NUOVO
)
```

---

## 📝 ANDROIDMANIFEST.XML

Registrate le tre nuove Activity:

```xml
<!-- Inventory Scan - NORMAL Mode -->
<activity
    android:name=".InventoryScanNormalActivity"
    android:exported="false"
    android:screenOrientation="portrait"
    android:keepScreenOn="true" />

<!-- Inventory Scan - CHECKLIST Mode -->
<activity
    android:name=".InventoryScanChecklistActivity"
    android:exported="false"
    android:screenOrientation="portrait"
    android:keepScreenOn="true" />

<!-- Inventory Scan - STOCK Mode (Last-Place) -->
<activity
    android:name=".InventoryScanStockActivity"
    android:exported="false"
    android:screenOrientation="portrait"
    android:keepScreenOn="true" />
```

**Nota**: La vecchia `InventoryScanActivity` è ancora presente per retrocompatibilità, ma marcata come da deprecare.

---

## 🎨 CONFRONTO VISIVO DELLE TRE UI

### NORMAL MODE
```
┌─────────────────────────────────┐
│ ← Inventory - Normal          ☰ │
├─────────────────────────────────┤
│ 20.12.2025                      │
│ Test Inventory 1       [Normal] │ ← Badge Grigio
├─────────────────────────────────┤
│                                 │
│       Tags Counter              │
│                                 │
│          [100]                  │ ← Verde, 100sp
│                                 │
│          [100]                  │ ← Nero, 40sp
│        Validated                │ ← UNICO counter
│                                 │
├─────────────────────────────────┤
│ [Details] [Settings] [CLEAR]   │
│ [CLOSE]   [DELETE]              │
├─────────────────────────────────┤
│ Connected              [▶]      │
└─────────────────────────────────┘
```

### CHECKLIST MODE
```
┌─────────────────────────────────┐
│ ← Inventory - Checklist       ☰ │
├─────────────────────────────────┤
│ 20.12.2025                      │
│ Test Checklist    [Checklist]   │ ← Badge Verde
│                       (8)        │ ← Totale attesi
├─────────────────────────────────┤
│                                 │
│       Tags Counter              │
│                                 │
│          [50]                   │ ← Verde, 100sp
│                                 │
│   [30]    [15]     [5]         │ ← 32sp
│ Expected Unexpected Lost        │ ← TRE counters
│ (verde)  (arancio) (rosso)     │
│                                 │
├─────────────────────────────────┤
│ [Details] [Settings] [CLEAR]   │
│ [CLOSE]   [DELETE]              │
├─────────────────────────────────┤
│ Connected              [▶]      │
└─────────────────────────────────┘
```

### STOCK MODE
```
┌─────────────────────────────────┐
│ ← Inventory - Stock           ☰ │
├─────────────────────────────────┤
│ 20.12.2025                      │
│ Test Stock           [Stock]    │ ← Badge Blu
│                       (15)       │ ← Totale attesi
├─────────────────────────────────┤
│                                 │
│       Tags Counter              │
│                                 │
│          [80]                   │ ← BLU, 100sp
│                                 │
│   [60]    [10]     [10]        │ ← 32sp
│ Expected Unexpected Lost        │ ← TRE counters
│ (verde)  (arancio) (rosso)     │
│                                 │
├─────────────────────────────────┤
│ [Details] [Settings] [CLEAR]   │
│ [CLOSE]   [DELETE]              │
├─────────────────────────────────┤
│ Connected              [▶]      │
└─────────────────────────────────┘
```

---

## 📊 TABELLA COMPARATIVA

| Feature | NORMAL | CHECKLIST | STOCK |
|---------|--------|-----------|-------|
| **File Activity** | InventoryScanNormalActivity.kt | InventoryScanChecklistActivity.kt | InventoryScanStockActivity.kt |
| **File Layout** | activity_inventory_scan_normal.xml | activity_inventory_scan_checklist.xml | activity_inventory_scan_stock.xml |
| **Badge Colore** | Grigio `#9E9E9E` | Verde `#4CAF50` | Blu `#2196F3` |
| **Badge Testo** | "Normal" | "Checklist (N)" | "Stock (N)" |
| **Counter Principale** | Verde | Verde | **Blu** |
| **Counter Validated/Expected** | Nero 40sp "Validated" | Verde 32sp "Expected" | Verde 32sp "Expected" |
| **Counter Unexpected** | ❌ Nascosto | ✅ Arancione 32sp | ✅ Arancione 32sp |
| **Counter Lost** | ❌ Nascosto | ✅ Rosso 32sp | ✅ Rosso 32sp |
| **Rispetta mode setting** | ✅ Sì | ❌ No (sempre Solo Censiti) | ✅ Sì |
| **Backend logic** | inv_expected = TRUE sempre | Incrementa ckp_qta_exp | Verifica place_last/zone_last |
| **Condizione routing** | Default | inv_chk_id != 0 | inv_last = true |

---

## 🔧 COME FUNZIONA

### 1. Utente apre lista inventari
→ `InventoryListActivity` mostra tutti gli inventari OPEN

### 2. Utente clicca su un inventario
→ `InventoryListActivity` legge `inv_chk_id` e `inv_last` dal backend

### 3. Routing automatico
```
IF inv_chk_id != 0:
    → Open InventoryScanChecklistActivity (Badge verde, 4 counters)
ELSE IF inv_last = true:
    → Open InventoryScanStockActivity (Badge blu, 4 counters)
ELSE:
    → Open InventoryScanNormalActivity (Badge grigio, 2 counters)
```

### 4. Scansione tag
- Ogni Activity usa lo stesso `InventoryScanViewModel`
- ViewModel applica logica corretta in base a `inventoryMode`
- Backend calcola status (expected/unexpected) e aggiorna counter

### 5. UI aggiornata in tempo reale
- Normal: Solo Validated incrementa
- Checklist: Expected/Unexpected/Lost si aggiornano
- Stock: Expected/Unexpected/Lost si aggiornano

---

## ✅ VANTAGGI APPROCCIO TRE ACTIVITY

1. **UI Dedicata**: Ogni modalità ha layout ottimizzato
2. **Codice Pulito**: Nessun `if/else` per nascondere/mostrare elementi
3. **Esperienza Utente**: Utente vede immediatamente la modalità dall'header
4. **Manutenibilità**: Modifiche a una modalità non impattano le altre
5. **Performance**: Nessun overhead di binding elementi nascosti
6. **Chiarezza**: Ogni file ha uno scopo preciso e ben definito

---

## 🚀 PROSSIMI PASSI

1. **Test su device fisico** con RFD8500
2. **Verifica routing** con inventari reali (normale, checklist, stock)
3. **Test counter** in tutte e tre le modalità
4. **Verifica persistenza** alla chiusura/riapertura
5. **Test CLEAR** per verificare reset counter checklist

---

## 📁 FILE MODIFICATI/CREATI

### File Nuovi (6 file):
```
android-app/app/src/main/java/com/rfid/reader/
  ├── InventoryScanNormalActivity.kt     (305 linee)
  ├── InventoryScanChecklistActivity.kt  (320 linee)
  └── InventoryScanStockActivity.kt      (320 linee)

android-app/app/src/main/res/layout/
  ├── activity_inventory_scan_normal.xml     (280 linee)
  ├── activity_inventory_scan_checklist.xml  (340 linee)
  └── activity_inventory_scan_stock.xml      (340 linee)
```

### File Modificati (3 file):
```
android-app/app/src/main/java/com/rfid/reader/
  ├── InventoryListActivity.kt    (routing automatico)
  └── network/ApiService.kt       (aggiunti inv_chk_id e inv_last)

android-app/app/src/main/
  └── AndroidManifest.xml          (registrate 3 Activity)
```

### File Backend (già modificati precedentemente):
```
backend/src/
  ├── models/ChecklistProduct.js        (5 nuovi metodi)
  ├── controllers/inventoriesController.js (logica 3 modalità)
  └── ...
```

---

## 🎯 RISULTATO FINALE

Ora hai **TRE PAGINE COMPLETAMENTE DIVERSE**:

1. **NORMAL**: Semplice, pulita, solo 2 counter (Principale + Validated nero)
2. **CHECKLIST**: Completa, badge verde, 4 counter con logica checklist
3. **STOCK**: Completa, badge blu, 4 counter con logica last-place

Ogni pagina è ottimizzata per il suo caso d'uso specifico, con UI e comportamenti dedicati!

