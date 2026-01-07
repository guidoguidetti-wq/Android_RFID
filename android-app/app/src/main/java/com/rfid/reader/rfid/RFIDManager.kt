package com.rfid.reader.rfid

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.zebra.rfid.api3.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RFIDManager(private val context: Context) {
    private var readers: Readers? = null
    private var rfidReader: RFIDReader? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _tags = MutableStateFlow<List<TagData>>(emptyList())
    val tags: StateFlow<List<TagData>> = _tags

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _triggerPressed = MutableStateFlow(false)
    val triggerPressed: StateFlow<Boolean> = _triggerPressed

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    init {
        try {
            Log.d(TAG, "Initializing Readers with BLUETOOTH transport")
            readers = Readers(context, ENUM_TRANSPORT.BLUETOOTH)
            Log.d(TAG, "Readers initialized successfully")
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
            } else {
                val pairedDevices = bluetoothAdapter.bondedDevices
                Log.d(TAG, "Found ${pairedDevices.size} paired Bluetooth devices")
                pairedDevices.forEach { device ->
                    Log.d(TAG, "Paired device: ${device.name} (${device.address})")
                }
                pairedDevices.filter { it.name?.startsWith("RFD", ignoreCase = true) == true }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting paired devices - missing BLUETOOTH_CONNECT permission?", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired Bluetooth devices", e)
            emptyList()
        }
    }

    fun getAvailableReaders(): List<ReaderDevice> {
        return try {
            val availableReaders = readers?.GetAvailableRFIDReaderList() ?: emptyList()
            Log.d(TAG, "Found ${availableReaders.size} available readers")
            availableReaders.forEachIndexed { index, reader ->
                Log.d(TAG, "Reader $index: ${reader.name}")
            }
            availableReaders
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available readers", e)
            emptyList()
        }
    }

    fun connectToReader(readerDevice: ReaderDevice? = null) {
        try {
            Log.d(TAG, "Starting connection process")
            _connectionState.value = ConnectionState.CONNECTING
            _errorMessage.value = null

            // Verifica prima che ci siano dispositivi Bluetooth paired
            val pairedDevices = getPairedBluetoothDevices()
            if (pairedDevices.isEmpty()) {
                val error = "Nessun reader RFD trovato nei dispositivi Bluetooth paired.\n" +
                        "1. Accendi il reader RFD8500\n" +
                        "2. Vai in Impostazioni → Bluetooth\n" +
                        "3. Associa il reader (cerca RFD8500...)"
                Log.e(TAG, error)
                _errorMessage.value = error
                _connectionState.value = ConnectionState.ERROR
                return
            }

            Log.d(TAG, "Found ${pairedDevices.size} paired RFD devices")

            // Tentativo di ottenere available readers dall'SDK
            // Retry con delays perché l'SDK potrebbe non essere pronto
            var availableReaders: List<ReaderDevice> = emptyList()
            var lastError: Exception? = null

            for (attempt in 1..3) {
                try {
                    Log.d(TAG, "Attempt $attempt to get available readers...")
                    Thread.sleep(500L * attempt) // Delay incrementale
                    availableReaders = readers?.GetAvailableRFIDReaderList() ?: emptyList()
                    if (availableReaders.isNotEmpty()) {
                        Log.d(TAG, "Found ${availableReaders.size} readers on attempt $attempt")
                        break
                    }
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Attempt $attempt failed: ${e.message}")
                }
            }

            if (availableReaders.isEmpty()) {
                val error = "SDK non riesce a trovare il reader.\n" +
                        "Il reader è paired come '${pairedDevices.first().name}'\n" +
                        "Prova a:\n" +
                        "1. Spegnere e riaccendere il reader\n" +
                        "2. Riavviare l'app\n" +
                        "Errore SDK: ${lastError?.message}"
                Log.e(TAG, error, lastError)
                _errorMessage.value = error
                _connectionState.value = ConnectionState.ERROR
                return
            }

            val reader = readerDevice ?: availableReaders.firstOrNull()!!
            connectViaReaderDevice(reader)
        } catch (e: InvalidUsageException) {
            val error = "SDK Error: ${e.vendorMessage}"
            Log.e(TAG, error, e)
            _errorMessage.value = error
            _connectionState.value = ConnectionState.ERROR
        } catch (e: OperationFailureException) {
            val details = """
                Connessione fallita al reader RFD8500
                StatusDescription: ${e.statusDescription}
                VendorMessage: ${e.vendorMessage}
                Results: ${e.results}

                Possibili cause:
                1. Reader in sleep mode - Premi il trigger per svegliarlo
                2. Reader già connesso a un'altra app (es. 123RFID)
                3. Reader spento o batteria scarica

                Soluzione:
                - Premi il trigger sul reader RFD8500
                - Chiudi altre app RFID Zebra
                - Riprova la connessione
            """.trimIndent()
            Log.e(TAG, "OperationFailureException details:", e)
            Log.e(TAG, "StatusDescription: ${e.statusDescription}")
            Log.e(TAG, "VendorMessage: ${e.vendorMessage}")
            Log.e(TAG, "Results: ${e.results}")
            _errorMessage.value = details
            _connectionState.value = ConnectionState.ERROR
        } catch (e: Exception) {
            val error = "Errore: ${e.message}"
            Log.e(TAG, error, e)
            _errorMessage.value = error
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun connectViaReaderDevice(reader: ReaderDevice) {
        Log.d(TAG, "Connecting via ReaderDevice: ${reader.name}")
        Log.d(TAG, "Reader transport type: ${reader.transport}")
        Log.d(TAG, "Reader password: ${reader.password}")

        rfidReader = reader.getRFIDReader()

        if (rfidReader == null) {
            val error = "getRFIDReader() returned null"
            Log.e(TAG, error)
            _errorMessage.value = error
            _connectionState.value = ConnectionState.ERROR
            return
        }

        Log.d(TAG, "RFIDReader instance obtained")

        // CRITICAL: Aspetta che l'SDK completi l'inizializzazione interna
        Log.d(TAG, "Waiting for SDK internal initialization...")
        Thread.sleep(500L)
        Log.d(TAG, "Wait complete, attempting connection")

        Log.d(TAG, "IsConnected before connect: ${rfidReader!!.isConnected}")

        if (!rfidReader!!.isConnected) {
            Log.d(TAG, "Calling connect()...")

            // Prova la connessione - l'OperationFailureException potrebbe dare dettagli
            try {
                rfidReader?.connect()
                Log.d(TAG, "connect() returned successfully!")
                Log.d(TAG, "isConnected: ${rfidReader!!.isConnected}")

                if (!rfidReader!!.isConnected) {
                    Log.w(TAG, "connect() succeeded but isConnected still false - SDK might need more time")
                    Thread.sleep(250L)
                    Log.d(TAG, "After additional wait, isConnected: ${rfidReader!!.isConnected}")
                }
            } catch (e: OperationFailureException) {
                // Log dettagli e re-throw
                Log.e(TAG, "connect() threw OperationFailureException")
                Log.e(TAG, "StatusDescription: ${e.statusDescription}")
                Log.e(TAG, "VendorMessage: ${e.vendorMessage}")
                throw e
            }
        } else {
            Log.d(TAG, "Reader already connected")
        }

        setupEventHandlers()
        configureReader()

        _connectionState.value = ConnectionState.CONNECTED
        Log.d(TAG, "Reader configured and ready")
    }


    companion object {
        private const val TAG = "RFIDManager"
    }

    private fun configureReader() {
        rfidReader?.let { reader ->
            try {
                // Configurazione base - SDK 2.0 ha API diverse
                val config = reader.Config

                // Configura potenza antenna
                val antennaConfig = config.Antennas.getAntennaRfConfig(1)
                antennaConfig.setTransmitPowerIndex(270) // Max power
                antennaConfig.setrfModeTableIndex(0)
                config.Antennas.setAntennaRfConfig(1, antennaConfig)

                // TODO: Configurazione session - richiede studio API SDK 2.0
                // Le API Singulation sono cambiate nella versione 2.0
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupEventHandlers() {
        try {
            Log.d(TAG, "Setting up event handlers")
            val events = rfidReader?.Events
            if (events == null) {
                Log.e(TAG, "rfidReader.Events is null!")
                return
            }

            Log.d(TAG, "Events object obtained, adding listeners")
            events.addEventsListener(object : RfidEventsListener {
                override fun eventReadNotify(e: RfidReadEvents?) {
                    Log.d(TAG, "=== Tag read event received ===")

                    // Nell'SDK 2.0, l'evento è solo una notifica
                    // I dati del tag devono essere letti dal reader usando Actions
                    try {
                        // Legge fino a 1000 tag dalla memoria del reader
                        val tagDataArray = rfidReader?.Actions?.getReadTags(1000)
                        if (tagDataArray != null && tagDataArray.isNotEmpty()) {
                            Log.d(TAG, "Got ${tagDataArray.size} tags from Actions.getReadTags()")
                            tagDataArray.forEach { tag ->
                                Log.d(TAG, "Tag: EPC=${tag.tagID}, RSSI=${tag.peakRSSI}")
                            }
                            handleTagRead(tagDataArray)
                        } else {
                            Log.w(TAG, "No tags returned from Actions.getReadTags()")
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error reading tags from Actions", ex)
                        ex.printStackTrace()
                    }
                }

                override fun eventStatusNotify(e: RfidStatusEvents?) {
                    Log.d(TAG, "Status event received")
                    e?.StatusEventData?.let { statusData ->
                        Log.d(TAG, "Status event data: $statusData")

                        // Gestione trigger press/release
                        if (statusData.HandheldTriggerEventData != null) {
                            val triggerEvent = statusData.HandheldTriggerEventData.handheldEvent
                            Log.d(TAG, "Trigger event: $triggerEvent")

                            when (triggerEvent) {
                                HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED -> {
                                    Log.d(TAG, "Trigger PRESSED")
                                    _triggerPressed.value = true
                                }
                                HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED -> {
                                    Log.d(TAG, "Trigger RELEASED")
                                    _triggerPressed.value = false
                                }
                                else -> {
                                    Log.d(TAG, "Unknown trigger event: $triggerEvent")
                                }
                            }
                        }
                    }
                    // Gestione eventi di status (disconnessione, errori, etc)
                }
            })
            Log.d(TAG, "Event listeners added successfully")

            // CRITICAL: Nell'SDK 2.0, gli eventi devono essere abilitati esplicitamente
            Log.d(TAG, "Enabling tag read events...")
            try {
                events.setTagReadEvent(true)
                Log.d(TAG, "Tag read events enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error enabling tag read events", e)
            }

            // Abilita eventi trigger
            Log.d(TAG, "Enabling handheld trigger events...")
            try {
                events.setHandheldEvent(true)
                Log.d(TAG, "Handheld trigger events enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error enabling handheld trigger events", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up event handlers", e)
            e.printStackTrace()
        }
    }

    private fun handleTagRead(tagDataArray: Array<TagData>) {
        Log.d(TAG, "handleTagRead: Processing ${tagDataArray.size} tags")
        val currentTags = _tags.value.toMutableList()
        Log.d(TAG, "handleTagRead: Current tags count: ${currentTags.size}")

        tagDataArray.forEach { tag ->
            val existingTag = currentTags.find { it.tagID == tag.tagID }
            if (existingTag == null) {
                Log.d(TAG, "handleTagRead: Adding new tag: ${tag.tagID}")
                currentTags.add(tag)
            } else {
                Log.d(TAG, "handleTagRead: Tag already exists: ${tag.tagID}")
            }
        }

        Log.d(TAG, "handleTagRead: Updating StateFlow with ${currentTags.size} tags")
        _tags.value = currentTags
        Log.d(TAG, "handleTagRead: StateFlow updated successfully")
    }

    fun startInventory() {
        try {
            Log.d(TAG, "startInventory() called")
            rfidReader?.Actions?.Inventory?.perform()
            Log.d(TAG, "Inventory.perform() executed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting inventory", e)
            e.printStackTrace()
        }
    }

    fun stopInventory() {
        try {
            Log.d(TAG, "stopInventory() called")
            rfidReader?.Actions?.Inventory?.stop()
            Log.d(TAG, "Inventory.stop() executed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping inventory", e)
            e.printStackTrace()
        }
    }

    fun clearTags() {
        Log.d(TAG, "clearTags() called - resetting tags list")
        _tags.value = emptyList()
        Log.d(TAG, "Tags cleared, current count: ${_tags.value.size}")
    }

    fun disconnect() {
        try {
            stopInventory()
            rfidReader?.disconnect()
            _connectionState.value = ConnectionState.DISCONNECTED
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dispose() {
        disconnect()
        readers?.Dispose()
    }
}
