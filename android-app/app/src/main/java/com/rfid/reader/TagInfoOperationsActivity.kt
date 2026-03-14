package com.rfid.reader

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rfid.reader.network.ChecklistScanItem
import com.rfid.reader.network.CreateChecklistFromScanRequest
import com.rfid.reader.network.PlaceResponse
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.network.TransferRequest
import com.rfid.reader.network.ZoneResponse
import com.rfid.reader.utils.SessionManager
import com.rfid.reader.viewmodel.TagInfoViewModel
import kotlinx.coroutines.launch

class TagInfoOperationsActivity : AppCompatActivity() {

    private val apiService = RetrofitClient.apiService
    private val tags get() = TagInfoViewModel.sharedTagList

    private var places: List<PlaceResponse> = emptyList()
    private var zones: List<ZoneResponse> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tag_info_operations)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvTagsInfo).text = "${tags.size} tag validati"

        loadPlacesAndZones()

        findViewById<Button>(R.id.btnCreateChecklist).setOnClickListener {
            showCreateChecklistDialog()
        }

        findViewById<Button>(R.id.btnTransfer).setOnClickListener {
            showTransferDialog()
        }
    }

    private fun loadPlacesAndZones() {
        lifecycleScope.launch {
            try {
                val placesResp = apiService.getAllPlaces()
                val zonesResp = apiService.getAllZones()
                if (placesResp.isSuccessful) places = placesResp.body() ?: emptyList()
                if (zonesResp.isSuccessful) zones = zonesResp.body() ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading places/zones", e)
            }
        }
    }

    private fun showCreateChecklistDialog() {
        if (tags.isEmpty()) {
            Toast.makeText(this, "Nessun tag validato da aggiungere", Toast.LENGTH_SHORT).show()
            return
        }
        if (zones.isEmpty()) {
            Toast.makeText(this, "Caricamento zone in corso, riprova tra un momento", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_checklist, null)
        val etCode = dialogView.findViewById<EditText>(R.id.etChecklistCode)
        val etNotes = dialogView.findViewById<EditText>(R.id.etChecklistNotes)
        val spinnerZone = dialogView.findViewById<Spinner>(R.id.spinnerZone)

        val zoneAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            zones.map { "${it.zone_id} – ${it.zone_name ?: it.zone_id}" }
        )
        zoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerZone.adapter = zoneAdapter

        AlertDialog.Builder(this)
            .setTitle("Crea Checklist")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Conferma") { _, _ ->
                val code = etCode.text.toString().trim()
                if (code.isEmpty()) {
                    Toast.makeText(this, "Il codice è obbligatorio", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val notes = etNotes.text.toString().trim().takeIf { it.isNotEmpty() }
                val selectedZone = zones[spinnerZone.selectedItemPosition]
                createChecklist(code, notes, selectedZone.zone_id)
            }
            .show()
    }

    private fun createChecklist(code: String, notes: String?, zoneId: String?) {
        val items = tags.map { ChecklistScanItem(epc = it.epc, product_id = it.product_id) }

        lifecycleScope.launch {
            try {
                val response = apiService.createChecklistFromScan(
                    CreateChecklistFromScanRequest(chk_code = code, chk_notes = notes, chk_zone = zoneId, items = items)
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    Toast.makeText(
                        this@TagInfoOperationsActivity,
                        "Checklist creata: ID ${body?.chk_id} — ${body?.items_count} items, ${body?.products_count} prodotti",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@TagInfoOperationsActivity, "Errore: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TagInfoOperationsActivity, "Errore di rete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTransferDialog() {
        if (tags.isEmpty()) {
            Toast.makeText(this, "Nessun tag validato da trasferire", Toast.LENGTH_SHORT).show()
            return
        }
        if (places.isEmpty() || zones.isEmpty()) {
            Toast.makeText(this, "Caricamento luoghi in corso, riprova tra un momento", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transfer, null)
        val spinnerPlace = dialogView.findViewById<Spinner>(R.id.spinnerPlace)
        val spinnerZone = dialogView.findViewById<Spinner>(R.id.spinnerZone)
        val etRef = dialogView.findViewById<EditText>(R.id.etTransferRef)

        val placeAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            places.map { "${it.place_id} – ${it.place_name ?: it.place_id}" }
        )
        placeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPlace.adapter = placeAdapter

        val zoneAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            zones.map { "${it.zone_id} – ${it.zone_name ?: it.zone_id}" }
        )
        zoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerZone.adapter = zoneAdapter

        AlertDialog.Builder(this)
            .setTitle("Trasferisci ${tags.size} tag")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Trasferisci") { _, _ ->
                val selectedPlace = places[spinnerPlace.selectedItemPosition]
                val selectedZone = zones[spinnerZone.selectedItemPosition]
                val ref = etRef.text.toString().trim().takeIf { it.isNotEmpty() }
                transferTags(selectedPlace.place_id, selectedZone.zone_id, ref)
            }
            .show()
    }

    private fun transferTags(placeId: String, zoneId: String, ref: String?) {
        val epcs = tags.map { it.epc }
        val userName = SessionManager(this).getUserName()

        lifecycleScope.launch {
            try {
                val response = apiService.transferTags(
                    TransferRequest(
                        place_id = placeId,
                        zone_id = zoneId,
                        epcs = epcs,
                        mov_ref = ref,
                        mov_user = userName
                    )
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    Toast.makeText(
                        this@TagInfoOperationsActivity,
                        "Trasferiti ${body?.transferred_count} tag → $placeId / $zoneId",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val errMsg = try {
                        val json = org.json.JSONObject(response.errorBody()?.string() ?: "{}")
                        json.optString("error", "Errore ${response.code()}")
                    } catch (e: Exception) {
                        "Errore ${response.code()}"
                    }
                    Toast.makeText(this@TagInfoOperationsActivity, errMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TagInfoOperationsActivity, "Errore di rete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val TAG = "TagInfoOperations"
    }
}
