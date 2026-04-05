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
import com.rfid.reader.databinding.ActivityInventoryScanChecklistBinding
import com.rfid.reader.viewmodel.InventoryScanViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity per scansione RFID - Modalità CHECKLIST
 * Mostra tutti i counter: Principale, Expected, Unexpected, Lost
 * Badge verde con totale attesi
 * Ignora setting mode (sempre Solo Censiti)
 */
class InventoryScanChecklistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInventoryScanChecklistBinding
    private lateinit var viewModel: InventoryScanViewModel
    private var inventoryId: Int = 0
    private var inventoryName: String = ""
    private var inventoryDate: String = ""

    companion object {
        private const val TAG = "InventoryScanChecklist"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventoryScanChecklistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ricevi dati inventario da Intent
        inventoryId = intent.getIntExtra("INVENTORY_ID", 0)
        inventoryName = intent.getStringExtra("INVENTORY_NAME") ?: ""
        inventoryDate = intent.getStringExtra("INVENTORY_START_DATE") ?: ""

        if (inventoryId == 0) {
            android.util.Log.e(TAG, "No inventory ID provided")
            Toast.makeText(this, "Errore: Inventario non specificato", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        android.util.Log.d(TAG, "Opening CHECKLIST inventory scan: $inventoryId - $inventoryName")

        // Inizializza ViewModel
        viewModel = ViewModelProvider(this)[InventoryScanViewModel::class.java]
        viewModel.setInventory(inventoryId)

        setupUI()
        setupObservers()
        setupListeners()

        // Check permissions prima di connettere
        if (checkBluetoothPermissions()) {
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
        // Contatore totale tag letti
        viewModel.totalTagsCount.observe(this) { count ->
            binding.tvTotalTagsCount.text = count.toString()
            android.util.Log.d(TAG, "Total tags count: $count")
        }

        // Contatore Expected (tag che matchano checklist)
        viewModel.expectedCount.observe(this) { count ->
            binding.tvExpectedCount.text = count.toString()
            android.util.Log.d(TAG, "Expected count: $count")
        }

        // Contatore Unexpected (tag fuori checklist o > qta)
        viewModel.unexpectedCount.observe(this) { count ->
            binding.tvUnexpectedCount.text = count.toString()
            android.util.Log.d(TAG, "Unexpected count: $count")
        }

        // Contatore Lost (tag attesi non ancora trovati)
        viewModel.lostCount.observe(this) { count ->
            binding.tvLostCount.text = count.toString()
            android.util.Log.d(TAG, "Lost count: $count")
        }

        // Contatore Ignored (tag letti ma non censiti in Items)
        viewModel.ignoredCount.observe(this) { count ->
            binding.tvIgnoredAnnotation.text = "$count Ignored"
            android.util.Log.d(TAG, "Ignored count: $count")
        }

        // Contatore Pending (tag letti ma non ancora confermati dal backend)
        viewModel.pendingCount.observe(this) { count ->
            binding.tvPendingAnnotation.visibility = android.view.View.VISIBLE
            binding.tvPendingAnnotation.text = "$count pending • "
        }

        // Total Expected (per badge)
        viewModel.totalExpectedLive.observe(this) { totalExp ->
            binding.tvExpectedTotal.text = "($totalExp)"
            android.util.Log.d(TAG, "Total expected: $totalExp")
        }

        // Modalità inventario: cambia colore badge per tipo 3 (checklist EPC = arancione)
        viewModel.inventoryModeLive.observe(this) { mode ->
            val badgeColor = if (mode == "checklist_epc") "#FF9800" else "#4CAF50"
            binding.llInventoryBadge.background.setTint(android.graphics.Color.parseColor(badgeColor))
            android.util.Log.d(TAG, "Badge color updated for mode: $mode")
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
            android.util.Log.d(TAG, "Opening details for inventory $inventoryId (Checklist mode)")
            val intent = Intent(this, InventoryDetailsActivity::class.java)
            intent.putExtra("INVENTORY_ID", inventoryId)
            intent.putExtra("INVENTORY_NAME", inventoryName)
            intent.putExtra("INVENTORY_MODE", "checklist")
            startActivity(intent)
        }

        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, InventorySettingsActivity::class.java)
            startActivity(intent)
        }

        binding.btnClearInventory.setOnClickListener {
            showClearConfirmDialog()
        }

        binding.btnCloseInventory.setOnClickListener {
            showCloseConfirmDialog()
        }

        binding.btnDeleteInventory.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    private fun showClearConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Inventory")
            .setMessage("Svuotare l'inventario? Tutti i tag scannerizzati e i counter della checklist verranno azzerati.")
            .setPositiveButton("Conferma") { _, _ ->
                android.util.Log.d(TAG, "Clearing checklist inventory $inventoryId")
                lifecycleScope.launch {
                    viewModel.clearInventory()
                    Toast.makeText(this@InventoryScanChecklistActivity, "Inventario svuotato", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showCloseConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Close Inventory")
            .setMessage("Chiudere l'inventario checklist? Non sarà più possibile aggiungere tag.")
            .setPositiveButton("Conferma") { _, _ ->
                android.util.Log.d(TAG, "Closing checklist inventory $inventoryId")
                lifecycleScope.launch {
                    viewModel.closeInventory()
                    Toast.makeText(this@InventoryScanChecklistActivity, "Inventario chiuso", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Elimina Inventario")
            .setMessage("Per eliminare un Inventario, utilizza l'applicazione Desktop")
            .setPositiveButton("OK", null)
            .setIcon(android.R.drawable.ic_dialog_info)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Disconnetti reader quando si chiude l'activity
        if (viewModel.isConnected.value == true) {
            viewModel.disconnectReader()
        }
    }
}
