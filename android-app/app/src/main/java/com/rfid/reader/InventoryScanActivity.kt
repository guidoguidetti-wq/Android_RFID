package com.rfid.reader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.rfid.reader.databinding.ActivityInventoryScanBinding
import com.rfid.reader.viewmodel.InventoryScanViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity per scansione RFID associata a un inventario specifico
 */
class InventoryScanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInventoryScanBinding
    private lateinit var viewModel: InventoryScanViewModel
    private var inventoryId: String = ""
    private var inventoryName: String = ""
    private var inventoryDate: String = ""
    private var existingCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventoryScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ricevi dati inventario da Intent
        inventoryId = intent.getStringExtra("INVENTORY_ID") ?: ""
        inventoryName = intent.getStringExtra("INVENTORY_NAME") ?: ""
        inventoryDate = intent.getStringExtra("INVENTORY_START_DATE") ?: ""
        existingCount = intent.getIntExtra("INVENTORY_COUNT", 0)

        if (inventoryId.isEmpty()) {
            android.util.Log.e(TAG, "No inventory ID provided")
            Toast.makeText(this, "Errore: Inventario non specificato", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        android.util.Log.d(TAG, "Opening inventory scan: $inventoryId - $inventoryName")

        // Inizializza ViewModel
        viewModel = ViewModelProvider(this)[InventoryScanViewModel::class.java]
        viewModel.setInventory(inventoryId)

        setupUI()
        setupObservers()
        setupListeners()

        // Auto-connessione reader
        android.util.Log.d(TAG, "Auto-connecting to RFID reader...")
        viewModel.connectReader()
    }

    private fun setupUI() {
        binding.tvInventoryName.text = inventoryName

        // Format date
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val date = inputFormat.parse(inventoryDate)
            binding.tvInventoryDate.text = date?.let { outputFormat.format(it) } ?: inventoryDate
        } catch (e: Exception) {
            binding.tvInventoryDate.text = inventoryDate
        }
    }

    private fun setupObservers() {
        // Contatore totale tag letti (già in inventario + nuovi scannati)
        viewModel.totalTagsCount.observe(this) { count ->
            binding.tvTotalTagsCount.text = count.toString()
            android.util.Log.d(TAG, "Total tags count: $count")
        }

        // Stato connessione reader
        viewModel.readerStatus.observe(this) { status ->
            binding.tvReaderStatus.text = status
            val isConnected = status.contains("Connected", ignoreCase = true)
            binding.tvReaderStatus.setTextColor(
                if (isConnected) getColor(android.R.color.holo_green_dark)
                else getColor(android.R.color.holo_red_dark)
            )
            android.util.Log.d(TAG, "Reader status: $status")
        }

        // Abilita pulsante scan solo se connesso
        viewModel.isConnected.observe(this) { connected ->
            binding.btnPlayPause.isEnabled = connected
            android.util.Log.d(TAG, "Reader connected: $connected")
        }

        // Stato scansione
        viewModel.isScanning.observe(this) { isScanning ->
            binding.btnPlayPause.text = if (isScanning) "⏸" else "▶"
            android.util.Log.d(TAG, "Is scanning: $isScanning")
        }

        // Connection progress
        viewModel.connectionProgress.observe(this) { isConnecting ->
            binding.progressConnection.visibility = if (isConnecting) View.VISIBLE else View.GONE
            binding.tvConnectionStatus.visibility = if (isConnecting) View.VISIBLE else View.GONE
            android.util.Log.d(TAG, "Connection progress: $isConnecting")
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            android.util.Log.d(TAG, "Back button pressed")
            finish()
        }

        binding.btnPlayPause.setOnClickListener {
            if (viewModel.isScanning.value == true) {
                android.util.Log.d(TAG, "Stop scan button pressed")
                viewModel.stopScan()
            } else {
                android.util.Log.d(TAG, "Start scan button pressed")
                viewModel.startScan()
            }
        }

        binding.btnInfo.setOnClickListener {
            android.util.Log.d(TAG, "Opening details for inventory $inventoryId")
            val intent = Intent(this, InventoryDetailsActivity::class.java)
            intent.putExtra("INVENTORY_ID", inventoryId)
            intent.putExtra("INVENTORY_NAME", inventoryName)
            startActivity(intent)
        }

        binding.btnSettings.setOnClickListener {
            // TODO: Aprire settings
            Toast.makeText(this, "Settings - Coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnMenu.setOnClickListener {
            // TODO: Menu per reset contatori, chiudi inventario, etc.
            Toast.makeText(this, "Menu - Coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop scan quando activity va in background
        if (viewModel.isScanning.value == true) {
            android.util.Log.d(TAG, "Activity pausing, stopping scan")
            viewModel.stopScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d(TAG, "Activity destroying, disconnecting reader")
        viewModel.disconnectReader()
    }

    companion object {
        private const val TAG = "InventoryScanActivity"
    }
}
