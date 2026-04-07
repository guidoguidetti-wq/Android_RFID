package com.rfid.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rfid.reader.network.LettureBatchScanRequest
import com.rfid.reader.network.LettureCommitRequest
import com.rfid.reader.network.LettureInitRequest
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.rfid.RFIDManager
import com.rfid.reader.utils.BeepHelper
import com.rfid.reader.utils.SettingsManager
import com.rfid.reader.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel per LettureSessionActivity.
 * Gestisce la sessione di lettura RFID usando inventory_items_temp (key = userId).
 * Supporta modalità: normal (no checklist), checklist SKU, checklist EPC.
 */
class LettureSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val rfidManager     = RFIDManager.getInstance(application)
    private val apiService      = RetrofitClient.apiService
    private val settingsManager = SettingsManager(application)
    private val sessionManager  = SessionManager(application)
    private val beepHelper      = BeepHelper.getInstance(application)

    // ── Parametri sessione ──────────────────────────────────────────────────
    private var userId:        String  = ""
    private var checklistMode: String  = "none"   // "none" | "sku" | "epc"
    private var chkId:         Int?    = null
    private var sessionMode:   String  = "normal" // "normal" | "checklist" | "checklist_epc"

    // ── Batch debounce ──────────────────────────────────────────────────────
    private val scannedEpcs  = mutableSetOf<String>()
    private val pendingBatch = mutableListOf<String>()
    private var batchJob: Job? = null

    // ── LiveData counters ───────────────────────────────────────────────────
    private val _totalTagsCount  = MutableLiveData<Int>(0)
    val totalTagsCount: LiveData<Int> = _totalTagsCount

    private val _expectedCount   = MutableLiveData<Int>(0)
    val expectedCount: LiveData<Int> = _expectedCount

    private val _unexpectedCount = MutableLiveData<Int>(0)
    val unexpectedCount: LiveData<Int> = _unexpectedCount

    private val _lostCount       = MutableLiveData<Int>(0)
    val lostCount: LiveData<Int> = _lostCount

    private val _ignoredCount    = MutableLiveData<Int>(0)
    val ignoredCount: LiveData<Int> = _ignoredCount

    private val _pendingCount    = MutableLiveData<Int>(0)
    val pendingCount: LiveData<Int> = _pendingCount

    private val _totalExpectedLive = MutableLiveData<Int>(0)
    val totalExpectedLive: LiveData<Int> = _totalExpectedLive

    private val _sessionModeLive = MutableLiveData<String>("normal")
    val sessionModeLive: LiveData<String> = _sessionModeLive

    // ── Reader state ────────────────────────────────────────────────────────
    private val _readerStatus = MutableLiveData<String>("Disconnected")
    val readerStatus: LiveData<String> = _readerStatus

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val _isScanning = MutableLiveData<Boolean>(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _connectionProgress = MutableLiveData<Boolean>(false)
    val connectionProgress: LiveData<Boolean> = _connectionProgress

    // ── Init result ─────────────────────────────────────────────────────────
    private val _initError = MutableLiveData<String?>(null)
    val initError: LiveData<String?> = _initError

    private val _commitResult = MutableLiveData<Pair<Boolean, String>?>()
    val commitResult: LiveData<Pair<Boolean, String>?> = _commitResult

    init {
        observeRFIDManager()
    }

    /**
     * Inizializza la sessione: pulisce temp, pre-popola lost per EPC checklist.
     */
    fun initSession(userId: String, checklistMode: String, checklistCode: String?) {
        this.userId = userId
        this.checklistMode = checklistMode

        viewModelScope.launch {
            try {
                val response = apiService.initLettureSession(
                    LettureInitRequest(userId = userId, checklistMode = checklistMode, checklistCode = checklistCode)
                )
                if (response.isSuccessful) {
                    val body = response.body()!!
                    sessionMode = body.mode
                    chkId = body.chkId
                    _sessionModeLive.value = body.mode
                    _totalExpectedLive.value = body.totalExpected
                    loadCounters(body.totalExpected)
                    android.util.Log.d(TAG, "Session init: mode=${body.mode}, totalExpected=${body.totalExpected}")
                } else {
                    val err = "Errore init sessione: ${response.code()}"
                    _initError.value = err
                    android.util.Log.e(TAG, err)
                }
            } catch (e: Exception) {
                _initError.value = "Errore di rete: ${e.message}"
                android.util.Log.e(TAG, "Init session error", e)
            }
        }
    }

    private suspend fun loadCounters(totalExpected: Int) {
        try {
            val response = apiService.getLettureCounters(userId)
            if (response.isSuccessful) {
                val c = response.body()!!
                val totalScanned = when (sessionMode) {
                    "normal" -> c.total_count
                    else -> c.expected_count + c.unexpected_count
                }
                _totalTagsCount.value = totalScanned

                when (sessionMode) {
                    "normal" -> {
                        _expectedCount.value   = c.expected_count
                        _unexpectedCount.value = 0
                        _lostCount.value       = 0
                        _ignoredCount.value    = maxOf(0, c.total_count - c.expected_count - c.unexpected_count - c.lost_count)
                    }
                    "checklist" -> {
                        _expectedCount.value   = c.expected_count
                        _unexpectedCount.value = c.unexpected_count
                        _lostCount.value       = maxOf(0, totalExpected - c.expected_count)
                        _ignoredCount.value    = 0
                    }
                    "checklist_epc" -> {
                        _expectedCount.value   = c.expected_count
                        _unexpectedCount.value = c.unexpected_count
                        _lostCount.value       = c.lost_count
                        _ignoredCount.value    = maxOf(0, c.total_count - c.expected_count - c.unexpected_count - c.lost_count)
                    }
                    else -> {
                        _expectedCount.value   = c.expected_count
                        _unexpectedCount.value = c.unexpected_count
                        _lostCount.value       = c.lost_count
                        _ignoredCount.value    = 0
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error loading counters", e)
        }
    }

    private fun observeRFIDManager() {
        viewModelScope.launch {
            rfidManager.connectionState.collect { state ->
                _isConnected.value  = state == RFIDManager.ConnectionState.CONNECTED
                _connectionProgress.value = state == RFIDManager.ConnectionState.CONNECTING
                _readerStatus.value = when (state) {
                    RFIDManager.ConnectionState.CONNECTED    -> "Reader Connected"
                    RFIDManager.ConnectionState.CONNECTING   -> "Connecting..."
                    RFIDManager.ConnectionState.DISCONNECTED -> "Reader Disconnected"
                    RFIDManager.ConnectionState.ERROR        -> "Connection Error"
                }
            }
        }

        viewModelScope.launch {
            rfidManager.errorMessage.collect { error ->
                error?.let { _readerStatus.value = it }
            }
        }

        viewModelScope.launch {
            var lastTrigger = false
            rfidManager.triggerPressed.collect { pressed ->
                if (lastTrigger && !pressed) toggleScan()
                lastTrigger = pressed
            }
        }

        viewModelScope.launch {
            rfidManager.tagReadFlow.collect { tagList ->
                val minRssi   = settingsManager.getMinRssi()
                val epcPrefix = settingsManager.getEpcPrefixFilter()

                tagList.forEach { tag ->
                    val epc  = tag.tagID
                    val rssi = tag.peakRSSI
                    if (rssi < minRssi) return@forEach
                    if (epcPrefix.isNotEmpty() && !epc.startsWith(epcPrefix)) return@forEach
                    sendTagToSession(epc)
                }
            }
        }
    }

    private fun sendTagToSession(epc: String) {
        if (scannedEpcs.contains(epc)) return
        scannedEpcs.add(epc)

        _totalTagsCount.value = (_totalTagsCount.value ?: 0) + 1
        beepHelper.playBeep()

        if (sessionMode == "normal") {
            _expectedCount.value = (_expectedCount.value ?: 0) + 1
        }

        pendingBatch.add(epc)
        _pendingCount.value = pendingBatch.size

        batchJob?.cancel()
        val delayMs = settingsManager.getBatchDelayMs()
        batchJob = viewModelScope.launch {
            delay(delayMs)
            flushBatch()
        }
    }

    private suspend fun flushBatch() {
        if (pendingBatch.isEmpty()) return
        val batch = pendingBatch.toList()
        pendingBatch.clear()

        try {
            val readingMode = settingsManager.getTagReadingMode()
            val response = apiService.lettureBatchScan(
                LettureBatchScanRequest(
                    userId        = userId,
                    epcs          = batch,
                    checklistMode = sessionMode,
                    chkId         = chkId,
                    mode          = readingMode,
                    placeId       = sessionManager.getUserPlace(),
                    zoneId        = null
                )
            )

            if (response.isSuccessful) {
                val counters = response.body()?.counters
                if (counters != null) {
                    when (sessionMode) {
                        "normal" -> {
                            _expectedCount.value   = counters.expectedCount
                            _unexpectedCount.value = 0
                            _lostCount.value       = 0
                            _ignoredCount.value    = counters.ignoredCount
                        }
                        "checklist" -> {
                            val lost = maxOf(0, (_totalExpectedLive.value ?: 0) - counters.expectedCount)
                            _expectedCount.value   = counters.expectedCount
                            _unexpectedCount.value = counters.unexpectedCount
                            _lostCount.value       = lost
                            _ignoredCount.value    = counters.ignoredCount
                        }
                        "checklist_epc" -> {
                            _expectedCount.value   = counters.expectedCount
                            _unexpectedCount.value = counters.unexpectedCount
                            _lostCount.value       = counters.lostCount
                            _ignoredCount.value    = counters.ignoredCount
                        }
                        else -> {
                            _expectedCount.value   = counters.expectedCount
                            _unexpectedCount.value = counters.unexpectedCount
                            _lostCount.value       = counters.lostCount
                            _ignoredCount.value    = counters.ignoredCount
                        }
                    }
                }
            } else {
                android.util.Log.e(TAG, "Batch error: ${response.code()}")
            }
            _pendingCount.value = pendingBatch.size
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Batch exception", e)
        }
    }

    fun connectReader() {
        viewModelScope.launch { rfidManager.connectToReader() }
    }

    fun disconnectReader() {
        stopScan()
        rfidManager.disconnect()
    }

    fun startScan() {
        viewModelScope.launch {
            rfidManager.clearTags()
            rfidManager.startInventory()
            _isScanning.value = true
        }
    }

    fun stopScan() {
        viewModelScope.launch {
            rfidManager.stopInventory()
            _isScanning.value = false
            batchJob?.cancel()
            batchJob = null
            if (pendingBatch.isNotEmpty()) flushBatch()
        }
    }

    fun toggleScan() {
        if (_isScanning.value == true) stopScan() else startScan()
    }

    /**
     * CLEAR: reimposta la sessione temp (mantiene il modo checklist).
     */
    suspend fun clearSession() {
        batchJob?.cancel()
        pendingBatch.clear()
        scannedEpcs.clear()
        _pendingCount.postValue(0)

        val response = apiService.clearLettureSession(
            userId        = userId,
            checklistMode = sessionMode.takeIf { it != "normal" },
            chkId         = chkId
        )
        if (response.isSuccessful) {
            _totalTagsCount.postValue(0)
            _expectedCount.postValue(0)
            _unexpectedCount.postValue(0)
            _lostCount.postValue(_totalExpectedLive.value ?: 0)
            _ignoredCount.postValue(0)
        }
    }

    /**
     * CONFIRM: conferma la sessione e registra movimenti/trasferimento.
     */
    fun commitSession(
        registraTransferimento: Boolean,
        destPlaceId: String?,
        destZoneId: String?,
        riferimento: String?,
        note: String?,
        registraSoloExpected: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                val response = apiService.commitLettureSession(
                    LettureCommitRequest(
                        userId                 = userId,
                        registraTransferimento = registraTransferimento,
                        destPlaceId            = destPlaceId,
                        destZoneId             = destZoneId,
                        riferimento            = riferimento,
                        note                   = note,
                        readerName             = sessionManager.getUserName(),
                        registraSoloExpected   = registraSoloExpected
                    )
                )
                if (response.isSuccessful) {
                    val count = response.body()?.processedCount ?: 0
                    _commitResult.value = Pair(true, "Confermati $count tag")
                } else {
                    _commitResult.value = Pair(false, "Errore commit: ${response.code()}")
                }
            } catch (e: Exception) {
                _commitResult.value = Pair(false, "Errore di rete: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        rfidManager.dispose()
    }

    companion object {
        private const val TAG = "LettureSessionVM"
    }
}
