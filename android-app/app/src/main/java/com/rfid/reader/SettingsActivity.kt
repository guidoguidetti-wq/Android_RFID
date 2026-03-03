package com.rfid.reader

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
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

        // Power buttons (10-300, step 5) with long-press repeat + tap-to-edit
        binding.tvPowerValue.text = settingsManager.getReaderPower().toString()

        setupStepButton(binding.btnPowerMinus, step = -5, min = 10, max = 300,
            getCurrent = { settingsManager.getReaderPower() },
            save = { settingsManager.setReaderPower(it) },
            display = { binding.tvPowerValue.text = it.toString() }
        )
        setupStepButton(binding.btnPowerPlus, step = 5, min = 10, max = 300,
            getCurrent = { settingsManager.getReaderPower() },
            save = { settingsManager.setReaderPower(it) },
            display = { binding.tvPowerValue.text = it.toString() }
        )
        binding.tvPowerValue.setOnClickListener {
            showEditDialog("Power (10–300)", settingsManager.getReaderPower(), 10, 300) { v ->
                settingsManager.setReaderPower(v)
                binding.tvPowerValue.text = v.toString()
            }
        }

        // RSSI buttons (-70 to -10, step 1) with long-press repeat + tap-to-edit
        binding.tvRssiValue.text = settingsManager.getMinRssi().toString()

        setupStepButton(binding.btnRssiMinus, step = -1, min = -70, max = -10,
            getCurrent = { settingsManager.getMinRssi() },
            save = { settingsManager.setMinRssi(it) },
            display = { binding.tvRssiValue.text = it.toString() }
        )
        setupStepButton(binding.btnRssiPlus, step = 1, min = -70, max = -10,
            getCurrent = { settingsManager.getMinRssi() },
            save = { settingsManager.setMinRssi(it) },
            display = { binding.tvRssiValue.text = it.toString() }
        )
        binding.tvRssiValue.setOnClickListener {
            showEditDialog("Min RSSI (−70 a −10)", settingsManager.getMinRssi(), -70, -10) { v ->
                settingsManager.setMinRssi(v)
                binding.tvRssiValue.text = v.toString()
            }
        }

        // EPC Prefix Filter
        binding.etEpcPrefix.setText(settingsManager.getEpcPrefixFilter())
        binding.btnSavePrefix.setOnClickListener {
            val prefix = binding.etEpcPrefix.text.toString()
            settingsManager.setEpcPrefixFilter(prefix)
            Toast.makeText(this, "Filtro EPC salvato: $prefix", Toast.LENGTH_SHORT).show()
            android.util.Log.d(TAG, "EPC prefix filter set to: $prefix")
        }

        // Beep Volume Radio Group
        when (settingsManager.getBeepVolume()) {
            "low" -> binding.rbBeepLow.isChecked = true
            "medium" -> binding.rbBeepMedium.isChecked = true
            "high" -> binding.rbBeepHigh.isChecked = true
        }

        binding.rgBeepVolume.setOnCheckedChangeListener { _, checkedId ->
            val volume = when (checkedId) {
                R.id.rbBeepLow -> "low"
                R.id.rbBeepMedium -> "medium"
                R.id.rbBeepHigh -> "high"
                else -> "medium"
            }
            settingsManager.setBeepVolume(volume)
            android.util.Log.d(TAG, "Beep volume set to: $volume")
            Toast.makeText(this, "Volume beep: $volume", Toast.LENGTH_SHORT).show()
        }

        // Tag Info Delay SeekBar (100-2000ms in steps of 100ms, progress 0-19)
        binding.seekBarTagInfoDelay.max = 19
        val currentTagDelay = settingsManager.getTagInfoDelayMs()
        binding.seekBarTagInfoDelay.progress = ((currentTagDelay / 100) - 1).toInt().coerceIn(0, 19)
        binding.tvTagInfoDelayValue.text = "${currentTagDelay}ms"

        binding.seekBarTagInfoDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val delayMs = (progress + 1) * 100
                binding.tvTagInfoDelayValue.text = "${delayMs}ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val delayMs = ((seekBar?.progress ?: 0) + 1) * 100L
                settingsManager.setTagInfoDelayMs(delayMs)
                android.util.Log.d(TAG, "Tag info delay set to: ${delayMs}ms")
            }
        })

        // Backend URL
        binding.etBackendUrl.setText(settingsManager.getBackendUrl())
        binding.btnSaveUrl.setOnClickListener {
            var url = binding.etBackendUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                // Assicura che l'URL termini con /
                if (!url.endsWith("/")) {
                    url = "$url/"
                }
                settingsManager.setBackendUrl(url)
                // Aggiorna RetrofitClient con il nuovo URL
                RetrofitClient.updateBaseUrl(url)
                Toast.makeText(this, "URL salvato: $url", Toast.LENGTH_SHORT).show()
                binding.tvUrlStatus.text = "URL aggiornato!"
                binding.tvUrlStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                android.util.Log.d(TAG, "Backend URL set to: $url")
            } else {
                Toast.makeText(this, "Inserisci un URL valido", Toast.LENGTH_SHORT).show()
            }
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

    /**
     * Wires a +/− button with single-click step and long-press auto-repeat.
     * Initial delay before repeat: 400ms; repeat interval: 80ms.
     */
    @Suppress("ClickableViewAccessibility")
    private fun setupStepButton(
        button: Button,
        step: Int,
        min: Int,
        max: Int,
        getCurrent: () -> Int,
        save: (Int) -> Unit,
        display: (Int) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var repeatRunnable: Runnable? = null

        fun applyStep() {
            val newVal = (getCurrent() + step).coerceIn(min, max)
            save(newVal)
            display(newVal)
        }

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    applyStep()
                    repeatRunnable = object : Runnable {
                        override fun run() {
                            applyStep()
                            handler.postDelayed(this, 80)
                        }
                    }
                    handler.postDelayed(repeatRunnable!!, 400)
                    v.isPressed = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    repeatRunnable?.let { handler.removeCallbacks(it) }
                    repeatRunnable = null
                    v.isPressed = false
                    v.performClick()
                }
            }
            true
        }
    }

    /** Opens a dialog with an EditText pre-filled with [current]; calls [onConfirm] on OK. */
    private fun showEditDialog(title: String, current: Int, min: Int, max: Int, onConfirm: (Int) -> Unit) {
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                    if (min < 0) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
            setText(current.toString())
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val v = editText.text.toString().toIntOrNull()
                if (v != null) onConfirm(v.coerceIn(min, max))
                else Toast.makeText(this, "Valore non valido", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
        editText.post { editText.selectAll() }
    }

    private fun loadSettings() {
        // Already loaded in setupUI
        android.util.Log.d(TAG, "Settings loaded - Mode: ${settingsManager.getTagReadingMode()}, Power: ${settingsManager.getReaderPower()}, RSSI: ${settingsManager.getMinRssi()}")
    }

    companion object {
        private const val TAG = "SettingsActivity"
    }
}
