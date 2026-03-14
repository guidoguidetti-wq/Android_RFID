package com.rfid.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.rfid.reader.databinding.ActivityTagInfoBinding
import com.rfid.reader.viewmodel.TagInfoViewModel

class TagInfoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTagInfoBinding
    private lateinit var viewModel: TagInfoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTagInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[TagInfoViewModel::class.java]

        setupObservers()
        setupListeners()

        if (checkBluetoothPermissions()) {
            viewModel.connectReader()
        } else {
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
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                viewModel.connectReader()
            } else {
                Toast.makeText(this, "Permessi Bluetooth necessari per usare il reader RFID", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupObservers() {
        viewModel.rawTagCount.observe(this) { count ->
            binding.tvFoundCount.text = count.toString()
        }

        viewModel.foundTags.observe(this) { tags ->
            binding.tvValidatedCount.text = tags.size.toString()
        }

        viewModel.pendingCount.observe(this) { count ->
            binding.tvPendingAnnotation.text = "$count pending • "
        }

        viewModel.ignoredCount.observe(this) { count ->
            binding.tvIgnoredAnnotation.text = "$count Ignored"
        }

        viewModel.isConnected.observe(this) { connected ->
            binding.btnPlayPause.isEnabled = connected
        }

        viewModel.isScanning.observe(this) { scanning ->
            binding.btnPlayPause.text = if (scanning) "⏸" else "▶"
        }

        viewModel.readerStatus.observe(this) { status ->
            binding.tvReaderStatus.text = status
            val isConnected = status.contains("Connected", ignoreCase = true)
            binding.tvReaderStatus.setTextColor(
                if (isConnected) getColor(android.R.color.holo_green_dark)
                else getColor(android.R.color.holo_red_dark)
            )
        }

        viewModel.connectionProgress.observe(this) { isConnecting ->
            binding.progressConnection.visibility = if (isConnecting) View.VISIBLE else View.GONE
            binding.tvConnectionStatus.visibility = if (isConnecting) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnPlayPause.setOnClickListener {
            viewModel.toggleScan()
        }

        binding.btnDetails.setOnClickListener {
            startActivity(Intent(this, TagInfoDetailsActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, InventorySettingsActivity::class.java))
        }

        binding.btnClear.setOnClickListener {
            viewModel.clearTags()
        }

        binding.btnOperations.setOnClickListener {
            startActivity(Intent(this, TagInfoOperationsActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.disconnectReader()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
