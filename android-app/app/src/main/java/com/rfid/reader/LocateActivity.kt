package com.rfid.reader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.rfid.reader.adapters.LocateTag
import com.rfid.reader.adapters.LocateTagsAdapter
import com.rfid.reader.databinding.ActivityLocateBinding
import com.rfid.reader.viewmodel.LocateViewModel

class LocateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocateBinding
    private lateinit var viewModel: LocateViewModel
    private lateinit var adapter: LocateTagsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[LocateViewModel::class.java]

        setupRecyclerView()
        setupAutoComplete()
        setupObservers()
        setupListeners()

        // Check permissions prima di connettere
        if (checkBluetoothPermissions()) {
            android.util.Log.d(TAG, "Auto-connecting to reader...")
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

    private fun setupAutoComplete() {
        viewModel.products.observe(this) { products ->
            val adapter = android.widget.ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                products
            )
            binding.etProductId.setAdapter(adapter)
            
            // Show full list on click if empty
            binding.etProductId.setOnClickListener {
                if (binding.etProductId.text.isEmpty()) {
                    binding.etProductId.showDropDown()
                }
            }
        }
        
        binding.etProductId.setOnItemClickListener { parent, _, position, _ ->
            val selection = parent.getItemAtPosition(position) as String
            // Estrai ID (assumendo formato "ID - Desc")
            val id = selection.split(" - ")[0]
            binding.etProductId.setText(id)
            binding.etProductId.setSelection(id.length)
        }
    }

    private fun setupRecyclerView() {
        adapter = LocateTagsAdapter { tag ->
            // Quando si clicca su un tag, ferma scan e apri RssiMonitorActivity
            onTagSelected(tag)
        }

        binding.rvFoundTags.layoutManager = LinearLayoutManager(this)
        binding.rvFoundTags.adapter = adapter
    }

    private fun onTagSelected(tag: LocateTag) {
        android.util.Log.d(TAG, "Tag selected: ${tag.epc}, opening RSSI monitor")

        // Ferma lo scan (ma lascia connesso il reader)
        if (viewModel.isScanning.value == true) {
            viewModel.stopScan()
        }

        // Apri RssiMonitorActivity passando l'EPC
        val intent = Intent(this, RssiMonitorActivity::class.java)
        intent.putExtra("TARGET_EPC", tag.epc)
        startActivity(intent)
    }

    private fun setupObservers() {
        viewModel.foundTags.observe(this) { tags ->
            adapter.submitList(tags)
            binding.tvFoundCount.text = tags.size.toString()
        }

        // Rimuoviamo l'observer per selectedTag perché ora usiamo una pagina separata
        // viewModel.selectedTag.observe(this) { ... }

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
                if (isConnected) androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_green_dark)
                else androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
        }
        
        viewModel.connectionProgress.observe(this) { isConnecting ->
            binding.progressConnection.visibility = if (isConnecting) View.VISIBLE else View.GONE
            binding.tvConnectionStatus.visibility = if (isConnecting) View.VISIBLE else View.GONE
        }

        // Observe product details
        viewModel.selectedProductDetails.observe(this) { product ->
            if (product != null) {
                binding.llProductDetails.visibility = View.VISIBLE

                binding.tvLocateFld01.text = product.fld01 ?: ""
                binding.tvLocateFld01.visibility = if (product.fld01.isNullOrBlank()) View.GONE else View.VISIBLE

                binding.tvLocateFld02.text = product.fld02 ?: ""
                binding.tvLocateFld02.visibility = if (product.fld02.isNullOrBlank()) View.GONE else View.VISIBLE

                binding.tvLocateFld03.text = product.fld03 ?: ""
                binding.tvLocateFld03.visibility = if (product.fld03.isNullOrBlank()) View.GONE else View.VISIBLE

                binding.tvLocateFldd01.text = product.fldd01 ?: ""
                binding.tvLocateFldd01.visibility = if (product.fldd01.isNullOrBlank()) View.GONE else View.VISIBLE
            } else {
                binding.llProductDetails.visibility = View.GONE
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnPlayPause.setOnClickListener {
            val productId = binding.etProductId.text.toString().trim()

            if (productId.isEmpty()) {
                Toast.makeText(this, "Inserisci un Product ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Nascondi tastiera
            hideKeyboard()

            // Imposta target solo se cambiato
            if (viewModel.isScanning.value != true) {
                viewModel.setTargetProduct(productId)
            }

            viewModel.toggleScan()
        }
        
        binding.btnClear.setOnClickListener {
            viewModel.clearAllTags()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.isScanning.value == true) {
            viewModel.stopScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d(TAG, "Activity destroying, disconnecting reader")
        viewModel.disconnectReader()
    }

    companion object {
        private const val TAG = "LocateActivity"
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
