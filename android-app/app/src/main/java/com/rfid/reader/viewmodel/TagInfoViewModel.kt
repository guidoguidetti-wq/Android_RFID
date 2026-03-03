package com.rfid.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rfid.reader.network.InventoryItemDetail
import com.rfid.reader.network.ItemResponse
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.rfid.RFIDManager
import com.rfid.reader.utils.BeepHelper
import com.rfid.reader.utils.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TagInfoViewModel(application: Application) : AndroidViewModel(application) {
    private val rfidManager = RFIDManager.getInstance(application)
    private val apiService = RetrofitClient.apiService
    private val settingsManager = SettingsManager(application)
    private val beepHelper = BeepHelper.getInstance(application)

    private val _foundTags = MutableLiveData<List<InventoryItemDetail>>(emptyList())
    val foundTags: LiveData<List<InventoryItemDetail>> = _foundTags

    // Immediate counter: updates as soon as a new EPC is read (before any HTTP call)
    private val _rawTagCount = MutableLiveData<Int>(0)
    val rawTagCount: LiveData<Int> = _rawTagCount

    // Tags read but not registered in Items (updated after each batch flush)
    private val _ignoredCount = MutableLiveData<Int>(0)
    val ignoredCount: LiveData<Int> = _ignoredCount

    private val _isScanning = MutableLiveData<Boolean>(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val _readerStatus = MutableLiveData<String>("Disconnected")
    val readerStatus: LiveData<String> = _readerStatus

    private val _connectionProgress = MutableLiveData<Boolean>(false)
    val connectionProgress: LiveData<Boolean> = _connectionProgress

    // Cache: null = not registered in Items, non-null = registered with product details
    private val checkedTagsCache = mutableMapOf<String, InventoryItemDetail?>()

    // EPCs seen so far (immediate dedup)
    private val seenEpcs = mutableSetOf<String>()

    // EPCs queued for backend fetch (debounce batch)
    private val pendingBatch = mutableSetOf<String>()
    private var debounceJob: Job? = null

    // Prevents cancellation of flushPendingBatch() while it is executing
    private var isFlushing = false

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
                if (lastTriggerState && !pressed) toggleScan()
                lastTriggerState = pressed
            }
        }

        viewModelScope.launch {
            rfidManager.tagReadFlow.collect { tagList ->
                tagList.forEach { tag -> onTagRead(tag.tagID) }
            }
        }
    }

    /**
     * Phase 1 (immediate): increment counter and beep.
     * Phase 2 (debounced): all pending EPCs fetched IN PARALLEL from backend.
     * Only EPCs registered in Items appear in the list; unregistered = Ignored.
     */
    private fun onTagRead(epc: String) {
        if (seenEpcs.contains(epc)) return

        seenEpcs.add(epc)
        _rawTagCount.postValue(seenEpcs.size)
        beepHelper.playBeep()

        if (checkedTagsCache.containsKey(epc)) {
            applyModeAndShow(epc, checkedTagsCache[epc])
        } else {
            pendingBatch.add(epc)
            scheduleDebounce()
        }
    }

    private fun scheduleDebounce() {
        if (isFlushing) {
            // A flush is already running; new EPCs stay in pendingBatch and will be
            // picked up when the current flush completes (see finally block below).
            return
        }
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(settingsManager.getTagInfoDelayMs())
            isFlushing = true
            try {
                flushPendingBatch()
            } finally {
                isFlushing = false
                // If new tags arrived while flushing, process them now
                if (pendingBatch.isNotEmpty()) {
                    scheduleDebounce()
                }
            }
        }
    }

    /**
     * Fetches all pending EPCs in parallel:
     * 1) All item lookups in parallel (1 call per EPC)
     * 2) All unique product lookups in parallel (1 call per unique product_id)
     * This reduces N×2 sequential calls to ~2 parallel bursts.
     */
    private suspend fun flushPendingBatch() {
        val toFetch = pendingBatch.filter { !checkedTagsCache.containsKey(it) }
        pendingBatch.clear()
        if (toFetch.isEmpty()) return

        // Step 1: fetch all items in parallel
        val itemResults: List<Pair<String, ItemResponse?>> = coroutineScope {
            toFetch.map { epc ->
                async {
                    try {
                        val resp = apiService.getItemByEpc(epc)
                        if (resp.isSuccessful) epc to resp.body() else epc to null
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Error fetching item $epc", e)
                        epc to null
                    }
                }
            }.awaitAll()
        }

        // Step 2: collect unique product IDs and fetch them in parallel
        val uniqueProductIds = itemResults
            .mapNotNull { (_, item) -> item?.item_product_id }
            .toSet()

        val productMap = coroutineScope {
            uniqueProductIds.map { prodId ->
                async {
                    try {
                        val resp = apiService.getProductById(prodId)
                        if (resp.isSuccessful) prodId to resp.body() else prodId to null
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        prodId to null
                    }
                }
            }.awaitAll()
        }.toMap()

        // Step 3: populate cache for all fetched EPCs
        for ((epc, itemBody) in itemResults) {
            if (itemBody != null) {
                val prodId = itemBody.item_product_id
                val product = if (prodId != null) productMap[prodId] else null
                checkedTagsCache[epc] = InventoryItemDetail(
                    epc = epc,
                    product_id = prodId,
                    fld01 = product?.fld01,
                    fld02 = product?.fld02,
                    fld03 = product?.fld03,
                    fldd01 = product?.fldd01
                )
            } else {
                checkedTagsCache[epc] = null
            }
        }

        // Step 4: single batch update of the list (avoids race condition from parallel postValue)
        val currentList = _foundTags.value?.toMutableList() ?: mutableListOf()
        for ((epc, _) in itemResults) {
            val item = checkedTagsCache[epc]
            if (item != null) {
                val idx = currentList.indexOfFirst { it.epc == epc }
                if (idx >= 0) currentList[idx] = item else currentList.add(0, item)
            } else {
                currentList.removeAll { it.epc == epc }
            }
        }
        _foundTags.postValue(currentList)

        // Update ignored count: EPCs confirmed as NOT in Items
        _ignoredCount.postValue(checkedTagsCache.values.count { it == null })
    }

    // Registered items go in the list; unregistered are counted as Ignored only.
    private fun applyModeAndShow(epc: String, item: InventoryItemDetail?) {
        if (item != null) addOrUpdateInList(item) else removeFromList(epc)
    }

    private fun addOrUpdateInList(item: InventoryItemDetail) {
        val currentList = _foundTags.value?.toMutableList() ?: mutableListOf()
        val idx = currentList.indexOfFirst { it.epc == item.epc }
        if (idx >= 0) currentList[idx] = item else currentList.add(0, item)
        _foundTags.postValue(currentList)
    }

    private fun removeFromList(epc: String) {
        val currentList = _foundTags.value?.toMutableList() ?: mutableListOf()
        if (currentList.removeAll { it.epc == epc }) _foundTags.postValue(currentList)
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
        if (_isScanning.value == true) stopScan() else startScan()
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
            // Flush immediately without waiting for the debounce timer,
            // like InventoryScanViewModel does on stop
            if (pendingBatch.isNotEmpty() && !isFlushing) {
                debounceJob?.cancel()
                debounceJob = viewModelScope.launch {
                    isFlushing = true
                    try {
                        flushPendingBatch()
                    } finally {
                        isFlushing = false
                        if (pendingBatch.isNotEmpty()) scheduleDebounce()
                    }
                }
            }
        }
    }

    fun clearTags() {
        debounceJob?.cancel()
        isFlushing = false
        rfidManager.clearTags()
        _foundTags.value = emptyList()
        _rawTagCount.value = 0
        _ignoredCount.value = 0
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
