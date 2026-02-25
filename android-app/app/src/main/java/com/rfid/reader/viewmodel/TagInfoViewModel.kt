package com.rfid.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rfid.reader.network.InventoryItemDetail
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.rfid.RFIDManager
import com.rfid.reader.utils.BeepHelper
import com.rfid.reader.utils.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TagInfoViewModel(application: Application) : AndroidViewModel(application) {
    private val rfidManager = RFIDManager.getInstance(application)
    private val apiService = RetrofitClient.apiService
    private val settingsManager = SettingsManager(application)
    private val beepHelper = BeepHelper.getInstance(application)

    private val _foundTags = MutableLiveData<List<InventoryItemDetail>>(emptyList())
    val foundTags: LiveData<List<InventoryItemDetail>> = _foundTags

    // Immediate counter: updates as soon as a new EPC is read
    private val _rawTagCount = MutableLiveData<Int>(0)
    val rawTagCount: LiveData<Int> = _rawTagCount

    private val _isScanning = MutableLiveData<Boolean>(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val _readerStatus = MutableLiveData<String>("Disconnected")
    val readerStatus: LiveData<String> = _readerStatus

    private val _connectionProgress = MutableLiveData<Boolean>(false)
    val connectionProgress: LiveData<Boolean> = _connectionProgress

    // Cache: null = not registered, non-null = registered with product details
    private val checkedTagsCache = mutableMapOf<String, InventoryItemDetail?>()

    // EPCs shown immediately (before backend validation)
    private val seenEpcs = mutableSetOf<String>()

    // EPCs pending backend fetch (debounce batch)
    private val pendingBatch = mutableSetOf<String>()
    private var debounceJob: Job? = null

    // Product labels loaded once at startup
    private var productLabels: Map<String, String> = emptyMap()

    init {
        loadProductLabels()
        observeRFIDManager()
    }

    private fun loadProductLabels() {
        viewModelScope.launch {
            try {
                val response = apiService.getProductLabels()
                if (response.isSuccessful) {
                    productLabels = response.body()
                        ?.associate { it.pr_fld to (it.pr_lab ?: it.pr_fld) }
                        ?: emptyMap()
                    android.util.Log.d(TAG, "Loaded ${productLabels.size} product labels")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading product labels", e)
            }
        }
    }

    private fun observeRFIDManager() {
        viewModelScope.launch {
            rfidManager.connectionState.collect { state ->
                _isConnected.value = (state == RFIDManager.ConnectionState.CONNECTED)
                _connectionProgress.value = (state == RFIDManager.ConnectionState.CONNECTING)
                _readerStatus.value = when (state) {
                    RFIDManager.ConnectionState.CONNECTED -> "Reader Connected"
                    RFIDManager.ConnectionState.CONNECTING -> "Connecting..."
                    RFIDManager.ConnectionState.DISCONNECTED -> "Reader Disconnected"
                    RFIDManager.ConnectionState.ERROR -> "Connection Error"
                }
            }
        }

        viewModelScope.launch {
            var lastTriggerState = false
            rfidManager.triggerPressed.collect { pressed ->
                if (lastTriggerState && !pressed) {
                    toggleScan()
                }
                lastTriggerState = pressed
            }
        }

        viewModelScope.launch {
            rfidManager.tagReadFlow.collect { tagList ->
                tagList.forEach { tag ->
                    onTagRead(tag.tagID)
                }
            }
        }
    }

    /**
     * Phase 1 (immediate): increment counter, beep, add placeholder for mode_b/c.
     * Phase 2 (debounced): fetch backend data and update list with real details.
     */
    private fun onTagRead(epc: String) {
        if (seenEpcs.contains(epc)) return  // Already handled this EPC

        seenEpcs.add(epc)
        _rawTagCount.postValue(seenEpcs.size)
        beepHelper.playBeep()

        val mode = settingsManager.getTagReadingMode()

        if (checkedTagsCache.containsKey(epc)) {
            // Already fetched from backend — apply directly
            applyModeAndShow(epc, checkedTagsCache[epc], mode)
        } else {
            // Show placeholder immediately for mode_b/c, then schedule fetch
            if (mode == "mode_b" || mode == "mode_c") {
                addOrUpdateInList(InventoryItemDetail(epc, "...", null, null, null, null))
            }
            pendingBatch.add(epc)
            scheduleDebounce()
        }
    }

    private fun scheduleDebounce() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(settingsManager.getTagInfoDelayMs())
            flushPendingBatch()
        }
    }

    private suspend fun flushPendingBatch() {
        val toFetch = pendingBatch.toSet()
        pendingBatch.clear()

        for (epc in toFetch) {
            if (checkedTagsCache.containsKey(epc)) continue

            try {
                val response = apiService.getItemByEpc(epc)
                if (response.isSuccessful) {
                    val itemResp = response.body()!!
                    var detail = InventoryItemDetail(
                        epc = epc,
                        product_id = itemResp.item_product_id,
                        fld01 = null,
                        fld02 = null,
                        fld03 = null,
                        fldd01 = null
                    )
                    val prodId = itemResp.item_product_id
                    if (prodId != null) {
                        val prodResp = apiService.getProductById(prodId)
                        if (prodResp.isSuccessful) {
                            val p = prodResp.body()
                            detail = detail.copy(
                                fld01 = p?.fld01,
                                fld02 = p?.fld02,
                                fld03 = p?.fld03,
                                fldd01 = p?.fldd01
                            )
                        }
                    }
                    checkedTagsCache[epc] = detail
                    applyModeAndShow(epc, detail, settingsManager.getTagReadingMode())
                } else {
                    checkedTagsCache[epc] = null
                    applyModeAndShow(epc, null, settingsManager.getTagReadingMode())
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error fetching tag $epc", e)
            }
        }
    }

    private fun applyModeAndShow(epc: String, item: InventoryItemDetail?, mode: String) {
        when (mode) {
            "mode_a" -> {
                if (item != null) {
                    addOrUpdateInList(item)
                } else {
                    removeFromList(epc)
                }
            }
            else -> {  // mode_b, mode_c, default
                if (item != null) {
                    addOrUpdateInList(item)
                } else {
                    addOrUpdateInList(InventoryItemDetail(epc, "NON CENSITO", null, null, null, null))
                }
            }
        }
    }

    private fun addOrUpdateInList(item: InventoryItemDetail) {
        val currentList = _foundTags.value?.toMutableList() ?: mutableListOf()
        val idx = currentList.indexOfFirst { it.epc == item.epc }
        if (idx >= 0) {
            currentList[idx] = item  // Replace placeholder with real data
        } else {
            currentList.add(0, item)  // Insert new at top
        }
        _foundTags.postValue(currentList)
    }

    private fun removeFromList(epc: String) {
        val currentList = _foundTags.value?.toMutableList() ?: mutableListOf()
        if (currentList.removeAll { it.epc == epc }) {
            _foundTags.postValue(currentList)
        }
    }

    fun connectReader() {
        _connectionProgress.value = true
        _readerStatus.value = "Connecting..."
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)
            rfidManager.connectToReader()
        }
    }

    fun disconnectReader() {
        stopScan()
        rfidManager.disconnect()
    }

    fun toggleScan() {
        if (_isScanning.value == true) {
            stopScan()
        } else {
            startScan()
        }
    }

    private fun startScan() {
        viewModelScope.launch {
            rfidManager.startInventory()
            _isScanning.postValue(true)
        }
    }

    private fun stopScan() {
        viewModelScope.launch {
            rfidManager.stopInventory()
            _isScanning.postValue(false)
        }
    }

    fun clearTags() {
        debounceJob?.cancel()
        rfidManager.clearTags()
        _foundTags.value = emptyList()
        _rawTagCount.value = 0
        checkedTagsCache.clear()
        seenEpcs.clear()
        pendingBatch.clear()
    }

    override fun onCleared() {
        super.onCleared()
        rfidManager.dispose()
    }

    companion object {
        private const val TAG = "TagInfoViewModel"
    }
}
