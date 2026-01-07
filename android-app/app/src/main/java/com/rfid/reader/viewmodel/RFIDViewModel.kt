package com.rfid.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rfid.reader.network.*
import com.rfid.reader.rfid.RFIDManager
import com.zebra.rfid.api3.TagData
import kotlinx.coroutines.launch

class RFIDViewModel(application: Application) : AndroidViewModel(application) {
    private val rfidManager = RFIDManager(application)
    private val apiService = RetrofitClient.apiService

    private val _readerStatus = MutableLiveData<String>("Disconnesso")
    val readerStatus: LiveData<String> = _readerStatus

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val _isScanning = MutableLiveData<Boolean>(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _tags = MutableLiveData<List<TagData>>(emptyList())
    val tags: LiveData<List<TagData>> = _tags

    private val _tagCount = MutableLiveData<Int>(0)
    val tagCount: LiveData<Int> = _tagCount

    private val _places = MutableLiveData<List<PlaceResponse>>(emptyList())
    val places: LiveData<List<PlaceResponse>> = _places

    private val _zones = MutableLiveData<List<ZoneResponse>>(emptyList())
    val zones: LiveData<List<ZoneResponse>> = _zones

    private val _selectedPlace = MutableLiveData<PlaceResponse?>()
    val selectedPlace: LiveData<PlaceResponse?> = _selectedPlace

    private val _selectedZone = MutableLiveData<ZoneResponse?>()
    val selectedZone: LiveData<ZoneResponse?> = _selectedZone

    init {
        observeRFIDManager()
        loadPlacesAndZones()
    }

    private fun observeRFIDManager() {
        viewModelScope.launch {
            rfidManager.connectionState.collect { state ->
                when (state) {
                    RFIDManager.ConnectionState.DISCONNECTED -> {
                        _readerStatus.value = "Disconnesso"
                        _isConnected.value = false
                    }
                    RFIDManager.ConnectionState.CONNECTING -> {
                        _readerStatus.value = "Connessione..."
                        _isConnected.value = false
                    }
                    RFIDManager.ConnectionState.CONNECTED -> {
                        _readerStatus.value = "Connesso"
                        _isConnected.value = true
                    }
                    RFIDManager.ConnectionState.ERROR -> {
                        _readerStatus.value = "Errore connessione"
                        _isConnected.value = false
                    }
                }
            }
        }

        viewModelScope.launch {
            rfidManager.errorMessage.collect { error ->
                error?.let {
                    _readerStatus.value = it
                }
            }
        }

        viewModelScope.launch {
            rfidManager.tags.collect { tagList ->
                android.util.Log.d("RFIDViewModel", "Tags flow collected: ${tagList.size} tags")
                _tags.value = tagList
                _tagCount.value = tagList.size
                android.util.Log.d("RFIDViewModel", "Updated LiveData: tagCount=${tagList.size}")

                // Invia tag al backend
                tagList.forEach { tag ->
                    android.util.Log.d("RFIDViewModel", "Sending tag to backend: ${tag.tagID}")
                    sendTagToBackend(tag)
                }
            }
        }
    }

    private fun loadPlacesAndZones() {
        viewModelScope.launch {
            try {
                // Carica Places
                val placesResponse = apiService.getAllPlaces()
                if (placesResponse.isSuccessful) {
                    _places.value = placesResponse.body() ?: emptyList()
                }

                // Carica Zones
                val zonesResponse = apiService.getAllZones()
                if (zonesResponse.isSuccessful) {
                    _zones.value = zonesResponse.body() ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setPlace(place: PlaceResponse) {
        _selectedPlace.value = place
    }

    fun setZone(zone: ZoneResponse) {
        _selectedZone.value = zone
    }

    fun connectReader() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            rfidManager.connectToReader()
        }
    }

    fun disconnectReader() {
        stopScan()
        rfidManager.disconnect()
    }

    fun startScan() {
        android.util.Log.d("RFIDViewModel", "startScan() called")
        viewModelScope.launch {
            rfidManager.clearTags()
            android.util.Log.d("RFIDViewModel", "Tags cleared, starting inventory")
            rfidManager.startInventory()
            _isScanning.value = true
            android.util.Log.d("RFIDViewModel", "Inventory started, isScanning=true")
        }
    }

    fun stopScan() {
        android.util.Log.d("RFIDViewModel", "stopScan() called")
        viewModelScope.launch {
            rfidManager.stopInventory()
            _isScanning.value = false
            android.util.Log.d("RFIDViewModel", "Inventory stopped, isScanning=false")
        }
    }

    private fun sendTagToBackend(tag: TagData) {
        viewModelScope.launch {
            try {
                val request = ScanRequest(
                    epc = tag.tagID,
                    tid = null,
                    placeId = _selectedPlace.value?.place_id,
                    zoneId = _selectedZone.value?.zone_id,
                    productId = null,
                    rssi = tag.peakRSSI?.toInt(),
                    readsCount = 1,
                    antenna = tag.antennaID?.toInt(),
                    user = null,
                    reader = "RFD8500-${android.os.Build.MODEL}",
                    unexpected = false,
                    notes = null
                )

                val response = apiService.recordScan(request)
                if (!response.isSuccessful) {
                    println("Error sending tag to backend: ${response.code()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendBatchScan() {
        viewModelScope.launch {
            val tagList = _tags.value ?: emptyList()
            if (tagList.isEmpty()) return@launch

            try {
                val tagDataList = tagList.map { tag ->
                    com.rfid.reader.network.TagData(
                        epc = tag.tagID,
                        tid = null,
                        productId = null,
                        rssi = tag.peakRSSI?.toInt(),
                        readsCount = 1,
                        antenna = tag.antennaID?.toInt(),
                        unexpected = false
                    )
                }

                val request = BatchScanRequest(
                    tags = tagDataList,
                    placeId = _selectedPlace.value?.place_id,
                    zoneId = _selectedZone.value?.zone_id,
                    user = null,
                    reader = "RFD8500-${android.os.Build.MODEL}"
                )

                val response = apiService.recordBatchScan(request)
                if (response.isSuccessful) {
                    println("Batch scan sent successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        rfidManager.dispose()
    }
}
