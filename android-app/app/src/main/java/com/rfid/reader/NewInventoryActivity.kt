package com.rfid.reader

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rfid.reader.databinding.ActivityNewInventoryBinding
import com.rfid.reader.network.ChecklistResponse
import com.rfid.reader.network.CreateInventoryRequest
import com.rfid.reader.network.PlaceResponse
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.network.ZoneResponse
import com.rfid.reader.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity per la creazione di un nuovo inventario
 */
class NewInventoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewInventoryBinding
    private lateinit var sessionManager: SessionManager

    // Data lists
    private var checklists: List<ChecklistResponse> = emptyList()
    private var places: List<PlaceResponse> = emptyList()
    private var zones: List<ZoneResponse> = emptyList()

    // Selected values
    private var selectedChecklistId: Int = 0
    private var selectedLastPlaceId: String? = null
    private var selectedZoneIds: MutableList<String> = mutableListOf()
    private var selectedDetPlaceId: String? = null
    private var selectedDetZoneId: String? = null
    private var selectedMisPlaceId: String? = null
    private var selectedMisZoneId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewInventoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Precarica campi del form
        setupForm()

        // Setup checkbox listeners
        setupCheckboxListeners()

        // Setup zone multi-select
        setupZoneMultiSelect()

        // Load data from API
        loadData()

        // Click listeners
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnCreate.setOnClickListener { createInventory() }
    }

    /**
     * Precarica i campi del form con dati utente
     */
    private fun setupForm() {
        // Place non editabile - usa usr_def_place dell'utente
        val userPlace = sessionManager.getUserPlace() ?: "N/A"
        val placeName = sessionManager.getPlaceName() ?: ""
        binding.tvPlace.text = if (placeName.isNotEmpty()) {
            "$userPlace ($placeName)"
        } else {
            userPlace
        }

        // Nome inventario precaricato con pattern: "userName place data-ora"
        val userName = sessionManager.getUserName() ?: "User"
        val dateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        binding.etInventoryName.setText("$userName $userPlace $dateTime")
    }

    /**
     * Setup listeners per checkbox mutualmente esclusive
     */
    private fun setupCheckboxListeners() {
        binding.cbFromChecklist.setOnCheckedChangeListener { _, isChecked ->
            // Se checked, deseleziona l'altra checkbox
            if (isChecked) {
                binding.cbFromRfidStock.isChecked = false
            }
            // Abilita/disabilita spinner checklist
            binding.spinnerChecklist.isEnabled = isChecked
            binding.spinnerChecklist.alpha = if (isChecked) 1.0f else 0.5f
        }

        binding.cbFromRfidStock.setOnCheckedChangeListener { _, isChecked ->
            // Se checked, deseleziona l'altra checkbox
            if (isChecked) {
                binding.cbFromChecklist.isChecked = false
            }
            // Abilita/disabilita spinner place e zone
            binding.spinnerLastPlace.isEnabled = isChecked
            binding.tvSelectedZones.isEnabled = isChecked
            binding.layoutLastPlace.alpha = if (isChecked) 1.0f else 0.5f
            binding.layoutLastZone.alpha = if (isChecked) 1.0f else 0.5f
        }
    }

    /**
     * Setup multi-select per le zone
     */
    private fun setupZoneMultiSelect() {
        binding.tvSelectedZones.setOnClickListener {
            if (!binding.cbFromRfidStock.isChecked) return@setOnClickListener
            showZoneMultiSelectDialog()
        }
    }

    /**
     * Mostra dialog per selezione multipla zone
     */
    private fun showZoneMultiSelectDialog() {
        if (zones.isEmpty()) {
            Toast.makeText(this, "Nessuna zona disponibile", Toast.LENGTH_SHORT).show()
            return
        }

        val zoneNames = zones.map { "${it.zone_id} - ${it.zone_name ?: ""}" }.toTypedArray()
        val checkedItems = zones.map { selectedZoneIds.contains(it.zone_id) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Seleziona Zone")
            .setMultiChoiceItems(zoneNames, checkedItems) { _, which, isChecked ->
                val zoneId = zones[which].zone_id
                if (isChecked) {
                    if (!selectedZoneIds.contains(zoneId)) {
                        selectedZoneIds.add(zoneId)
                    }
                } else {
                    selectedZoneIds.remove(zoneId)
                }
            }
            .setPositiveButton("OK") { _, _ ->
                updateSelectedZonesText()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /**
     * Aggiorna il testo delle zone selezionate
     */
    private fun updateSelectedZonesText() {
        binding.tvSelectedZones.text = if (selectedZoneIds.isEmpty()) {
            "Seleziona zone..."
        } else {
            selectedZoneIds.joinToString(", ")
        }
    }

    /**
     * Carica dati da API (checklist, places, zones)
     */
    private fun loadData() {
        lifecycleScope.launch {
            try {
                // Load checklist con type=CHI
                val checklistResponse = RetrofitClient.apiService.getChecklists("CHI")
                if (checklistResponse.isSuccessful) {
                    checklists = checklistResponse.body() ?: emptyList()
                    setupChecklistSpinner()
                }

                // Load places
                val placesResponse = RetrofitClient.apiService.getAllPlaces()
                if (placesResponse.isSuccessful) {
                    places = placesResponse.body() ?: emptyList()
                    setupPlaceSpinners()
                }

                // Load zones
                val zonesResponse = RetrofitClient.apiService.getAllZones()
                if (zonesResponse.isSuccessful) {
                    zones = zonesResponse.body() ?: emptyList()
                    setupZoneSpinners()
                }
            } catch (e: Exception) {
                Toast.makeText(this@NewInventoryActivity, "Errore caricamento dati: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Setup spinner checklist
     */
    private fun setupChecklistSpinner() {
        val items = if (checklists.isEmpty()) {
            listOf("Nessuna checklist disponibile")
        } else {
            checklists.map { "${it.chk_code ?: ""} - ${it.chk_place ?: ""} - ${it.chk_notes ?: ""}" }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerChecklist.adapter = adapter

        binding.spinnerChecklist.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedChecklistId = if (checklists.isNotEmpty() && position < checklists.size) {
                    checklists[position].chk_id
                } else {
                    0
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedChecklistId = 0
            }
        }
    }

    /**
     * Setup spinner per place (Last Place, Detected Place, Missed Place)
     */
    private fun setupPlaceSpinners() {
        val userPlaceId = sessionManager.getUserPlace()
        val placeItems = places.map { "${it.place_id} - ${it.place_name ?: ""}" }

        // Trova indice del place di default dell'utente
        val defaultIndex = places.indexOfFirst { it.place_id == userPlaceId }.takeIf { it >= 0 } ?: 0

        // Last Place spinner
        val lastPlaceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, placeItems)
        lastPlaceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLastPlace.adapter = lastPlaceAdapter
        binding.spinnerLastPlace.setSelection(defaultIndex)
        binding.spinnerLastPlace.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedLastPlaceId = if (places.isNotEmpty()) places[position].place_id else null
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedLastPlaceId = null
            }
        }

        // Detected Place spinner
        val detPlaceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, placeItems)
        detPlaceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDetPlace.adapter = detPlaceAdapter
        binding.spinnerDetPlace.setSelection(defaultIndex)
        binding.spinnerDetPlace.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDetPlaceId = if (places.isNotEmpty()) places[position].place_id else null
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedDetPlaceId = null
            }
        }

        // Missed Place spinner
        val misPlaceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, placeItems)
        misPlaceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMisPlace.adapter = misPlaceAdapter
        binding.spinnerMisPlace.setSelection(defaultIndex)
        binding.spinnerMisPlace.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMisPlaceId = if (places.isNotEmpty()) places[position].place_id else null
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedMisPlaceId = null
            }
        }
    }

    /**
     * Setup spinner per zone (Detected Zone, Missed Zone)
     */
    private fun setupZoneSpinners() {
        val zoneItems = zones.map { "${it.zone_id} - ${it.zone_name ?: ""}" }

        // Detected Zone spinner
        val detZoneAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, zoneItems)
        detZoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDetZone.adapter = detZoneAdapter
        binding.spinnerDetZone.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDetZoneId = if (zones.isNotEmpty()) zones[position].zone_id else null
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedDetZoneId = null
            }
        }

        // Missed Zone spinner
        val misZoneAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, zoneItems)
        misZoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMisZone.adapter = misZoneAdapter
        binding.spinnerMisZone.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMisZoneId = if (zones.isNotEmpty()) zones[position].zone_id else null
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedMisZoneId = null
            }
        }
    }

    /**
     * Crea un nuovo inventario tramite API
     */
    private fun createInventory() {
        val name = binding.etInventoryName.text.toString().trim()
        val note = binding.etNotes.text.toString().trim()

        // Validazione nome inventario
        if (name.isEmpty()) {
            Toast.makeText(this, "Nome inventario richiesto", Toast.LENGTH_SHORT).show()
            return
        }

        val placeId = sessionManager.getUserPlace()

        if (placeId == null) {
            Toast.makeText(this, "Errore: luogo utente non trovato", Toast.LENGTH_LONG).show()
            return
        }

        // Determina valori in base alle checkbox
        val invChkId = if (binding.cbFromChecklist.isChecked) selectedChecklistId else 0
        val isFromRfidStock = binding.cbFromRfidStock.isChecked
        val invLastZones = if (isFromRfidStock && selectedZoneIds.isNotEmpty()) {
            selectedZoneIds.joinToString(",")
        } else {
            null
        }
        val invLastPlace = if (isFromRfidStock) selectedLastPlaceId else null

        // Disabilita pulsante durante creazione
        binding.btnCreate.isEnabled = false
        binding.btnCreate.text = "Creazione..."

        lifecycleScope.launch {
            try {
                val request = CreateInventoryRequest(
                    name = name,
                    note = note.ifEmpty { null },
                    placeId = placeId,
                    invChkId = invChkId,
                    invZones = null, // DEPRECATED
                    invDetPlace = selectedDetPlaceId,
                    invDetZone = selectedDetZoneId,
                    invMisPlace = selectedMisPlaceId,
                    invMisZone = selectedMisZoneId,
                    invLast = isFromRfidStock,
                    invLastPlace = invLastPlace,
                    invLastZones = invLastZones
                )

                val response = RetrofitClient.apiService.createInventory(request)

                if (response.success) {
                    // Torna alla lista con risultato OK
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(
                        this@NewInventoryActivity,
                        "Errore: ${response.error}",
                        Toast.LENGTH_LONG
                    ).show()
                    resetCreateButton()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@NewInventoryActivity,
                    "Errore di rete: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                resetCreateButton()
            }
        }
    }

    /**
     * Riabilita il pulsante "Crea Inventario"
     */
    private fun resetCreateButton() {
        binding.btnCreate.isEnabled = true
        binding.btnCreate.text = "Crea Inventario"
    }
}
