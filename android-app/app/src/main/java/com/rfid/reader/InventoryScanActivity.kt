package com.rfid.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.rfid.reader.databinding.ActivityInventoryScanBinding
import com.rfid.reader.viewmodel.InventoryScanViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity per scansione RFID associata a un inventario specifico
 */
class InventoryScanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInventoryScanBinding
    private lateinit var viewModel: InventoryScanViewModel
    private var inventoryId: Int = 0
    private var inventoryName: String = ""
    private var inventoryDate: String = ""
    private var existingCount: Int = 0
    private var currentInventoryMode: String = "normal"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventoryScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ricevi dati inventario da Intent
        inventoryId = intent.getIntExtra("INVENTORY_ID", 0)
        inventoryName = intent.getStringExtra("INVENTORY_NAME") ?: ""
        inventoryDate = intent.getStringExtra("INVENTORY_START_DATE") ?: ""
        existingCount = intent.getIntExtra("INVENTORY_COUNT", 0)

        if (inventoryId == 0) {
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

        // Check permissions prima di connettere
        if (checkBluetoothPermissions()) {
            // Auto-connessione reader
            android.util.Log.d(TAG, "Auto-connecting to RFID reader...")
            viewModel.connectReader()
        } else {
            android.util.Log.d(TAG, "Requesting Bluetooth permissions...")
            requestBluetoothPermissions()
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        ActivityCompat.requestPermissions(
            this,
            permissions.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                android.util.Log.d(TAG, "Bluetooth permissions granted, connecting to reader...")
                viewModel.connectReader()
            } else {
                android.util.Log.e(TAG, "Bluetooth permissions denied")
                Toast.makeText(this, "Permessi Bluetooth necessari per usare il reader RFID", Toast.LENGTH_LONG).show()
            }
        }
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

        // Contatore Expected (tag attesi trovati)
        viewModel.expectedCount.observe(this) { count ->
            binding.tvExpectedCount.text = count.toString()
            android.util.Log.d(TAG, "Expected count: $count")
        }

        // Contatore Unexpected (tag non attesi)
        viewModel.unexpectedCount.observe(this) { count ->
            binding.tvUnexpectedCount.text = count.toString()
            android.util.Log.d(TAG, "Unexpected count: $count")
        }

        // Contatore Lost (tag attesi non ancora trovati)
        viewModel.lostCount.observe(this) { count ->
            binding.tvLostCount.text = count.toString()
            android.util.Log.d(TAG, "Lost count: $count")
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

        // Inventory mode
        viewModel.inventoryModeLive.observe(this) { mode ->
            currentInventoryMode = mode
            updateModeDisplay(mode)
            android.util.Log.d(TAG, "Inventory mode: $mode")
        }

        // Total Expected (for badge display)
        viewModel.totalExpectedLive.observe(this) { totalExp ->
            if (totalExp > 0 && currentInventoryMode != "normal") {
                binding.tvExpectedTotal.text = "($totalExp)"
                binding.tvExpectedTotal.visibility = View.VISIBLE
            } else {
                binding.tvExpectedTotal.visibility = View.GONE
            }
            android.util.Log.d(TAG, "Total expected: $totalExp")
        }
    }

    private fun updateModeDisplay(mode: String) {
        val (displayText, bgColor) = when (mode) {
            "checklist" -> "Checklist" to "#4CAF50"  // Verde
            "last_place" -> "Stock" to "#2196F3"     // Blu
            else -> "Normal" to "#9E9E9E"            // Grigio
        }
        binding.tvInventoryMode.text = displayText
        binding.llInventoryBadge.background.setTint(android.graphics.Color.parseColor(bgColor))
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
            android.util.Log.d(TAG, "Opening details for inventory $inventoryId (mode: $currentInventoryMode)")
            val intent = Intent(this, InventoryDetailsActivity::class.java)
            intent.putExtra("INVENTORY_ID", inventoryId)
            intent.putExtra("INVENTORY_NAME", inventoryName)
            intent.putExtra("INVENTORY_MODE", currentInventoryMode)
            startActivity(intent)
        }

        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, InventorySettingsActivity::class.java)
            startActivity(intent)
        }

        binding.btnClearInventory.setOnClickListener {
            android.util.Log.d(TAG, "Clear inventory button pressed")
            showClearConfirmationDialog()
        }

        binding.btnCloseInventory.setOnClickListener {
            android.util.Log.d(TAG, "Close inventory button pressed")
            showCloseConfirmationDialog()
        }

        binding.btnDeleteInventory.setOnClickListener {
            android.util.Log.d(TAG, "Delete inventory button pressed")
            showDeleteConfirmationDialog()
        }

        binding.btnMenu.setOnClickListener {
            // TODO: Menu per reset contatori, chiudi inventario, etc.
            Toast.makeText(this, "Menu - Coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Mostra dialog di conferma per svuotare l'inventario
     */
    private fun showClearConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Svuota Inventario")
            .setMessage("Sei sicuro di voler eliminare tutti gli item da questo inventario? Questa azione non può essere annullata.")
            .setPositiveButton("Sì, Svuota") { _, _ ->
                clearInventory()
            }
            .setNegativeButton("Annulla", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    /**
     * Mostra dialog di conferma per chiudere l'inventario
     */
    private fun showCloseConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Chiudi Inventario")
            .setMessage("Confermi di voler chiudere questo inventario? Lo stato sarà impostato a CLOSE.")
            .setPositiveButton("Sì, Chiudi") { _, _ ->
                closeInventory()
            }
            .setNegativeButton("Annulla", null)
            .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .show()
    }

    /**
     * Mostra dialog di conferma per eliminare l'inventario
     */
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Elimina Inventario")
            .setMessage("ATTENZIONE: Sei sicuro di voler eliminare completamente questo inventario? Saranno eliminati l'inventario e tutti i suoi item. Questa azione non può essere annullata.")
            .setPositiveButton("Sì, Elimina") { _, _ ->
                deleteInventory()
            }
            .setNegativeButton("Annulla", null)
            .setIcon(android.R.drawable.ic_delete)
            .show()
    }

    /**
     * Svuota l'inventario eliminando tutti gli items
     */
    private fun clearInventory() {
        android.util.Log.d(TAG, "Clearing inventory $inventoryId")

        // Disabilita pulsante durante operazione
        binding.btnClearInventory.isEnabled = false
        binding.btnClearInventory.text = "Clearing..."

        lifecycleScope.launch {
            try {
                viewModel.clearInventory()

                Toast.makeText(
                    this@InventoryScanActivity,
                    "Inventario svuotato con successo",
                    Toast.LENGTH_SHORT
                ).show()

                android.util.Log.d(TAG, "Inventory cleared successfully")
            } catch (e: Exception) {
                Toast.makeText(
                    this@InventoryScanActivity,
                    "Errore: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.e(TAG, "Error clearing inventory", e)
            } finally {
                // Riabilita pulsante
                binding.btnClearInventory.isEnabled = true
                binding.btnClearInventory.text = "CLEAR"
            }
        }
    }

    /**
     * Chiude l'inventario impostando lo stato a CLOSE
     */
    private fun closeInventory() {
        android.util.Log.d(TAG, "Closing inventory $inventoryId")

        // Disabilita pulsanti durante operazione
        binding.btnCloseInventory.isEnabled = false
        binding.btnCloseInventory.text = "Closing..."

        lifecycleScope.launch {
            try {
                viewModel.closeInventory()

                Toast.makeText(
                    this@InventoryScanActivity,
                    "Inventario chiuso con successo",
                    Toast.LENGTH_SHORT
                ).show()

                android.util.Log.d(TAG, "Inventory closed successfully")

                // Torna alla lista inventari
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@InventoryScanActivity,
                    "Errore: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.e(TAG, "Error closing inventory", e)

                // Riabilita pulsante
                binding.btnCloseInventory.isEnabled = true
                binding.btnCloseInventory.text = "CLOSE\nInventory"
            }
        }
    }

    /**
     * Elimina l'inventario e tutti gli items associati
     */
    private fun deleteInventory() {
        android.util.Log.d(TAG, "Deleting inventory $inventoryId")

        // Disabilita pulsanti durante operazione
        binding.btnDeleteInventory.isEnabled = false
        binding.btnDeleteInventory.text = "Deleting..."

        lifecycleScope.launch {
            try {
                viewModel.deleteInventory()

                Toast.makeText(
                    this@InventoryScanActivity,
                    "Inventario eliminato con successo",
                    Toast.LENGTH_SHORT
                ).show()

                android.util.Log.d(TAG, "Inventory deleted successfully")

                // Torna alla lista inventari
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@InventoryScanActivity,
                    "Errore: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.e(TAG, "Error deleting inventory", e)

                // Riabilita pulsante
                binding.btnDeleteInventory.isEnabled = true
                binding.btnDeleteInventory.text = "DELETE\nInventory"
            }
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
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
