package com.rfid.reader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.rfid.reader.databinding.ActivityMainBinding
import com.rfid.reader.viewmodel.RFIDViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: RFIDViewModel

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[RFIDViewModel::class.java]

        setupObservers()
        setupListeners()
        checkPermissions()
    }

    private fun setupObservers() {
        viewModel.readerStatus.observe(this) { status ->
            binding.tvReaderStatus.text = getString(R.string.reader_status, status)
        }

        viewModel.tagCount.observe(this) { count ->
            binding.tvTagCount.text = getString(R.string.tag_count, count)
        }

        viewModel.tags.observe(this) { tags ->
            // Aggiorna UI con lista tag
        }

        // Abilita/disabilita pulsante scansione in base allo stato di connessione
        viewModel.isConnected.observe(this) { isConnected ->
            binding.btnScan.isEnabled = isConnected
            binding.btnConnect.text = if (isConnected) {
                getString(R.string.disconnect_reader)
            } else {
                getString(R.string.connect_reader)
            }
        }

        // Cambia testo pulsante scansione in base allo stato di scansione
        viewModel.isScanning.observe(this) { isScanning ->
            binding.btnScan.text = if (isScanning) {
                getString(R.string.stop_scan)
            } else {
                getString(R.string.start_scan)
            }
        }
    }

    private fun setupListeners() {
        binding.btnConnect.setOnClickListener {
            if (viewModel.isConnected.value == true) {
                viewModel.disconnectReader()
            } else {
                viewModel.connectReader()
            }
        }

        binding.btnScan.setOnClickListener {
            if (viewModel.isScanning.value == true) {
                viewModel.stopScan()
            } else {
                viewModel.startScan()
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Gestisci risultato permessi
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.disconnectReader()
    }
}
