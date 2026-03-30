package com.rfid.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rfid.reader.adapters.LocateTag
import com.rfid.reader.network.ProductResponse
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.rfid.RFIDManager
import com.rfid.reader.utils.BeepHelper
import com.rfid.reader.utils.SettingsManager
import kotlinx.coroutines.launch

class LocateViewModel(application: Application) : AndroidViewModel(application) {
    private val rfidManager = RFIDManager.getInstance(application)
    private val apiService = RetrofitClient.apiService
    private val settingsManager = SettingsManager(application)
    private val beepHelper = BeepHelper.getInstance(application)

    // State
    private val _foundTags = MutableLiveData<List<LocateTag>>(emptyList())
    val foundTags: LiveData<List<LocateTag>> = _foundTags

    private val _selectedTag = MutableLiveData<LocateTag?>()
    val selectedTag: LiveData<LocateTag?> = _selectedTag

    private val _isScanning = MutableLiveData<Boolean>(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val _readerStatus = MutableLiveData<String>("Disconnected")
    val readerStatus: LiveData<String> = _readerStatus

    private val _connectionProgress = MutableLiveData<Boolean>(false)
    val connectionProgress: LiveData<Boolean> = _connectionProgress

    private val _products = MutableLiveData<List<String>>(emptyList())
    val products: LiveData<List<String>> = _products

    private val _selectedProductDetails = MutableLiveData<ProductResponse?>()
    val selectedProductDetails: LiveData<ProductResponse?> = _selectedProductDetails

    private var targetProductId: String? = null

    // Set di EPC pre-caricati per il prodotto selezionato (lookup locale, no API per tag)
    private val productEpcSet = mutableSetOf<String>()
    private var productEpcSetLoaded = false

    // Cache per EPC già esclusi (non appartengono al prodotto)
    private val checkedTagsCache = mutableMapOf<String, Boolean>()

    init {
        observeRFIDManager()
        loadProducts()
        startRssiMonitorLoop()
    }

    private fun startRssiMonitorLoop() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(100) // Check every 100ms (più fluido)
                if (_isScanning.value == true && _selectedTag.value != null) {
                    val now = System.currentTimeMillis()
                    val selected = _selectedTag.value!!

                    // Se non vediamo il tag da più di 1000ms (ridotto), resetta RSSI
                    if (now - selected.lastSeen > 1000) {
                        // -100 dBm è considerato "fuori range" / barra a zero
                        if (selected.rssi > -100) {
                             val lostTag = selected.copy(rssi = -100)
                            _selectedTag.value = lostTag

                            // Aggiorna anche nella lista principale
                            val currentList = _foundTags.value?.toMutableList() ?: mutableListOf()
                            val idx = currentList.indexOfFirst { it.epc == lostTag.epc }
                            if (idx >= 0) {
                                currentList[idx] = lostTag
                                _foundTags.value = currentList
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            try {
                val response = apiService.getAllProducts()
                if (response.isSuccessful) {
                    val productList = response.body() ?: emptyList()
                    val suggestions = productList.mapNotNull { product ->
                        val id = product.product_id
                        val desc = product.fld01 // Usiamo fld01 come da richiesta (anche se utente ha detto fldd01, meglio verificare)
                        // L'utente ha chiesto fldd01 ma ProductResponse ha fld01-05. Verifichiamo Product.kt o Response.
                        // Assumiamo fld01 come descrizione principale per ora o concateniamo id
                        if (!desc.isNullOrBlank()) "$id - $desc" else id
                    }
                    _products.value = suggestions
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading products", e)
            }
        }
    }

    private fun observeRFIDManager() {
        // Osserva connection state
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

        // Osserva trigger
        viewModelScope.launch {
            var lastTriggerState = false
            rfidManager.triggerPressed.collect { pressed ->
                android.util.Log.d(TAG, "Trigger state changed: $pressed (last: $lastTriggerState)")
                if (lastTriggerState && !pressed) {
                    android.util.Log.d(TAG, "Trigger released, toggling scan")
                    toggleScan()
                }
                lastTriggerState = pressed
            }
        }

        // Osserva tag letti (usa tagReadFlow per flusso continuo "Geiger")
        viewModelScope.launch {
            rfidManager.tagReadFlow.collect { tagList ->
                // Log.d(TAG, "Flow collected ${tagList.size} tags") // Verbose log

                tagList.forEach { tag ->
                    val epc = tag.tagID
                    val rssi = tag.peakRSSI.toInt()
                    
                    val currentSelected = _selectedTag.value
                    
                    // SE c'è un tag selezionato:
                    if (currentSelected != null) {
                        // Accetta SOLO aggiornamenti per questo EPC
                        if (epc == currentSelected.epc) {
                             checkAndAddTag(epc, rssi)
                        }
                    } else {
                        // NESSUN tag selezionato: accetta tutti quelli che matchano il product ID
                        if (targetProductId != null) {
                            checkAndAddTag(epc, rssi)
                        }
                    }
                }
            }
        }
    }

    private fun checkAndAddTag(epc: String, rssi: Int) {
        val currentList = _foundTags.value?.toMutableList() ?: mutableListOf()
        val existingIndex = currentList.indexOfFirst { it.epc == epc }

        if (existingIndex >= 0) {
            // Tag già trovato: aggiorna RSSI e LastSeen
            val existingTag = currentList[existingIndex]
            val newTag = existingTag.copy(rssi = rssi, lastSeen = System.currentTimeMillis())
            currentList[existingIndex] = newTag
            _foundTags.value = currentList

            if (_selectedTag.value?.epc == epc) {
                _selectedTag.value = newTag
            }
            return
        }

        // EPC già escluso in precedenza
        if (checkedTagsCache[epc] == false) return

        // Set non ancora caricato: skip (il tag verrà ripreso al prossimo ciclo di lettura)
        if (!productEpcSetLoaded) return

        // Lookup locale: nessuna chiamata API per tag
        val isMatch = productEpcSet.contains(epc)
        checkedTagsCache[epc] = isMatch

        if (isMatch) {
            val updatedList = _foundTags.value?.toMutableList() ?: mutableListOf()
            if (updatedList.none { it.epc == epc }) {
                beepHelper.playBeep()
                updatedList.add(LocateTag(epc, rssi, false, System.currentTimeMillis()))
                _foundTags.value = updatedList
                android.util.Log.d(TAG, "New matching tag found: $epc")
            }
        }
    }

    fun setTargetProduct(productId: String) {
        targetProductId = productId
        _foundTags.value = emptyList()
        _selectedTag.value = null
        checkedTagsCache.clear()
        productEpcSet.clear()
        productEpcSetLoaded = false
        android.util.Log.d(TAG, "Target product set: $productId")
        loadProductDetails(productId)
        loadProductEpcs(productId)
    }

    private fun loadProductEpcs(productId: String) {
        viewModelScope.launch {
            try {
                val response = apiService.getItemsByProduct(productId)
                if (response.isSuccessful) {
                    val items = response.body() ?: emptyList()
                    productEpcSet.clear()
                    productEpcSet.addAll(items.map { it.item_id })
                    productEpcSetLoaded = true
                    android.util.Log.d(TAG, "Loaded ${productEpcSet.size} EPCs for product $productId")
                } else {
                    productEpcSetLoaded = true // caricamento fallito, set vuoto
                    android.util.Log.w(TAG, "Failed to load EPCs for product $productId: ${response.code()}")
                }
            } catch (e: Exception) {
                productEpcSetLoaded = true
                android.util.Log.e(TAG, "Error loading product EPCs", e)
            }
        }
    }

    private fun loadProductDetails(productId: String) {
        viewModelScope.launch {
            try {
                val response = apiService.getProductById(productId)
                if (response.isSuccessful) {
                    _selectedProductDetails.value = response.body()
                    android.util.Log.d(TAG, "Product details loaded for: $productId")
                } else {
                    _selectedProductDetails.value = null
                    android.util.Log.w(TAG, "Product not found: $productId")
                }
            } catch (e: Exception) {
                _selectedProductDetails.value = null
                android.util.Log.e(TAG, "Error loading product details", e)
            }
        }
    }

    fun selectTag(tag: LocateTag) {
        // Deseleziona precedente
        val updatedList = _foundTags.value?.map {
            it.copy(isSelected = it.epc == tag.epc)
        } ?: emptyList()

        _foundTags.value = updatedList
        _selectedTag.value = tag
        android.util.Log.d(TAG, "Tag selected: ${tag.epc}")
    }

    fun clearSelection() {
        val updatedList = _foundTags.value?.map {
            it.copy(isSelected = false)
        } ?: emptyList()

        _foundTags.value = updatedList
        _selectedTag.value = null
    }

    fun clearAllTags() {
        rfidManager.clearTags()
        _foundTags.value = emptyList()
        _selectedTag.value = null
        checkedTagsCache.clear()
    }

    fun connectReader() {
        // Force UI to show connecting immediately
        _connectionProgress.value = true
        _readerStatus.value = "Connecting..."
        
        viewModelScope.launch {
            // Small delay to ensure UI updates before heavy work
            kotlinx.coroutines.delay(100)
            android.util.Log.d(TAG, "Connecting to reader...")
            rfidManager.connectToReader()
        }
    }

    fun disconnectReader() {
        stopScan()
        rfidManager.disconnect()
    }

    fun startScan() {
        // Rimuovo il check su targetProductId - permetti scan anche senza product ID
        // if (targetProductId.isNullOrEmpty()) {
        //     android.util.Log.w(TAG, "Cannot start scan: no target product set")
        //     return
        // }

        viewModelScope.launch {
            rfidManager.clearTags()
            rfidManager.startInventory()
            _isScanning.value = true
            android.util.Log.d(TAG, "Scan started (product: ${targetProductId ?: "none"})")
        }
    }

    fun stopScan() {
        viewModelScope.launch {
            rfidManager.stopInventory()
            _isScanning.value = false
            android.util.Log.d(TAG, "Scan stopped")
        }
    }

    fun toggleScan() {
        if (_isScanning.value == true) {
            stopScan()
        } else {
            startScan()
        }
    }

    override fun onCleared() {
        super.onCleared()
        rfidManager.dispose()
    }

    companion object {
        private const val TAG = "LocateViewModel"
    }
}
