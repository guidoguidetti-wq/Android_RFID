package com.rfid.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.network.ScanToInventoryRequest
import com.rfid.reader.rfid.RFIDManager
import com.rfid.reader.utils.BeepHelper
import com.rfid.reader.utils.SettingsManager
import com.rfid.reader.utils.SessionManager
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
        loadExistingCount()
        loadExpectations()
    }

    /**
     * Carica il conteggio esistente di tag nel DB per questo inventario
     */
    private fun loadExistingCount() {
        viewModelScope.launch {
            try {
                val response = apiService.getInventoryItemsCount(currentInventoryId)
                if (response.isSuccessful) {
                    val count = response.body()?.get("count") ?: 0
                    initialInventoryCount = count
                    _totalTagsCount.value = count
                    android.util.Log.d(TAG, "Initial inventory count loaded: $count")
                } else {
                    android.util.Log.e(TAG, "Failed to load existing count: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading existing count", e)
            }
        }
    }

    /**
     * Carica le expectations dall'API per determinare la modalità dell'inventario
     * e inizializzare i counters Expected/Unexpected/Lost
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

                    when (data.mode) {
                        "checklist" -> {
                            android.util.Log.d(TAG, "Checklist mode: ${data.checklistProducts.size} products, totalExpected=${data.totalExpected}")
                            // Initialize counters: all items start as "lost" until scanned
                            _expectedCount.value = 0
                            _unexpectedCount.value = 0
                            _lostCount.value = data.totalExpected
                            // Then load existing status counters from DB (will update based on already scanned items)
                            loadExistingStatusCounters(data.totalExpected)
                        }
                        "last_place" -> {
                            android.util.Log.d(TAG, "Last-place (Stock) mode: ${data.expectedItems.size} expected EPCs")
                            // Initialize counters: all items start as "lost" until scanned
                            _expectedCount.value = 0
                            _unexpectedCount.value = 0
                            _lostCount.value = data.totalExpected
                            // Then load existing status counters from DB (will update based on already scanned items)
                            loadExistingStatusCounters(data.totalExpected)
                        }
                        else -> {
                            // Normal mode - reset tutti i counters
                            _expectedCount.value = 0
                            _unexpectedCount.value = 0
                            _lostCount.value = 0
                            _totalExpectedLive.value = 0
                            _inventoryModeLive.value = "normal"
                            android.util.Log.d(TAG, "Normal mode: no expectations")
                        }
                    }
                } else {
                    android.util.Log.e(TAG, "Failed to load expectations: ${response.code()}")
                    // Fallback a normal mode
                    inventoryMode = "normal"
                    _inventoryModeLive.value = "normal"
                    _totalExpectedLive.value = 0
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading expectations", e)
                inventoryMode = "normal"
                _inventoryModeLive.value = "normal"
                _totalExpectedLive.value = 0
            }
        }
    }

    /**
     * Carica i contatori existing Expected/Unexpected/Lost dalla tabella inventory_items
     */
    private fun loadExistingStatusCounters(totalExpected: Int) {
        viewModelScope.launch {
            try {
                android.util.Log.d(TAG, "Loading existing status counters for inventory $currentInventoryId")
                val response = apiService.getInventoryItemsDetails(currentInventoryId)

                if (response.isSuccessful) {
                    val items = response.body() ?: emptyList()

                    // Conta per status dalla tabella (ora include inv_lost)
                    val expCount = items.count { it.inv_expected == true }
                    val unexpCount = items.count { it.inv_unexpected == true }
                    val lostCountFromDB = items.count { it.inv_lost == true }

                    _expectedCount.value = expCount
                    _unexpectedCount.value = unexpCount

                    // ✅ Per checklist mode: calcola lost come totalExpected - expectedCount
                    // Per last_place mode: usa il valore dal DB
                    if (inventoryMode == "checklist") {
                        _lostCount.value = Math.max(0, totalExpected - expCount)
                        android.util.Log.d(TAG, "Checklist mode: Lost = totalExpected($totalExpected) - expected($expCount) = ${_lostCount.value}")
                    } else {
                        _lostCount.value = lostCountFromDB
                        android.util.Log.d(TAG, "Stock mode: Lost from DB = $lostCountFromDB")
                    }

                    // Aggiungi agli scannedEpcs solo se NON è lost (per permettere di ri-scansionarli)
                    items.forEach { item ->
                        if (item.inv_lost != true) {
                            scannedEpcs.add(item.epc)
                        }
                    }

                    android.util.Log.d(TAG, "Loaded existing counters - Exp: $expCount, Unexp: $unexpCount, Lost: ${_lostCount.value}")
                } else {
                    android.util.Log.e(TAG, "Failed to load existing status counters: ${response.code()}")
                    // Fallback: inizializza con valori default
                    _expectedCount.value = 0
                    _unexpectedCount.value = 0
                    _lostCount.value = totalExpected
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading existing status counters", e)
                _expectedCount.value = 0
                _unexpectedCount.value = 0
                _lostCount.value = totalExpected
            }
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

        // Osserva tag letti
        viewModelScope.launch {
            rfidManager.tags.collect { tagList ->
                android.util.Log.d(TAG, "Tags flow collected: ${tagList.size} tags")

                // Per ogni tag letto
                tagList.forEach { tag ->
                    val epc = tag.tagID
                    val rssi = tag.peakRSSI

                    // Applica filtri RSSI
                    val minRssi = settingsManager.getMinRssi()
                    if (rssi < minRssi) {
                        android.util.Log.d(TAG, "Tag $epc filtered by RSSI: $rssi < $minRssi")
                        return@forEach
                    }

                    // Applica filtro prefisso EPC
                    val epcPrefix = settingsManager.getEpcPrefixFilter()
                    if (epcPrefix.isNotEmpty() && !epc.startsWith(epcPrefix)) {
                        android.util.Log.d(TAG, "Tag $epc filtered by prefix: doesn't match '$epcPrefix'")
                        return@forEach
                    }

                    // Se passa i filtri e non è già scannerizzato in questa sessione
                    if (!scannedEpcs.contains(epc)) {
                        android.util.Log.d(TAG, "New unique tag detected: $epc (RSSI: $rssi)")
                        scannedEpcs.add(epc)

                        // ✅ Beep SOLO su nuovo EPC rilevato
                        beepHelper.playBeep()

                        sendTagToInventory(epc)
                    }
                }
            }
        }
    }

    /**
     * Invia un tag scannerizzato al backend per l'inventario corrente
     * e classifica l'EPC per aggiornare Expected/Unexpected/Lost
     */
    private fun sendTagToInventory(epc: String) {
        viewModelScope.launch {
            try {
                val mode = settingsManager.getTagReadingMode()
                val placeId = sessionManager.getUserPlace()
                val zoneId = settingsManager.getInventoryZone()

                // NUOVO: Ottieni filtri prodotto attivi (solo se mode_a)
                val productFilters = if (mode == "mode_a") {
                    settingsManager.getActiveProductFilters()
                } else {
                    null
                }

                android.util.Log.d(TAG, "Sending tag $epc to inventory $currentInventoryId (mode: $mode, filters: $productFilters)")
                val response = apiService.addScanToInventory(
                    currentInventoryId,
                    ScanToInventoryRequest(
                        epc = epc,
                        mode = mode,
                        placeId = placeId,
                        zoneId = zoneId,
                        productFilters = productFilters  // NUOVO
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    val totalCount = body?.totalCount ?: 0
                    val isNew = body?.isNew ?: false
                    val productId = body?.productId
                    val counters = body?.counters

                    android.util.Log.d(TAG, "Scan sent successfully - Total: $totalCount, IsNew: $isNew, ProductId: $productId")

                    // Aggiorna contatore usando il count dal backend (che è sempre accurato)
                    _totalTagsCount.value = totalCount

                    // ✅ NUOVO: Usa i contatori dal backend invece di calcolarli localmente
                    if (counters != null) {
                        _expectedCount.value = counters.expectedCount
                        _unexpectedCount.value = counters.unexpectedCount
                        _lostCount.value = counters.lostCount
                        android.util.Log.d(TAG, "Updated counters from backend - Exp: ${counters.expectedCount}, Unexp: ${counters.unexpectedCount}, Lost: ${counters.lostCount}")
                    }
                } else {
                    android.util.Log.e(TAG, "Error sending scan: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error adding scan: ${e.message}", e)
            }
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
     * Ferma la scansione RFID
     */
    fun stopScan() {
        android.util.Log.d(TAG, "Stopping scan...")
        viewModelScope.launch {
            rfidManager.stopInventory()
            _isScanning.value = false
            android.util.Log.d(TAG, "Scan stopped")
        }
    }

    /**
     * Reset dei contatori per nuova sessione di scansione
     * Ricarica i contatori dal backend
     */
    fun resetCounters() {
        scannedEpcs.clear()

        // Ricarica conteggio e expectations
        loadExistingCount()
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

                // Reset ALL counters (Expected/Unexpected/Lost)
                _expectedCount.postValue(0)
                _unexpectedCount.postValue(0)
                _lostCount.postValue(0)

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
