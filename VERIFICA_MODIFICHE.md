# VERIFICA ESTESA MODIFICHE INVENTARIO

## RIEPILOGO MODIFICHE IMPLEMENTATE

### 1. Backend - ChecklistProduct Model

**File**: `backend/src/models/ChecklistProduct.js`

**Metodi Aggiunti**:
- `incrementExpected(chkId, productId)`: +1 su `ckp_qta_exp`
- `incrementUnexpected(chkId, productId)`: +1 su `ckp_qta_unexp`
- `decrementMissing(chkId, productId)`: -1 su `ckp_qta_missing`
- `resetCounters(chkId)`: Azzera counter e imposta `ckp_qta_missing = ckp_qta`
- `syncCountersWithInventory(chkId, invId)`: Ricalcola counter dai dati reali in `inventory_items`

---

### 2. Backend - Inventory Controller

**File**: `backend/src/controllers/inventoriesController.js`

#### Endpoint: POST `/api/inventories/:invId/scan` (linee 387-600)

**MODALITÀ NORMAL** (`inv_chk_id = 0` AND `inv_last = false/null`):

```
IF mode = 'mode_b' (Tutti):
  - Crea EPC in Items se non esiste
  - Scrivi in inventory_items con inv_expected = TRUE
  - Counter: Principale = tutti, Validated = tutti

IF mode = 'mode_a' (Solo Censiti):
  - Solo se EPC esiste in Items
  - Scrivi in inventory_items con inv_expected = TRUE
  - Counter: Principale = censiti, Validated = censiti
  - Tag NON in Items → IGNORATI (non scritti)
```

**MODALITÀ CHECKLIST** (`inv_chk_id != 0`):

```
IGNORA setting mode (sempre Solo Censiti)

Per ogni tag:
  1. Ottieni product_id da Items (item_product_id)

  2. IF product_id IN checklist_products:
       IF ckp_qta_exp + 1 <= ckp_qta:
         - incrementExpected(chk_id, product_id)
         - Scrivi inventory_items con inv_expected = TRUE
       ELSE:
         - incrementUnexpected(chk_id, product_id)
         - Scrivi inventory_items con inv_unexpected = TRUE

     ELSE (prodotto non in checklist):
       - Scrivi inventory_items con inv_unexpected = TRUE

Counter: Expected, Unexpected, Lost (totalExpected - expected)
```

**MODALITÀ STOCK** (`inv_last = true`):
```
Logica esistente (non modificata in questa fase)
```

#### Endpoint: GET `/api/inventories/:invId/expectations` (linee 745-862)

**All'apertura dell'inventario**:

```
IF Checklist Mode:
  IF inventario vuoto:
    - resetCounters(chk_id)
    - ckp_qta_exp = 0, ckp_qta_unexp = 0, ckp_qta_missing = ckp_qta
  ELSE (inventario ha già item):
    - syncCountersWithInventory(chk_id, inv_id)
    - Ricalcola counter dai dati reali

IF Normal Mode:
  - UPDATE tutti item esistenti: inv_expected = TRUE

IF Stock Mode:
  - Pre-popola inventory_items con item "lost"
```

#### Endpoint: DELETE `/api/inventories/:invId/items` (CLEAR)

```
IF Checklist Mode:
  - resetCounters(chk_id) prima di cancellare item
  - Cancella tutti item da inventory_items
```

---

### 3. Android - ViewModel

**File**: `android-app/.../viewmodel/InventoryScanViewModel.kt`

#### Metodo: `loadExistingStatusCountersSync()` (linee 146-201)

**All'apertura inventario**:
```kotlin
when (inventoryMode) {
  "normal" -> {
    expectedCount = counters.expected_count  // Validated
    unexpectedCount = 0  // NON usato
    lostCount = 0        // NON usato
  }
  "checklist" -> {
    expectedCount = counters.expected_count
    unexpectedCount = counters.unexpected_count
    lostCount = totalExpected - expected_count
  }
  "last_place" -> {
    // Tutti i counter attivi (esistente)
  }
}
```

#### Metodo: `sendTagToInventory()` (linee 365-417)

**Dopo ogni scan**:
```kotlin
when (inventoryMode) {
  "normal" -> {
    expectedCount = counters.expectedCount  // Validated
    unexpectedCount = 0
    lostCount = 0
  }
  "checklist" -> {
    expectedCount = counters.expectedCount
    unexpectedCount = counters.unexpectedCount
    lostCount = counters.lostCount
  }
  "last_place" -> {
    // Logica esistente
  }
}
```

---

### 4. Android - Activity UI

**File**: `android-app/.../InventoryScanActivity.kt`

#### Metodo: `updateModeDisplay()` (linee 212-252)

**MODALITÀ NORMAL**:
```kotlin
- Nasconde: llUnexpectedCounter, llLostCounter
- Mostra: llExpectedCounter
- Label: "Validated" (colore nero)
- Badge: Grigio "Normal"
```

**MODALITÀ CHECKLIST**:
```kotlin
- Mostra: llExpectedCounter, llUnexpectedCounter, llLostCounter
- Label: "Expected" (colore #666666)
- Badge: Verde "Checklist" con totalExpected (es: (8))
```

**MODALITÀ STOCK**:
```kotlin
- Mostra: tutti i counter
- Label: "Expected"
- Badge: Blu "Stock"
```

---

### 5. Android - Layout XML

**File**: `android-app/.../res/layout/activity_inventory_scan.xml`

**Counter aggiunti ID**:
- `llExpectedCounter` (container)
- `llUnexpectedCounter` (container)
- `llLostCounter` (container)
- `tvExpectedLabel` (label dinamica)

---

## COMPORTAMENTO ATTESO

### 📊 INVENTORY NORMAL

#### UI Visibile:
```
┌─────────────────────────────────┐
│  Normal (badge grigio)          │
├─────────────────────────────────┤
│                                 │
│      Tags Counter               │
│         [100]                   │  ← Principale (tutti)
│                                 │
│         [100]                   │  ← Validated (nero)
│       Validated                 │
│                                 │
└─────────────────────────────────┘
```

#### Logica:
- **mode_b (Tutti)**: Tutti i tag → Validated = Principale
- **mode_a (Solo Censiti)**: Solo tag in Items → Validated

#### Database:
```sql
inventory_items:
  - inv_expected = TRUE per tag validati
  - inv_unexpected = FALSE
  - inv_lost = FALSE
```

---

### 📋 INVENTORY CHECKLIST

#### UI Visibile:
```
┌─────────────────────────────────┐
│  Checklist (badge verde) (8)   │  ← Totale attesi
├─────────────────────────────────┤
│                                 │
│      Tags Counter               │
│         [50]                    │  ← Principale (tutti)
│                                 │
│   [30]     [15]      [5]       │
│ Expected Unexpected  Lost       │
│ (verde)  (arancio) (rosso)     │
│                                 │
└─────────────────────────────────┘
```

#### Logica:
- **Ignora mode** - sempre Solo Censiti
- Expected: tag che matchano checklist ≤ ckp_qta
- Unexpected: tag fuori checklist O > ckp_qta
- Lost: ckp_qta - ckp_qta_exp (totalExpected - expected)

#### Database:
```sql
inventory_items:
  - inv_expected = TRUE/FALSE
  - inv_unexpected = TRUE/FALSE
  - inv_lost = FALSE

checklist_products:
  - ckp_qta_exp: incrementato ad ogni tag expected
  - ckp_qta_unexp: incrementato ad ogni tag unexpected
  - ckp_qta_missing: calcolato = ckp_qta - ckp_qta_exp
```

#### Inizializzazione:
```
Prima apertura (inventory_items vuoto):
  - ckp_qta_exp = 0
  - ckp_qta_unexp = 0
  - ckp_qta_missing = ckp_qta (tutti lost)

Riapertura (inventory_items non vuoto):
  - Ricalcola counter dai dati reali
  - Sincronizza ckp_qta_exp con COUNT(inv_expected=TRUE)
```

---

### 📦 INVENTORY STOCK

```
Da implementare (prossimo step)
```

---

## TEST SUGGERITI

### Test 1: Normal Mode con "Tutti"

**Setup**:
1. Crea inventario Normal (inv_chk_id = 0, inv_last = false)
2. Imposta mode = "mode_b" (Tutti)

**Azioni**:
1. Apri inventario
2. Scansiona tag EPC-001 (NON in Items)
3. Scansiona tag EPC-002 (in Items)

**Risultato Atteso**:
- Principale: 2
- Validated: 2
- EPC-001 e EPC-002 entrambi con `inv_expected = TRUE`
- EPC-001 creato automaticamente in Items

---

### Test 2: Normal Mode con "Solo Censiti"

**Setup**:
1. Inventario Normal
2. mode = "mode_a" (Solo Censiti)

**Azioni**:
1. Scansiona EPC-003 (NON in Items)
2. Scansiona EPC-004 (in Items)

**Risultato Atteso**:
- Principale: 1 (solo EPC-004)
- Validated: 1
- EPC-003 IGNORATO (non scritto in inventory_items)
- EPC-004 con `inv_expected = TRUE`

---

### Test 3: Checklist Mode - Prima Apertura

**Setup**:
1. Crea checklist con:
   - Product A: ckp_qta = 3
   - Product B: ckp_qta = 2
2. Crea inventario Checklist (inv_chk_id = checklist_id)

**Azioni**:
1. Apri inventario → UI mostra badge verde "(5)"

**Database Check**:
```sql
SELECT * FROM checklist_products WHERE ckp_chk_id = ?

Risultato:
ckp_qta_exp = 0, ckp_qta_unexp = 0, ckp_qta_missing = 3 (Product A)
ckp_qta_exp = 0, ckp_qta_unexp = 0, ckp_qta_missing = 2 (Product B)
```

**UI Check**:
- Principale: 0
- Expected: 0
- Unexpected: 0
- Lost: 5

---

### Test 4: Checklist Mode - Scan Tag Expected

**Azioni**:
1. Scansiona EPC-A1 (item_product_id = Product A)
2. Scansiona EPC-A2 (item_product_id = Product A)
3. Scansiona EPC-B1 (item_product_id = Product B)

**Risultato Atteso**:

**UI**:
- Principale: 3
- Expected: 3
- Unexpected: 0
- Lost: 2 (5 - 3)

**Database checklist_products**:
```
Product A: ckp_qta_exp = 2, ckp_qta_unexp = 0, ckp_qta_missing = 1
Product B: ckp_qta_exp = 1, ckp_qta_unexp = 0, ckp_qta_missing = 1
```

**Database inventory_items**:
```
EPC-A1: inv_expected = TRUE
EPC-A2: inv_expected = TRUE
EPC-B1: inv_expected = TRUE
```

---

### Test 5: Checklist Mode - Scan Tag Unexpected

**Azioni**:
1. Scansiona EPC-A3 (Product A, ma ckp_qta_exp = 3, quindi superato!)
2. Scansiona EPC-C1 (Product C, NON in checklist)

**Risultato Atteso**:

**UI**:
- Principale: 5 (3 + 2)
- Expected: 3 (invariato)
- Unexpected: 2
- Lost: 2

**Database checklist_products**:
```
Product A: ckp_qta_exp = 3, ckp_qta_unexp = 1
Product C: NON modificato (non in checklist)
```

**Database inventory_items**:
```
EPC-A3: inv_unexpected = TRUE
EPC-C1: inv_unexpected = TRUE
```

---

### Test 6: Checklist Mode - Chiudi e Riapri

**Azioni**:
1. Chiudi inventario
2. Riapri inventario

**Risultato Atteso**:
- Counter persistono (non azzerati!)
- Principale: 5
- Expected: 3
- Unexpected: 2
- Lost: 2
- Backend esegue `syncCountersWithInventory()` per verificare consistenza

---

### Test 7: Checklist Mode - CLEAR

**Azioni**:
1. Premi pulsante CLEAR
2. Conferma

**Risultato Atteso**:

**Database inventory_items**:
```
Tutti record cancellati
```

**Database checklist_products**:
```
Product A: ckp_qta_exp = 0, ckp_qta_unexp = 0, ckp_qta_missing = 3
Product B: ckp_qta_exp = 0, ckp_qta_unexp = 0, ckp_qta_missing = 2
```

**UI**:
- Principale: 0
- Expected: 0
- Unexpected: 0
- Lost: 5 (reset)

---

## DIFFERENZE TRA LE TRE MODALITÀ

| Feature | NORMAL | CHECKLIST | STOCK |
|---------|--------|-----------|-------|
| Badge | Grigio "Normal" | Verde "Checklist (N)" | Blu "Stock" |
| Counter Principale | ✅ Visibile | ✅ Visibile | ✅ Visibile |
| Counter Expected/Validated | ✅ Visibile (nero) | ✅ Visibile (verde) | ✅ Visibile |
| Counter Unexpected | ❌ Nascosto | ✅ Visibile | ✅ Visibile |
| Counter Lost | ❌ Nascosto | ✅ Visibile | ✅ Visibile |
| Rispetta mode setting | ✅ Sì | ❌ No (sempre Solo Censiti) | ✅ Sì |
| Auto-crea tag in Items | ✅ Se mode_b | ❌ No | ✅ Se mode_b |
| Persiste counter | ✅ In inventory_items | ✅ In checklist_products | ✅ In inventory_items |
| Inizializza all'apertura | ✅ UPDATE expected=TRUE | ✅ resetCounters o sync | ✅ Pre-popola lost |

---

## FILE MODIFICATI (Riepilogo)

### Backend:
1. ✅ `backend/src/models/ChecklistProduct.js` (5 nuovi metodi)
2. ✅ `backend/src/controllers/inventoriesController.js`
   - `addScan()` - linee 387-600
   - `getExpectations()` - linee 745-862
   - `clearItems()` - linee 691-716

### Android:
3. ✅ `android-app/.../viewmodel/InventoryScanViewModel.kt`
   - `loadExistingStatusCountersSync()` - linee 146-201
   - `sendTagToInventory()` - linee 365-417
4. ✅ `android-app/.../InventoryScanActivity.kt`
   - `updateModeDisplay()` - linee 212-252
5. ✅ `android-app/.../res/layout/activity_inventory_scan.xml`
   - Aggiunti ID ai container counter

---

## PROBLEMI RISOLTI

1. ✅ **Counter checklist non inizializzati**: Aggiunto `resetCounters()` all'apertura
2. ✅ **Counter checklist non persistono**: Aggiunto `syncCountersWithInventory()` alla riapertura
3. ✅ **CLEAR non resetta checklist**: Aggiunto `resetCounters()` in `clearItems()`
4. ✅ **Normal mode mostra counter inutili**: Nascosti Unexpected e Lost
5. ✅ **Label "Expected" confusa in Normal**: Cambiata in "Validated" (nero)
6. ✅ **Counter persi alla riapertura**: Sincronizzazione automatica con dati reali

---

## PROSSIMI STEP (Modalità Stock)

Da implementare secondo specifiche fornite dall'utente...

