package com.rfid.reader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.integration.android.IntentIntegrator
import com.rfid.reader.network.PlaceResponse
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.network.ZoneResponse
import com.rfid.reader.utils.SessionManager
import kotlinx.coroutines.launch

class LettureActivity : AppCompatActivity() {

    private val apiService     = RetrofitClient.apiService
    private lateinit var sessionManager: SessionManager

    private var places: List<PlaceResponse> = emptyList()
    private var zones: List<ZoneResponse> = emptyList()

    // ---- modalità lettura ----
    private lateinit var rgModalitaLettura: RadioGroup
    private lateinit var layoutTrasferimento: LinearLayout
    private lateinit var spinnerPlace: Spinner
    private lateinit var spinnerZone: Spinner
    private lateinit var etRiferimento: EditText
    private lateinit var etNote: EditText

    // ---- checklist ----
    private lateinit var rgChecklist: RadioGroup
    private lateinit var layoutChecklistCode: LinearLayout
    private lateinit var etChecklistCode: EditText
    private lateinit var btnScanBarcode: ImageButton

    // ---- scope registrazione (transfer + checklist) ----
    private lateinit var layoutRegistrazioneScope: LinearLayout
    private lateinit var rgRegistrazioneScope: RadioGroup

    // ---- pulsanti ----
    private lateinit var btnStart: Button
    private lateinit var btnCancel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_letture)

        sessionManager = SessionManager(this)
        bindViews()
        setupListeners()
        loadPlacesAndZones()
    }

    private fun bindViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rgModalitaLettura = findViewById(R.id.rgModalitaLettura)
        layoutTrasferimento = findViewById(R.id.layoutTrasferimento)
        spinnerPlace = findViewById(R.id.spinnerPlace)
        spinnerZone = findViewById(R.id.spinnerZone)
        etRiferimento = findViewById(R.id.etRiferimento)
        etNote = findViewById(R.id.etNote)

        rgChecklist = findViewById(R.id.rgChecklist)
        layoutChecklistCode = findViewById(R.id.layoutChecklistCode)
        etChecklistCode = findViewById(R.id.etChecklistCode)
        btnScanBarcode = findViewById(R.id.btnScanChecklistBarcode)

        layoutRegistrazioneScope = findViewById(R.id.layoutRegistrazioneScope)
        rgRegistrazioneScope     = findViewById(R.id.rgRegistrazioneScope)

        btnStart = findViewById(R.id.btnStart)
        btnCancel = findViewById(R.id.btnCancel)
    }

    private fun updateRegistrazioneScopeVisibility() {
        val isTransfer  = rgModalitaLettura.checkedRadioButtonId == R.id.rbRegistraTrasferimento
        val isChecklist = rgChecklist.checkedRadioButtonId == R.id.rbChecklistSku ||
                          rgChecklist.checkedRadioButtonId == R.id.rbChecklistEpc
        layoutRegistrazioneScope.visibility = if (isTransfer && isChecklist) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        // Modalità lettura: mostra/nascondi campi trasferimento + scope
        rgModalitaLettura.setOnCheckedChangeListener { _, checkedId ->
            val isTransfer = checkedId == R.id.rbRegistraTrasferimento
            layoutTrasferimento.visibility = if (isTransfer) View.VISIBLE else View.GONE
            updateRegistrazioneScopeVisibility()
        }

        // Checklist: mostra/nascondi campo codice + scope
        rgChecklist.setOnCheckedChangeListener { _, checkedId ->
            val needsCode = checkedId == R.id.rbChecklistSku || checkedId == R.id.rbChecklistEpc
            layoutChecklistCode.visibility = if (needsCode) View.VISIBLE else View.GONE
            updateRegistrazioneScopeVisibility()
        }

        // Scan barcode per codice checklist
        btnScanBarcode.setOnClickListener {
            val integrator = IntentIntegrator(this)
            integrator.setCaptureActivity(PortraitCaptureActivity::class.java)
            integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
            integrator.setPrompt("Scansiona il codice checklist")
            integrator.setBeepEnabled(true)
            integrator.setBarcodeImageEnabled(false)
            integrator.initiateScan()
        }

        btnStart.setOnClickListener { handleStart() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun loadPlacesAndZones() {
        lifecycleScope.launch {
            try {
                val placesResp = apiService.getAllPlaces()
                val zonesResp = apiService.getAllZones()
                if (placesResp.isSuccessful) {
                    places = placesResp.body() ?: emptyList()
                    setupPlaceSpinner()
                }
                if (zonesResp.isSuccessful) {
                    zones = zonesResp.body() ?: emptyList()
                    setupZoneSpinner()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Errore caricamento places/zones", e)
                Toast.makeText(this@LettureActivity, "Errore caricamento luoghi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPlaceSpinner() {
        val items = places.map { "${it.place_id} – ${it.place_name ?: it.place_id}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPlace.adapter = adapter
    }

    private fun setupZoneSpinner() {
        val items = zones.map { "${it.zone_id} – ${it.zone_name ?: it.zone_id}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerZone.adapter = adapter
    }

    private fun handleStart() {
        if (!validateForm()) return

        val params = buildSessionParams()
        android.util.Log.d(TAG, "Avvio sessione lettura: $params")

        val userId = sessionManager.getUserName() ?: "user"

        val intent = Intent(this, LettureSessionActivity::class.java).apply {
            putExtra("USER_ID",                 userId)
            putExtra("READING_MODE",            if (params.registraTransferimento) "transfer" else "readonly")
            putExtra("CHECKLIST_MODE",          when (params.checklistMode) {
                ChecklistMode.SKU -> "sku"
                ChecklistMode.EPC -> "epc"
                else              -> "none"
            })
            putExtra("CHECKLIST_CODE",          params.checklistCode)
            putExtra("DEST_PLACE_ID",           params.destPlaceId)
            putExtra("DEST_ZONE_ID",            params.destZoneId)
            putExtra("RIFERIMENTO",             params.riferimento)
            putExtra("NOTE",                    params.note)
            putExtra("REGISTRA_SOLO_EXPECTED",  params.registraSoloExpected)
        }
        startActivity(intent)
    }

    private fun validateForm(): Boolean {
        val isTransfer = rgModalitaLettura.checkedRadioButtonId == R.id.rbRegistraTrasferimento

        if (isTransfer) {
            if (places.isEmpty()) {
                Toast.makeText(this, "Caricamento luoghi in corso, riprova tra un momento", Toast.LENGTH_SHORT).show()
                return false
            }
            if (zones.isEmpty()) {
                Toast.makeText(this, "Caricamento zone in corso, riprova tra un momento", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        val needsChecklist = rgChecklist.checkedRadioButtonId == R.id.rbChecklistSku ||
                rgChecklist.checkedRadioButtonId == R.id.rbChecklistEpc

        if (needsChecklist && etChecklistCode.text.toString().trim().isEmpty()) {
            etChecklistCode.error = "Inserisci il codice checklist"
            etChecklistCode.requestFocus()
            return false
        }

        return true
    }

    private fun buildSessionParams(): SessionParams {
        val isTransfer = rgModalitaLettura.checkedRadioButtonId == R.id.rbRegistraTrasferimento
        val selectedPlace = if (isTransfer && places.isNotEmpty()) places[spinnerPlace.selectedItemPosition] else null
        val selectedZone = if (isTransfer && zones.isNotEmpty()) zones[spinnerZone.selectedItemPosition] else null

        val checklistMode = when (rgChecklist.checkedRadioButtonId) {
            R.id.rbChecklistSku -> ChecklistMode.SKU
            R.id.rbChecklistEpc -> ChecklistMode.EPC
            else -> ChecklistMode.NONE
        }

        val registraSoloExpected = rgRegistrazioneScope.checkedRadioButtonId == R.id.rbSoloExpected

        return SessionParams(
            registraTransferimento = isTransfer,
            destPlaceId = selectedPlace?.place_id,
            destZoneId = selectedZone?.zone_id,
            riferimento = etRiferimento.text.toString().trim().takeIf { it.isNotEmpty() },
            note = etNote.text.toString().trim().takeIf { it.isNotEmpty() },
            checklistMode = checklistMode,
            checklistCode = if (checklistMode != ChecklistMode.NONE)
                etChecklistCode.text.toString().trim().takeIf { it.isNotEmpty() }
            else null,
            registraSoloExpected = registraSoloExpected
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                etChecklistCode.setText(result.contents)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackPressed() {
        finish()
    }

    // ---- Data classes ----

    enum class ChecklistMode { NONE, SKU, EPC }

    data class SessionParams(
        val registraTransferimento: Boolean,
        val destPlaceId: String?,
        val destZoneId: String?,
        val riferimento: String?,
        val note: String?,
        val checklistMode: ChecklistMode,
        val checklistCode: String?,
        val registraSoloExpected: Boolean = true
    )

    companion object {
        private const val TAG = "LettureActivity"
    }
}
