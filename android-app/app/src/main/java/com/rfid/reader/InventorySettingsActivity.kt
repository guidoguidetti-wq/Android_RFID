package com.rfid.reader

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.integration.android.IntentIntegrator
import com.rfid.reader.databinding.ActivityInventorySettingsBinding
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.utils.SettingsManager
import kotlinx.coroutines.launch

class InventorySettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInventorySettingsBinding
    private lateinit var settingsManager: SettingsManager

    private val filterFields = listOf("fld01", "fld02", "fld03", "fldd01")
    private val checkBoxes = mutableMapOf<String, CheckBox>()
    private val editTexts = mutableMapOf<String, EditText>()
    private val labels = mutableMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventorySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        // Mappa UI elements
        checkBoxes["fld01"] = binding.cbFilterFld01
        checkBoxes["fld02"] = binding.cbFilterFld02
        checkBoxes["fld03"] = binding.cbFilterFld03
        checkBoxes["fldd01"] = binding.cbFilterFldd01

        editTexts["fld01"] = binding.etFilterFld01
        editTexts["fld02"] = binding.etFilterFld02
        editTexts["fld03"] = binding.etFilterFld03
        editTexts["fldd01"] = binding.etFilterFldd01

        labels["fld01"] = binding.tvLabelFld01
        labels["fld02"] = binding.tvLabelFld02
        labels["fld03"] = binding.tvLabelFld03
        labels["fldd01"] = binding.tvLabelFldd01

        checkModeAndEnableFilters()
        loadProductLabels()
        loadSavedFilters()
        setupListeners()
    }

    private fun checkModeAndEnableFilters() {
        val currentMode = settingsManager.getTagReadingMode()
        val isModeA = currentMode == "mode_a"

        // Mostra/nascondi warning banner
        binding.llWarningBanner.visibility = if (isModeA) View.GONE else View.VISIBLE

        // Abilita/disabilita tutti i controlli
        filterFields.forEach { field ->
            checkBoxes[field]?.isEnabled = isModeA
            if (!isModeA) {
                checkBoxes[field]?.isChecked = false
                editTexts[field]?.isEnabled = false
            }
        }

        // btnSave always enabled - batch delay can be saved in any mode
    }

    private fun loadProductLabels() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getProductLabels()
                if (response.isSuccessful) {
                    val labelsData = response.body() ?: emptyList()

                    // Aggiorna le label con i nomi dal database
                    labelsData.forEach { labelData ->
                        val field = labelData.pr_fld
                        val labelText = labelData.pr_lab ?: field
                        labels[field]?.text = labelText
                    }
                } else {
                    android.util.Log.e(TAG, "Failed to load product labels: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading product labels", e)
                Toast.makeText(this@InventorySettingsActivity,
                    "Errore caricamento labels: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSavedFilters() {
        filterFields.forEach { field ->
            val enabled = settingsManager.isProductFilterEnabled(field)
            val value = settingsManager.getProductFilter(field)

            checkBoxes[field]?.isChecked = enabled
            editTexts[field]?.setText(value)
            editTexts[field]?.isEnabled = enabled
        }

        // Batch delay
        binding.etBatchDelay.setText(settingsManager.getBatchDelayMs().toString())
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // Checkbox listeners - abilita/disabilita EditText
        filterFields.forEach { field ->
            checkBoxes[field]?.setOnCheckedChangeListener { _, isChecked ->
                editTexts[field]?.isEnabled = isChecked
                if (!isChecked) {
                    editTexts[field]?.text?.clear()
                }
            }
        }

        // Barcode: cerca prodotto alla pressione di "Search" sulla tastiera
        binding.etBarcodeSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val barcode = binding.etBarcodeSearch.text.toString().trim()
                if (barcode.isNotEmpty()) searchProductByBarcode(barcode)
                true
            } else false
        }

        // Barcode: apri fotocamera con ZXing
        binding.btnScanBarcode.setOnClickListener {
            val integrator = IntentIntegrator(this)
            integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
            integrator.setPrompt("Scansiona il barcode prodotto")
            integrator.setBeepEnabled(true)
            integrator.setBarcodeImageEnabled(false)
            integrator.initiateScan()
        }

        binding.btnSave.setOnClickListener {
            saveFilters()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                binding.etBarcodeSearch.setText(result.contents)
                searchProductByBarcode(result.contents)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun searchProductByBarcode(barcode: String) {
        binding.tvBarcodeStatus.setTextColor(getColor(android.R.color.darker_gray))
        binding.tvBarcodeStatus.text = "Ricerca in corso..."

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getProductById(barcode)
                if (response.isSuccessful) {
                    val product = response.body()
                    if (product != null) {
                        // Popola i 3 filtri con i valori del prodotto e attiva i flag
                        val fieldsToFill = mapOf(
                            "fld01" to product.fld01,
                            "fld02" to product.fld02,
                            "fld03" to product.fld03
                        )
                        fieldsToFill.forEach { (field, value) ->
                            if (!value.isNullOrEmpty()) {
                                editTexts[field]?.setText(value)
                                checkBoxes[field]?.isChecked = true
                                editTexts[field]?.isEnabled = true
                            }
                        }
                        binding.tvBarcodeStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                        binding.tvBarcodeStatus.text = "Prodotto trovato: ${product.product_id}"
                    } else {
                        binding.tvBarcodeStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                        binding.tvBarcodeStatus.text = "Prodotto non trovato: $barcode"
                    }
                } else {
                    binding.tvBarcodeStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    binding.tvBarcodeStatus.text = "Prodotto non trovato: $barcode"
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error searching product by barcode", e)
                binding.tvBarcodeStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                binding.tvBarcodeStatus.text = "Errore: ${e.message}"
            }
        }
    }

    private fun saveFilters() {
        filterFields.forEach { field ->
            val enabled = checkBoxes[field]?.isChecked ?: false
            val value = editTexts[field]?.text?.toString()?.trim() ?: ""

            settingsManager.setProductFilterEnabled(field, enabled)
            settingsManager.setProductFilter(field, value)
        }

        // Salva batch delay (clamp tra 50ms e 2000ms)
        val batchDelay = binding.etBatchDelay.text.toString().toLongOrNull()
            ?.coerceIn(50L, 2000L)
            ?: SettingsManager.DEFAULT_BATCH_DELAY_MS
        settingsManager.setBatchDelayMs(batchDelay)
        binding.etBatchDelay.setText(batchDelay.toString())

        Toast.makeText(this, "Filtri salvati con successo", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        private const val TAG = "InventorySettings"
    }
}
