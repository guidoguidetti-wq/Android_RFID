package com.rfid.reader.rfid

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.rfid.reader.utils.BeepHelper
import com.rfid.reader.utils.SettingsManager
import com.zebra.rfid.api3.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RFIDManager private constructor(private val context: Context) {
    private var readers: Readers? = null
    private var rfidReader: RFIDReader? = null
    private var currentEventsListener: RfidEventsListener? = null
    private val settingsManager = SettingsManager(context)
    private val beepHelper = BeepHelper.getInstance(context)

    private var pollingJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _tags = MutableStateFlow<List<TagData>>(emptyList())
    val tags: StateFlow<List<TagData>> = _tags
    
    private val _tagReadFlow = kotlinx.coroutines.flow.MutableSharedFlow<List<TagData>>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val tagReadFlow: kotlinx.coroutines.flow.SharedFlow<List<TagData>> = _tagReadFlow

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _triggerPressed = MutableStateFlow(false)
    val triggerPressed: StateFlow<Boolean> = _triggerPressed

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    companion object {
        private const val TAG = "RFIDManager"
        
        @Volatile
        private var INSTANCE: RFIDManager? = null

        fun getInstance(context: Context): RFIDManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RFIDManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        try {
            Log.d(TAG, "Initializing Readers with BLUETOOTH transport")
            readers = Readers(context, ENUM_TRANSPORT.BLUETOOTH)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Readers", e)
        }
    }

    private fun getPairedBluetoothDevices(): List<BluetoothDevice> {
        return try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Log.e(TAG, "BluetoothAdapter is null")
                emptyList()
            } else if (!bluetoothAdapter.isEnabled) {
                Log.e(TAG, "Bluetooth is disabled")
                emptyList()
            } else {
                val pairedDevices = bluetoothAdapter.bondedDevices
                Log.d(TAG, "Found ${pairedDevices.size} paired Bluetooth devices")
                pairedDevices.forEach { device ->
                    Log.d(TAG, "Device: ${device.name} - ${device.address}")
                }
                // Filtro più permissivo: cerca RFD o dispositivi Zebra
                pairedDevices.filter { 
                    val name = it.name ?: ""
                    name.contains("RFD", ignoreCase = true) || name.contains("Zebra", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired devices", e)
            emptyList()
        }
    }

    suspend fun connectToReader(readerDevice: ReaderDevice? = null) = withContext(Dispatchers.IO) {
        try {
            // Verifica se già connesso MA reinizializza sempre gli event handlers
            if (_connectionState.value == ConnectionState.CONNECTED && rfidReader?.isConnected == true) {
                Log.d(TAG, "Reader already connected, reinstalling event handlers...")
                setupEventHandlers() // ✅ REINSTALLA SEMPRE gli handler
                return@withContext
            }

            Log.d(TAG, "Starting connection process")
            _connectionState.value = ConnectionState.CONNECTING
            _errorMessage.value = null

            // Assicurati che l'oggetto Readers sia pronto
            if (readers == null) {
                readers = Readers(context, ENUM_TRANSPORT.BLUETOOTH)
            }

            val pairedDevices = getPairedBluetoothDevices()
            if (pairedDevices.isEmpty()) {
                val error = "Nessun reader RFD/Zebra trovato nei dispositivi paired. Verifica Bluetooth e associazione."
                Log.e(TAG, error)
                _errorMessage.value = error
                _connectionState.value = ConnectionState.ERROR
                return@withContext
            }

            var availableReaders: List<ReaderDevice> = emptyList()
            for (attempt in 1..3) {
                try {
                    Log.d(TAG, "Attempt $attempt to get available readers...")
                    availableReaders = readers?.GetAvailableRFIDReaderList() ?: emptyList()
                    if (availableReaders.isNotEmpty()) break
                    
                    // Se non trova nulla, prova a reinizializzare l'SDK
                    if (attempt == 2) {
                        Log.d(TAG, "Re-initializing Readers object...")
                        readers?.Dispose()
                        readers = Readers(context, ENUM_TRANSPORT.BLUETOOTH)
                    }
                    kotlinx.coroutines.delay(1000L) 
                } catch (e: Exception) {
                    Log.w(TAG, "Attempt $attempt failed: ${e.message}")
                }
            }

            if (availableReaders.isEmpty()) {
                val error = "SDK Zebra non rileva il reader. Prova a spegnere/riaccendere il Bluetooth del telefono."
                _errorMessage.value = error
                _connectionState.value = ConnectionState.ERROR
                return@withContext
            }

            val reader = readerDevice ?: availableReaders.first()
            Log.d(TAG, "Connecting to: ${reader.name}")
            rfidReader = reader.getRFIDReader()
            
            if (rfidReader == null) throw Exception("Impossibile ottenere istanza RFIDReader dall'SDK")

            if (!rfidReader!!.isConnected) {
                rfidReader?.connect()
            }

            // Configurazione post-connessione
            setupEventHandlers()
            configureReader()

            _connectionState.value = ConnectionState.CONNECTED
            Log.d(TAG, "Reader connected successfully: ${reader.name}")

            // ✅ Beep doppio per conferma connessione
            beepHelper.playDoubleBeep()
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            val msg = if (e is OperationFailureException) e.statusDescription else e.message
            _errorMessage.value = "Errore: $msg"
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun configureReader() {
        rfidReader?.let { reader ->
            try {
                val config = reader.Config
                // Antenna
                val antennaConfig = config.Antennas.getAntennaRfConfig(1)
                antennaConfig.setTransmitPowerIndex(270)
                antennaConfig.setrfModeTableIndex(0)
                config.Antennas.setAntennaRfConfig(1, antennaConfig)

                // Singulation (Session S0 per Geiger)
                val singulationControl = config.Antennas.getSingulationControl(1)
                singulationControl.session = SESSION.SESSION_S0
                singulationControl.Action.inventoryState = INVENTORY_STATE.INVENTORY_STATE_A
                singulationControl.Action.slFlag = SL_FLAG.SL_ALL
                config.Antennas.setSingulationControl(1, singulationControl)

                config.setDPOState(DYNAMIC_POWER_OPTIMIZATION.DISABLE)
                config.setBatchMode(BATCH_MODE.DISABLE)

                // ✅ DISABILITA completamente beep hardware del reader
                // Il beep sarà gestito via software (BeepHelper) solo su eventi specifici
                try {
                    config.setBeeperVolume(BEEPER_VOLUME.QUIET_BEEP)
                    Log.d(TAG, "Hardware beeper DISABLED (using software beep)")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not disable hardware beeper: ${e.message}")
                }

                // ✅ Configura trigger mode per inventory
                try {
                    config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true)
                    Log.d(TAG, "Trigger mode set to RFID_MODE")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set trigger mode: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Configuration error", e)
            }
        }
    }

    private fun setupEventHandlers() {
        try {
            val events = rfidReader?.Events ?: return

            // Rimuovi solo il listener precedente se esiste
            if (currentEventsListener != null) {
                Log.d(TAG, "Removing previous event listener")
                events.removeEventsListener(currentEventsListener)
            }

            // Crea e registra nuovo listener
            currentEventsListener = object : RfidEventsListener {
                override fun eventReadNotify(e: RfidReadEvents?) {
                    scope.launch {
                        try {
                            val tagDataArray = rfidReader?.Actions?.getReadTags(500)
                            if (tagDataArray != null && tagDataArray.isNotEmpty()) {
                                handleTagRead(tagDataArray)
                            }
                        } catch (ex: Exception) {}
                    }
                }

                override fun eventStatusNotify(e: RfidStatusEvents?) {
                    e?.StatusEventData?.let { statusData ->
                        if (statusData.HandheldTriggerEventData != null) {
                            val triggerEvent = statusData.HandheldTriggerEventData.handheldEvent
                            val isPressed = (triggerEvent == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED)
                            Log.d(TAG, "Trigger event: $triggerEvent, pressed: $isPressed")
                            _triggerPressed.value = isPressed
                        }
                    }
                }
            }

            events.addEventsListener(currentEventsListener)
            events.setTagReadEvent(true)
            events.setHandheldEvent(true)
            Log.d(TAG, "Event handlers installed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Event handler error", e)
        }
    }

    private fun handleTagRead(tagDataArray: Array<TagData>) {
        val currentTags = _tags.value.toMutableList()
        var updated = false
        _tagReadFlow.tryEmit(tagDataArray.toList())

        tagDataArray.forEach { tag ->
            val index = currentTags.indexOfFirst { it.tagID == tag.tagID }
            if (index == -1) {
                currentTags.add(tag)
                updated = true
            } else {
                currentTags[index] = tag
                updated = true
            }
        }
        if (updated) _tags.value = currentTags
    }

    fun startInventory() {
        try {
            rfidReader?.Actions?.purgeTags()
            rfidReader?.Actions?.Inventory?.perform()
            startPolling()
        } catch (e: Exception) {
            Log.e(TAG, "Start inventory error", e)
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var emptyReads = 0
            while (true) {
                try {
                    val tags = rfidReader?.Actions?.getReadTags(100)
                    if (tags != null && tags.isNotEmpty()) {
                        handleTagRead(tags)
                        emptyReads = 0
                    } else {
                        emptyReads++
                        // Aumentato threshold per maggiore tolleranza
                        if (emptyReads > 20) {
                            rfidReader?.Actions?.Inventory?.stop()
                            kotlinx.coroutines.delay(100)
                            rfidReader?.Actions?.purgeTags()
                            rfidReader?.Actions?.Inventory?.perform()
                            emptyReads = 0
                        }
                    }
                } catch (e: Exception) {}
                // Ridotto delay per maggiore fluidità RSSI (da 50ms a 30ms)
                kotlinx.coroutines.delay(30)
            }
        }
    }

    fun stopInventory() {
        try {
            pollingJob?.cancel()
            rfidReader?.Actions?.Inventory?.stop()
            Thread.sleep(50)
        } catch (e: Exception) {}
    }

    fun clearTags() {
        _tags.value = emptyList()
        try { rfidReader?.Actions?.purgeTags() } catch (e: Exception) {}
    }

    fun disconnect() {
        try {
            stopInventory()

            // ✅ Pulisci event listeners prima della disconnessione
            if (currentEventsListener != null && rfidReader != null) {
                try {
                    rfidReader?.Events?.removeEventsListener(currentEventsListener)
                    Log.d(TAG, "Event listener removed before disconnect")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not remove event listener: ${e.message}")
                }
                currentEventsListener = null
            }

            rfidReader?.disconnect()
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.d(TAG, "Reader disconnected successfully")

            // ✅ Beep singolo per conferma disconnessione
            beepHelper.playBeep()
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
    }

    fun dispose() {
        stopInventory()
    }
}
