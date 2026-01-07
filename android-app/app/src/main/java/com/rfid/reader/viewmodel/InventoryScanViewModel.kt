package com.rfid.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.network.ScanToInventoryRequest
import com.rfid.reader.rfid.RFIDManager
import com.rfid.reader.utils.SettingsManager
import com.rfid.reader.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * ViewModel per InventoryScanActivity
 * Gestisce la scansione RFID e l'invio dei tag a un inventario specifico
 */
class InventoryScanViewModel(application: Application) : AndroidViewModel(application) {
    private val rfidManager = RFIDManager(application)
    private val apiService = RetrofitClient.apiService
    private val settingsManager = SettingsManager(application)
    private val sessionManager = SessionManager(application)

    private var currentInventoryId: String = ""
    private val scannedEpcs = mutableSetOf<String>() // Track EPCs scannati in questa sessione

    // Contatore totale tag unici letti (esistenti + nuovi)
    private val _totalTagsCount = MutableLiveData<Int>(0)
    val totalTagsCount: LiveData<Int> = _totalTagsCount

    // Contatore tag già presenti nell'inventario all'inizio
    private var initialInventoryCount: Int = 0

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
     */
    fun setInventory(inventoryId: String) {
        currentInventoryId = inventoryId
        android.util.Log.d(TAG, "Inventory set to: $inventoryId")
        loadExistingCount()
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
                        sendTagToInventory(epc)
                    }
                }
            }
        }
    }

    /**
     * Invia un tag scannerizzato al backend per l'inventario corrente
     */
    private fun sendTagToInventory(epc: String) {
        viewModelScope.launch {
            try {
                val mode = settingsManager.getTagReadingMode()
                val placeId = sessionManager.getUserPlace()
                val zoneId = settingsManager.getInventoryZone()

                android.util.Log.d(TAG, "Sending tag $epc to inventory $currentInventoryId (mode: $mode, place: $placeId, zone: $zoneId)")
                val response = apiService.addScanToInventory(
                    currentInventoryId,
                    ScanToInventoryRequest(
                        epc = epc,
                        mode = mode,
                        placeId = placeId,
                        zoneId = zoneId
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    val totalCount = body?.totalCount ?: 0
                    val isNew = body?.isNew ?: false

                    android.util.Log.d(TAG, "Scan sent successfully - Total: $totalCount, IsNew: $isNew")

                    // Aggiorna contatore usando il count dal backend (che è sempre accurato)
                    _totalTagsCount.value = totalCount
                    android.util.Log.d(TAG, "Updated total count from backend: $totalCount")
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
     */
    fun resetCounters() {
        scannedEpcs.clear()
        loadExistingCount()
        android.util.Log.d(TAG, "Counters reset")
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

    override fun onCleared() {
        super.onCleared()
        android.util.Log.d(TAG, "ViewModel cleared, disposing RFID manager")
        rfidManager.dispose()
    }

    companion object {
        private const val TAG = "InventoryScanViewModel"
    }
}
