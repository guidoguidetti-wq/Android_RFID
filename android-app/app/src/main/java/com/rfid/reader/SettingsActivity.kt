package com.rfid.reader

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rfid.reader.databinding.ActivitySettingsBinding
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.utils.SettingsManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    private var zones: List<Pair<String, String>> = emptyList() // id, name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        setupUI()
        loadZones()
        loadSettings()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        // Tag Reading Mode Radio Group
        when (settingsManager.getTagReadingMode()) {
            "mode_a" -> binding.rbModeA.isChecked = true
            "mode_b" -> binding.rbModeB.isChecked = true
            "mode_c" -> binding.rbModeC.isChecked = true
        }

        binding.rgTagMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbModeA -> "mode_a"
                R.id.rbModeB -> "mode_b"
                R.id.rbModeC -> "mode_c"
                else -> "mode_c"
            }
            settingsManager.setTagReadingMode(mode)
            android.util.Log.d(TAG, "Tag reading mode set to: $mode")
        }

        // Power SeekBar (10-300)
        binding.seekBarPower.max = 290 // 300-10
        binding.seekBarPower.progress = settingsManager.getReaderPower() - 10
        binding.tvPowerValue.text = settingsManager.getReaderPower().toString()

        binding.seekBarPower.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val power = progress + 10
                binding.tvPowerValue.text = power.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val power = (seekBar?.progress ?: 0) + 10
                settingsManager.setReaderPower(power)
                android.util.Log.d(TAG, "Reader power set to: $power")
            }
        })

        // RSSI SeekBar (-70 to -10)
        binding.seekBarRssi.max = 60 // -10 - (-70)
        binding.seekBarRssi.progress = settingsManager.getMinRssi() + 70
        binding.tvRssiValue.text = settingsManager.getMinRssi().toString()

        binding.seekBarRssi.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rssi = progress - 70
                binding.tvRssiValue.text = rssi.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val rssi = (seekBar?.progress ?: 0) - 70
                settingsManager.setMinRssi(rssi)
                android.util.Log.d(TAG, "Min RSSI set to: $rssi")
            }
        })

        // EPC Prefix Filter
        binding.etEpcPrefix.setText(settingsManager.getEpcPrefixFilter())
        binding.btnSavePrefix.setOnClickListener {
            val prefix = binding.etEpcPrefix.text.toString()
            settingsManager.setEpcPrefixFilter(prefix)
            Toast.makeText(this, "Filtro EPC salvato: $prefix", Toast.LENGTH_SHORT).show()
            android.util.Log.d(TAG, "EPC prefix filter set to: $prefix")
        }
    }

    private fun loadZones() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getAllZones()
                if (response.isSuccessful) {
                    zones = response.body()?.map { it.zone_id to (it.zone_name ?: it.zone_id) } ?: emptyList()
                    android.util.Log.d(TAG, "Loaded ${zones.size} zones")
                    setupZoneSpinner()
                } else {
                    android.util.Log.e(TAG, "Failed to load zones: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading zones", e)
                Toast.makeText(this@SettingsActivity, "Errore caricamento zone", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupZoneSpinner() {
        val zoneNames = zones.map { it.second }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, zoneNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerZone.adapter = adapter

        // Set current selection
        val currentZone = settingsManager.getInventoryZone()
        val index = zones.indexOfFirst { it.first == currentZone }
        if (index >= 0) {
            binding.spinnerZone.setSelection(index)
        }

        binding.spinnerZone.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedZone = zones[position].first
                settingsManager.setInventoryZone(selectedZone)
                android.util.Log.d(TAG, "Inventory zone set to: $selectedZone")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadSettings() {
        // Already loaded in setupUI
        android.util.Log.d(TAG, "Settings loaded - Mode: ${settingsManager.getTagReadingMode()}, Power: ${settingsManager.getReaderPower()}, RSSI: ${settingsManager.getMinRssi()}")
    }

    companion object {
        private const val TAG = "SettingsActivity"
    }
}
