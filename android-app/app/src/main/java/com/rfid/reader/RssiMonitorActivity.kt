package com.rfid.reader

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.rfid.reader.databinding.ActivityRssiMonitorBinding
import com.rfid.reader.viewmodel.RssiMonitorViewModel

/**
 * Activity per monitorare l'RSSI di un singolo tag in modalità "Geiger"
 */
class RssiMonitorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRssiMonitorBinding
    private lateinit var viewModel: RssiMonitorViewModel
    private var targetEpc: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRssiMonitorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ricevi EPC dal Intent
        targetEpc = intent.getStringExtra("TARGET_EPC") ?: ""
        if (targetEpc.isEmpty()) {
            android.util.Log.e(TAG, "No target EPC provided")
            finish()
            return
        }

        val autoStart = intent.getBooleanExtra("AUTO_START", false)
        android.util.Log.d(TAG, "Starting RSSI monitor for: $targetEpc (autoStart=$autoStart)")

        viewModel = ViewModelProvider(this)[RssiMonitorViewModel::class.java]
        viewModel.setTargetEpc(targetEpc)

        setupUI()
        setupObservers()
        setupListeners()

        if (autoStart) {
            viewModel.startScan()
        }
    }

    private fun setupUI() {
        binding.tvTargetEpc.text = "EPC: $targetEpc"
    }

    private fun setupObservers() {
        // Product data
        viewModel.productData.observe(this) { product ->
            if (product != null) {
                binding.tvProductId.text = product.product_id ?: "N/A"
                binding.tvFld01.text = product.fld01 ?: ""
                binding.tvFld02.text = product.fld02 ?: ""
                binding.tvFld03.text = product.fld03 ?: ""

                // Nascondi campi vuoti
                binding.tvFld01.visibility = if (product.fld01.isNullOrBlank()) View.GONE else View.VISIBLE
                binding.tvFld02.visibility = if (product.fld02.isNullOrBlank()) View.GONE else View.VISIBLE
                binding.tvFld03.visibility = if (product.fld03.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        }

        // RSSI value
        viewModel.rssiValue.observe(this) { rssi ->
            updateRssiDisplay(rssi)
        }

        // Scanning state
        viewModel.isScanning.observe(this) { scanning ->
            binding.btnPlayPause.text = if (scanning) "⏸" else "▶"
        }

        // Connection status
        viewModel.readerStatus.observe(this) { status ->
            binding.tvReaderStatus.text = status
        }

        viewModel.connectionProgress.observe(this) { isConnecting ->
            binding.progressConnection.visibility = if (isConnecting) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnPlayPause.setOnClickListener {
            viewModel.toggleScan()
        }
    }

    private fun updateRssiDisplay(rssi: Int) {
        if (rssi <= -100) {
            binding.tvRssiValue.text = "Lost Signal"
            binding.progressRssi.progress = 0
            binding.tvRssiValue.setTextColor(getColor(android.R.color.darker_gray))
            binding.tvDistance.text = "---"
            return
        }

        binding.tvRssiValue.text = "$rssi dBm"

        // Mappa RSSI da [-90, -20] a [0, 100] per progress bar
        val progress = ((rssi + 90) * 100 / 70).coerceIn(0, 100)
        binding.progressRssi.progress = progress

        // Aggiorna colore testo in base alla vicinanza
        val color = when {
            rssi >= -40 -> getColor(android.R.color.holo_green_dark)
            rssi >= -60 -> getColor(android.R.color.holo_orange_light)
            else -> getColor(android.R.color.holo_red_dark)
        }
        binding.tvRssiValue.setTextColor(color)

        // ✅ Sincronizza colore barra RSSI con colore testo
        binding.progressRssi.progressTintList = android.content.res.ColorStateList.valueOf(color)

        // Stima distanza approssimativa
        val distance = when {
            rssi >= -40 -> "< 0.5m"
            rssi >= -50 -> "0.5-1m"
            rssi >= -60 -> "1-2m"
            rssi >= -70 -> "2-4m"
            rssi >= -80 -> "4-8m"
            else -> "> 8m"
        }
        binding.tvDistance.text = distance
    }

    override fun onPause() {
        super.onPause()
        // Stop scan quando activity va in background
        if (viewModel.isScanning.value == true) {
            viewModel.stopScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d(TAG, "Activity destroying, stopping scan only (keeping reader connected)")
        // ✅ NON disconnettere il reader - solo stop scan
        viewModel.stopScan()
        // NON chiamare viewModel.disconnectReader()
    }

    companion object {
        private const val TAG = "RssiMonitorActivity"
    }
}
