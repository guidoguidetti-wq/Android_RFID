package com.rfid.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rfid.reader.network.BatchScanToInventoryRequest
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.network.ScanToInventoryRequest
import com.rfid.reader.rfid.RFIDManager
import com.rfid.reader.utils.BeepHelper
import com.rfid.reader.utils.SettingsManager
import com.rfid.reader.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel per InventoryScanActivity
 * Gestisce la scansione RFID e l'invio dei tag a un inventario specifico
 * Include logica Expected/Unexpected/Lost per inventari checklist e last-place
 */
class InventoryScanViewModel(application: Application) : AndroidViewModel(application) {
    private val rfidManager = RFIDManager.getInstance(application)
    private val apiService = RetrofitClient.apiService
    private val settingsManager = SettingsManager(application)
    private val sessionManager = SessionManager(application)
    private val beepHelper = BeepHelper.getInstance(application)

    private var currentInventoryId: Int = 0
    private val scannedEpcs = mutableSetOf<String>() // Track EPCs scannati in questa sessione

    // ── Batch debounce ──────────────────────────────────────────────────────
    private val pendingBatch = mutableListOf<String>() // EPCs in attesa di essere inviati
    private var batchJob: Job? = null                  // Job di debounce corrente

    // Contatore totale tag unici letti (esistenti + nuovi)
    private val _totalTagsCount = MutableLiveData<Int>(0)
    val totalTagsCount: LiveData<Int> = _totalTagsCount

    // Contatore tag già presenti nell'inventario all'inizio
    private var initialInventoryCount: Int = 0

    // ========== Expected/Unexpected/Lost Counters ==========
    private val _expectedCount = MutableLiveData<Int>(0)
    val expectedCount: LiveData<Int> = _expectedCount

    private val _unexpectedCount = MutableLiveData<Int>(0)
    val unexpectedCount: LiveData<Int> = _unexpectedCount

    private val _lostCount = MutableLiveData<Int>(0)
    val lostCount: LiveData<Int> = _lostCount

    private val _ignoredCount = MutableLiveData<Int>(0)
    val ignoredCount: LiveData<Int> = _ignoredCount

    // Tag letti ma non ancora confermati dal backend (in pendingBatch o risposta in transito)
    private val _pendingCount = MutableLiveData<Int>(0)
    val pendingCount: LiveData<Int> = _pendingCount

    // Modalità inventario: "normal", "checklist", "last_place" (stock)
    private var inventoryMode: String = "normal"

    // LiveData per modalità inventario (per UI)
    private val _inventoryModeLive = MutableLiveData<String>("normal")
    val inventoryModeLive: LiveData<String> = _inventoryModeLive

    // LiveData per totalExpected (per mostrare nel badge)
    private val _totalExpectedLive = MutableLiveData<Int>(0)
    val totalExpectedLive: LiveData<Int> = _totalExpectedLive

    // ✅ Strutture dati per tracking locale RIMOSSE (gestito dal backend)

    // Stato connessione reader
    private val _readerStatus = MutableLiveData<String>("Disconnected")
    val readerStatus: LiveData<String> = _readerStatus

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> = _isConnected

    // Stato scansione
    private val _isScanning = MutableLiveData<Boolean>(false)
    val isScanning: LiveData<Boolean> = _isScanning

    // Connection progress
    private val _connectionProgress = MutableLiveData<Boolean>(false)
    val connectionProgress: LiveData<Boolean> = _connectionProgress

    init {
        observeRFIDManager()
    }

    /**
     * Imposta l'inventario corrente e carica il count esistente dal DB
     * Carica anche le expectations per la logica Expected/Unexpected/Lost
     */
    fun setInventory(inventoryId: Int) {
        currentInventoryId = inventoryId
        android.util.Log.d(TAG, "Inventory set to: $inventoryId")
        // ✅ SOLO loadExpectations (che chiama loadExistingStatusCounters)
        // Rimuove race condition con loadExistingCount
        loadExpectations()
    }

    /**
     * Carica le expectations dall'API per determinare la modalità dell'inventario
     * e inizializzare i counters Expected/Unexpected/Lost
     * ✅ OTTIMIZZATO: Carica i counter dal DB SUBITO, senza azzerarli prima
     */
    private fun loadExpectations() {
        viewModelScope.launch {
            try {
                android.util.Log.d(TAG, "Loading expectations for inventory $currentInventoryId")
                val response = apiService.getInventoryExpectations(currentInventoryId)

                if (response.isSuccessful) {
                    val data = response.body()!!
                    inventoryMode = data.mode
                    _inventoryModeLive.value = data.mode
                    _totalExpectedLive.value = data.totalExpected
                    android.util.Log.d(TAG, "Inventory mode: $inventoryMode, totalExpected: ${data.totalExpected}")

                    // ✅ Carica counters SINCRONO (nello stesso coroutine, no race condition)
                    when (data.mode) {
                        "checklist", "last_place" -> {
                            android.util.Log.d(TAG, "${data.mode} mode: totalExpected=${data.totalExpected}")
                            // Carica counters esistenti dal DB (senza azzerarli prima)
                            loadExistingStatusCountersSync(data.totalExpected)
                        }
                        else -> {
                            // Normal mode - carica counters dal DB (expected dovrebbe = total)
                            android.util.Log.d(TAG, "Normal mode: no expectations")
                            loadExistingStatusCountersSync(0)
                        }
                    }
                } else {
                    android.util.Log.e(TAG, "Failed to load expectations: ${response.code()}")
                    // Fallback a normal mode
                    inventoryMode = "normal"
                    _inventoryModeLive.value = "normal"
                    _totalExpectedLive.value = 0
                    loadExistingStatusCountersSync(0)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading expectations", e)
                inventoryMode = "normal"
                _inventoryModeLive.value = "normal"
                _totalExpectedLive.value = 0
                loadExistingStatusCountersSync(0)
            }
        }
    }

    /**
     * Carica i contatori existing Expected/Unexpected/Lost dalla tabella inventory_items
     * OTTIMIZZATO: usa endpoint /counters (nessun JOIN) invece di /items-details
     * SINCRONO: chiamato dentro un coroutine esistente (no race condition)
     */
    private suspend fun loadExistingStatusCountersSync(totalExpected: Int) {
        try {
            android.util.Log.d(TAG, "Loading existing status counters for inventory $currentInventoryId (optimized)")

            // OTTIMIZZAZIONE: Fast count-only query (no item details, no JOINs)
            val countersResponse = apiService.getInventoryCounters(currentInventoryId)

            if (countersResponse.isSuccessful) {
                val counters = countersResponse.body()!!

                // ✅ CORREZIONE: Total counter = COUNT(*) di tutti i tag (dal backend)
                // Per Normal mode: tutti i tag in inventory_items
                // Per Checklist/Stock: expected + unexpected (i tag scannati, esclusi lost)
                val totalScanned = when (inventoryMode) {
                    "normal" -> counters.total_count  // Tutti i tag
                    else -> counters.expected_count + counters.unexpected_count  // Solo scannati
                }
                _totalTagsCount.value = totalScanned

                // Ignored = tag letti ma non censiti in Items (nessun flag impostato)
                val ignoredFromDb = Math.max(0, counters.total_count - counters.expected_count - counters.unexpected_count - counters.lost_count)

                // Aggiorna counter in base alla modalità
                when (inventoryMode) {
                    "normal" -> {
                        // ✅ NORMAL MODE: Validated = expected_count
                        _expectedCount.value = counters.expected_count
                        _unexpectedCount.value = 0  // Non usato
                        _lostCount.value = 0        // Non usato
                        _ignoredCount.value = ignoredFromDb
                        android.util.Log.d(TAG, "Normal mode loaded - Total: ${counters.total_count}, Validated: ${counters.expected_count}, Ignored: $ignoredFromDb")
                    }
                    "checklist" -> {
                        // ✅ CHECKLIST MODE: lost_count da DB (pre-popolato come stock mode)
                        _expectedCount.value = counters.expected_count
                        _unexpectedCount.value = counters.unexpected_count
                        _lostCount.value = counters.lost_count
                        _ignoredCount.value = ignoredFromDb
                        android.util.Log.d(TAG, "Checklist mode loaded - Total: $totalScanned, Exp: ${counters.expected_count}, Unexp: ${counters.unexpected_count}, Lost: ${counters.lost_count}, Ignored: $ignoredFromDb")
                    }
                    "last_place" -> {
                        // ✅ STOCK MODE: Logica esistente
                        _expectedCount.value = counters.expected_count
                        _unexpectedCount.value = counters.unexpected_count
                        _lostCount.value = counters.lost_count
                        _ignoredCount.value = ignoredFromDb
                        android.util.Log.d(TAG, "Stock mode loaded - Total: $totalScanned, Exp: ${counters.expected_count}, Unexp: ${counters.unexpected_count}, Lost: ${counters.lost_count}, Ignored: $ignoredFromDb")
                    }
                    else -> {
                        // Fallback
                        _expectedCount.value = counters.expected_count
                        _unexpectedCount.value = counters.unexpected_count
                        _lostCount.value = counters.lost_count
                        _ignoredCount.value = ignoredFromDb
                        android.util.Log.d(TAG, "Unknown mode - using all counters from DB")
                    }
                }

                // OTTIMIZZAZIONE: Load scanned EPCs separately (lightweight query, no product data)
                loadScannedEpcs()
            } else {
                android.util.Log.e(TAG, "Failed to load counters: ${countersResponse.code()}")
                // Fallback: inizializza con valori default
                _expectedCount.value = 0
                _unexpectedCount.value = 0
                _lostCount.value = if (inventoryMode == "checklist") totalExpected else 0
                _ignoredCount.value = 0
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error loading counters", e)
            _expectedCount.value = 0
            _unexpectedCount.value = 0
            _lostCount.value = totalExpected
            _ignoredCount.value = 0
        }
    }

    /**
     * Carica la lista di EPCs già scannerizzati (senza dettagli prodotto)
     * NON popola scannedEpcs - quel set traccia solo tag letti in QUESTA sessione
     * Il backend gestisce già i duplicati (isNew=false se EPC già esistente)
     */
    private suspend fun loadScannedEpcs() {
        try {
            android.util.Log.d(TAG, "Loading existing EPCs for inventory $currentInventoryId")
            val epcsResponse = apiService.getInventoryEpcs(currentInventoryId)

            if (epcsResponse.isSuccessful) {
                val epcs = epcsResponse.body() ?: emptyList()

                // ✅ NON popolare scannedEpcs - deve restare vuoto all'inizio
                // scannedEpcs traccia solo i tag letti DURANTE questa sessione di scansione
                // per evitare di processare lo stesso tag più volte quando il reader
                // legge lo stesso EPC 10 volte in 1 secondo

                android.util.Log.d(TAG, "Found ${epcs.size} existing EPCs in inventory (not added to scannedEpcs)")
            } else {
                android.util.Log.e(TAG, "Failed to load EPCs: ${epcsResponse.code()}")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error loading EPCs", e)
        }
    }

    // ✅ Funzioni di classificazione locale RIMOSSE
    // I contatori vengono ora gestiti completamente dal backend e ricevuti via API response

    /**
     * Osserva i flow del RFIDManager per aggiornare lo stato
     */
    private fun observeRFIDManager() {
        // Osserva connection state
        viewModelScope.launch {
            rfidManager.connectionState.collect { state ->
                val connected = state == RFIDManager.ConnectionState.CONNECTED
                _isConnected.value = connected

                // Update connection progress
                _connectionProgress.value = (state == RFIDManager.ConnectionState.CONNECTING)

                _readerStatus.value = when (state) {
                    RFIDManager.ConnectionState.CONNECTED -> {
                        _connectionProgress.value = false
                        "Reader Connected"
                    }
                    RFIDManager.ConnectionState.CONNECTING -> {
                        _connectionProgress.value = true
                        "Connecting..."
                    }
                    RFIDManager.ConnectionState.DISCONNECTED -> {
                        _connectionProgress.value = false
                        "Reader Disconnected"
                    }
                    RFIDManager.ConnectionState.ERROR -> {
                        _connectionProgress.value = false
                        "Connection Error"
                    }
                }
                android.util.Log.d(TAG, "Connection state: $state, Progress: ${_connectionProgress.value}")
            }
        }

        // Osserva errori
        viewModelScope.launch {
            rfidManager.errorMessage.collect { error ->
                error?.let {
                    android.util.Log.e(TAG, "RFID Error: $it")
                    _readerStatus.value = it
                }
            }
        }

        // Osserva eventi trigger
        viewModelScope.launch {
            var lastTriggerState = false
            rfidManager.triggerPressed.collect { pressed ->
                // Toggle scan quando il trigger viene rilasciato (transizione da pressed a released)
                if (lastTriggerState && !pressed) {
                    android.util.Log.d(TAG, "Trigger released - toggling scan")
                    toggleScan()
                }
                lastTriggerState = pressed
            }
        }

        // Osserva tag letti — usa tagReadFlow (SharedFlow, solo nuove letture hardware, no replay)
        // Evita di iterare l'intera lista accumulata ad ogni aggiornamento RSSI (ogni 30ms)
        viewModelScope.launch {
            rfidManager.tagReadFlow.collect { tagList ->
                android.util.Log.d(TAG, "tagReadFlow: ${tagList.size} tags in batch")

                val minRssi = settingsManager.getMinRssi()
                val epcPrefix = settingsManager.getEpcPrefixFilter()

                tagList.forEach { tag ->
                    val epc = tag.tagID
                    val rssi = tag.peakRSSI

                    if (rssi < minRssi) {
                        android.util.Log.d(TAG, "Tag $epc filtered by RSSI: $rssi < $minRssi")
                        return@forEach
                    }

                    if (epcPrefix.isNotEmpty() && !epc.startsWith(epcPrefix)) {
                        android.util.Log.d(TAG, "Tag $epc filtered by prefix: doesn't match '$epcPrefix'")
                        return@forEach
                    }

                    sendTagToInventory(epc)
                }
            }
        }
    }

    /**
     * Riceve un tag letto dal reader.
     * - Beep + incremento counter principale: IMMEDIATI (client-side)
     * - Validazione backend: BATCH con debounce configurabile (default 300 ms)
     */
    private fun sendTagToInventory(epc: String) {
        // Duplicati: ignora senza beep né backend
        if (scannedEpcs.contains(epc)) {
            android.util.Log.d(TAG, "Tag $epc already scanned - ignoring")
            return
        }

        // Aggiungi subito a scannedEpcs per bloccare ri-letture del reader
        scannedEpcs.add(epc)

        // Incremento counter principale e beep: immediati
        val currentTotal = _totalTagsCount.value ?: 0
        _totalTagsCount.value = currentTotal + 1
        beepHelper.playBeep()
        android.util.Log.d(TAG, "Tag $epc - immediate counter → ${currentTotal + 1}")

        // Normal mode: in mode_b/mode_c tutti i tag sono expected → incrementa subito.
        // In mode_a non sappiamo se il tag è censito finché il backend non risponde:
        // expected/ignored verranno aggiornati da flushBatch().
        if (inventoryMode == "normal") {
            val scanMode = settingsManager.getTagReadingMode()
            if (scanMode == "mode_b" || scanMode == "mode_c") {
                _expectedCount.value = (_expectedCount.value ?: 0) + 1
            }
        }

        // Accoda nel buffer batch
        pendingBatch.add(epc)
        _pendingCount.value = pendingBatch.size

        // Annulla il debounce precedente e riparte
        batchJob?.cancel()
        val delayMs = settingsManager.getBatchDelayMs()
        batchJob = viewModelScope.launch {
            delay(delayMs)
            flushBatch()
        }
    }

    /**
     * Invia al backend tutti gli EPCs accumulati nel buffer.
     * Aggiorna i counter di validazione (Expected/Unexpected/Lost) con la risposta.
     * Su errore: rimuove gli EPCs dal set e decrementa il counter principale per permettere retry.
     */
    private suspend fun flushBatch() {
        if (pendingBatch.isEmpty()) return

        val batch = pendingBatch.toList()
        pendingBatch.clear()
        // _pendingCount NON si azzera qui: i tag sono in volo verso il backend.
        // Si aggiornerà dopo la risposta per riflettere eventuali nuovi tag arrivati nel frattempo.
        android.util.Log.d(TAG, "Flushing batch of ${batch.size} EPCs to inventory $currentInventoryId")

        try {
            val mode = settingsManager.getTagReadingMode()
            val placeId = sessionManager.getUserPlace()
            val zoneId = settingsManager.getInventoryZone()
            val productFilters = if (mode == "mode_a") settingsManager.getActiveProductFilters() else null

            val response = apiService.addBatchScanToInventory(
                currentInventoryId,
                BatchScanToInventoryRequest(
                    epcs = batch,
                    mode = mode,
                    placeId = placeId,
                    zoneId = zoneId,
                    productFilters = productFilters
                )
            )

            if (response.isSuccessful) {
                val counters = response.body()?.counters
                android.util.Log.d(TAG, "Batch validated: ${response.body()?.processedCount} processed - Mode: $inventoryMode")

                if (counters != null) {
                    when (inventoryMode) {
                        "normal" -> {
                            _expectedCount.value = counters.expectedCount
                            _unexpectedCount.value = 0
                            _lostCount.value = 0
                            _ignoredCount.value = counters.ignoredCount
                            android.util.Log.d(TAG, "Normal - Validated: ${counters.expectedCount}, Ignored: ${counters.ignoredCount}")
                        }
                        "checklist", "last_place" -> {
                            _expectedCount.value = counters.expectedCount
                            _unexpectedCount.value = counters.unexpectedCount
                            _lostCount.value = counters.lostCount
                            _ignoredCount.value = counters.ignoredCount
                            android.util.Log.d(TAG, "Mode $inventoryMode - Exp: ${counters.expectedCount}, Unexp: ${counters.unexpectedCount}, Lost: ${counters.lostCount}, Ignored: ${counters.ignoredCount}")
                        }
                        else -> {
                            _expectedCount.value = counters.expectedCount
                            _unexpectedCount.value = counters.unexpectedCount
                            _lostCount.value = counters.lostCount
                            _ignoredCount.value = counters.ignoredCount
                        }
                    }
                    // Pending si aggiorna DOPO la risposta: riflette solo i tag
                    // arrivati mentre questa chiamata era in volo (0 se nessuno)
                    _pendingCount.value = pendingBatch.size
                }
            } else {
                // Errore backend: i tag restano in scannedEpcs (counter non si azzera)
                // L'utente vede il count corretto; i dati verranno salvati al prossimo retry manuale
                android.util.Log.e(TAG, "Batch error: ${response.code()} for ${batch.size} tags (keeping counter, data not saved to DB)")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Batch exception: ${e.message} for ${batch.size} tags (keeping counter)", e)
        }
    }

    /**
     * Connette al reader RFID
     */
    fun connectReader() {
        viewModelScope.launch {
            android.util.Log.d(TAG, "Connecting to RFID reader...")
            rfidManager.connectToReader()
        }
    }

    /**
     * Disconnette dal reader RFID
     */
    fun disconnectReader() {
        stopScan()
        android.util.Log.d(TAG, "Disconnecting from RFID reader")
        rfidManager.disconnect()
    }

    /**
     * Avvia la scansione RFID
     */
    fun startScan() {
        android.util.Log.d(TAG, "Starting scan...")
        viewModelScope.launch {
            // Clear tags già letti (per permettere ri-letture)
            rfidManager.clearTags()
            rfidManager.startInventory()
            _isScanning.value = true
            android.util.Log.d(TAG, "Scan started")
        }
    }

    /**
     * Ferma la scansione RFID e invia immediatamente eventuali tag pendenti nel buffer
     */
    fun stopScan() {
        android.util.Log.d(TAG, "Stopping scan...")
        viewModelScope.launch {
            rfidManager.stopInventory()
            _isScanning.value = false

            // Cancella il debounce e invia subito il batch pendente (se presente)
            batchJob?.cancel()
            batchJob = null
            if (pendingBatch.isNotEmpty()) {
                android.util.Log.d(TAG, "Flushing ${pendingBatch.size} pending tags on scan stop")
                flushBatch()
            }

            android.util.Log.d(TAG, "Scan stopped")
        }
    }

    /**
     * Reset dei contatori per nuova sessione di scansione
     * Ricarica i contatori dal backend
     */
    fun resetCounters() {
        batchJob?.cancel()
        batchJob = null
        pendingBatch.clear()
        scannedEpcs.clear()
        loadExpectations()
        android.util.Log.d(TAG, "Counters reset - reloaded from backend")
    }

    /**
     * Toggle scan (start/stop)
     */
    fun toggleScan() {
        if (_isScanning.value == true) {
            stopScan()
        } else {
            startScan()
        }
    }

    /**
     * Svuota l'inventario eliminando tutti gli items
     * Resets ALL counters including Expected/Unexpected/Lost
     */
    suspend fun clearInventory() {
        try {
            val response = apiService.clearInventory(currentInventoryId)

            if (response.success) {
                // Azzerare counter principale
                _totalTagsCount.postValue(0)
                initialInventoryCount = 0

                // Resettare lista tag scannati in questa sessione
                scannedEpcs.clear()

                // Reset ALL counters (Expected/Unexpected/Lost/Ignored)
                _expectedCount.postValue(0)
                _unexpectedCount.postValue(0)
                _lostCount.postValue(0)
                _ignoredCount.postValue(0)

                // Reload expectations to re-initialize counters properly
                loadExpectations()

                android.util.Log.d(TAG, "Inventory $currentInventoryId cleared successfully. ${response.itemsRemoved} items removed. All counters reset.")
            } else {
                throw Exception("Failed to clear inventory: ${response.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error clearing inventory", e)
            throw e
        }
    }

    /**
     * Chiude l'inventario impostando lo stato a CLOSE
     */
    suspend fun closeInventory() {
        try {
            val response = apiService.closeInventory(currentInventoryId)

            if (response.success) {
                android.util.Log.d(TAG, "Inventory $currentInventoryId closed successfully")
            } else {
                throw Exception("Failed to close inventory")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error closing inventory", e)
            throw e
        }
    }

    /**
     * Elimina l'inventario e tutti gli items associati
     */
    suspend fun deleteInventory() {
        try {
            val response = apiService.deleteInventory(currentInventoryId)

            if (response.success) {
                android.util.Log.d(TAG, "Inventory $currentInventoryId deleted successfully")
            } else {
                throw Exception("Failed to delete inventory: ${response.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error deleting inventory", e)
            throw e
        }
    }

    override fun onCleared() {
        super.onCleared()
        android.util.Log.d(TAG, "ViewModel cleared, disposing RFID manager")
        rfidManager.dispose()
    }

    companion object {
        private const val TAG = "InventoryScanViewModel"
    }
}
