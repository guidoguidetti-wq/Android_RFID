package com.rfid.reader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.rfid.reader.viewmodel.LettureSessionViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity di scansione RFID per la sessione Letture.
 * Supporta 3 modalità checklist (normal / SKU / EPC) identiche agli Inventari
 * ma usa inventory_items_temp anziché inventory_items.
 * Riceve i parametri di sessione da LettureActivity via Intent.
 */
class LettureSessionActivity : AppCompatActivity() {

    private lateinit var viewModel: LettureSessionViewModel

    // ── Parametri sessione (da Intent) ──────────────────────────────────────
    private var userId:                String  = ""
    private var readingMode:           String  = "readonly"  // "readonly" | "transfer"
    private var checklistMode:         String  = "none"      // "none" | "sku" | "epc"
    private var checklistCode:         String? = null
    private var destPlaceId:           String? = null
    private var destZoneId:            String? = null
    private var riferimento:           String? = null
    private var note:                  String? = null

    // ── Views ───────────────────────────────────────────────────────────────
    private lateinit var tvTitle:           TextView
    private lateinit var tvSessionDate:     TextView
    private lateinit var tvSessionInfo:     TextView
    private lateinit var llBadge:           LinearLayout
    private lateinit var tvBadgeLabel:      TextView
    private lateinit var tvBadgeTotal:      TextView
    private lateinit var tvTotalTagsCount:  TextView
    private lateinit var layoutSubCounters: LinearLayout
    private lateinit var layoutValidated:   LinearLayout
    private lateinit var tvExpectedCount:   TextView
    private lateinit var tvUnexpectedCount: TextView
    private lateinit var tvLostCount:       TextView
    private lateinit var tvValidatedCount:  TextView
    private lateinit var tvPendingAnnotation: TextView
    private lateinit var tvIgnoredAnnotation: TextView
    private lateinit var tvReaderStatus:    TextView
    private lateinit var progressConnection: ProgressBar
    private lateinit var tvConnectionStatus: TextView
    private lateinit var btnPlayPause:      Button
    private lateinit var btnClear:          Button
    private lateinit var btnConfirm:        Button
    private lateinit var btnSettings:       Button

    companion object {
        private const val TAG = "LettureSession"
        private const val PERM_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_letture_session)

        // Leggi Intent extras
        userId        = intent.getStringExtra("USER_ID") ?: ""
        readingMode   = intent.getStringExtra("READING_MODE") ?: "readonly"
        checklistMode = intent.getStringExtra("CHECKLIST_MODE") ?: "none"
        checklistCode = intent.getStringExtra("CHECKLIST_CODE")
        destPlaceId   = intent.getStringExtra("DEST_PLACE_ID")
        destZoneId    = intent.getStringExtra("DEST_ZONE_ID")
        riferimento   = intent.getStringExtra("RIFERIMENTO")
        note          = intent.getStringExtra("NOTE")

        if (userId.isEmpty()) {
            Toast.makeText(this, "Errore: utente non identificato", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        setupUI()

        viewModel = ViewModelProvider(this)[LettureSessionViewModel::class.java]
        viewModel.initSession(userId, checklistMode, checklistCode)

        setupObservers()
        setupListeners()

        if (checkBluetoothPermissions()) {
            viewModel.connectReader()
        } else {
            requestBluetoothPermissions()
        }
    }

    private fun bindViews() {
        tvTitle              = findViewById(R.id.tvTitle)
        tvSessionDate        = findViewById(R.id.tvSessionDate)
        tvSessionInfo        = findViewById(R.id.tvSessionInfo)
        llBadge              = findViewById(R.id.llBadge)
        tvBadgeLabel         = findViewById(R.id.tvBadgeLabel)
        tvBadgeTotal         = findViewById(R.id.tvBadgeTotal)
        tvTotalTagsCount     = findViewById(R.id.tvTotalTagsCount)
        layoutSubCounters    = findViewById(R.id.layoutSubCounters)
        layoutValidated      = findViewById(R.id.layoutValidated)
        tvExpectedCount      = findViewById(R.id.tvExpectedCount)
        tvUnexpectedCount    = findViewById(R.id.tvUnexpectedCount)
        tvLostCount          = findViewById(R.id.tvLostCount)
        tvValidatedCount     = findViewById(R.id.tvValidatedCount)
        tvPendingAnnotation  = findViewById(R.id.tvPendingAnnotation)
        tvIgnoredAnnotation  = findViewById(R.id.tvIgnoredAnnotation)
        tvReaderStatus       = findViewById(R.id.tvReaderStatus)
        progressConnection   = findViewById(R.id.progressConnection)
        tvConnectionStatus   = findViewById(R.id.tvConnectionStatus)
        btnPlayPause         = findViewById(R.id.btnPlayPause)
        btnClear             = findViewById(R.id.btnClear)
        btnConfirm           = findViewById(R.id.btnConfirm)
        btnSettings          = findViewById(R.id.btnSettings)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { onBackPressed() }
    }

    private fun setupUI() {
        val now = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        tvSessionDate.text = now

        val modeLabel = when (readingMode) {
            "transfer" -> "Trasferimento → ${destPlaceId ?: ""}/${destZoneId ?: ""}"
            else       -> "Sola Lettura"
        }
        tvSessionInfo.text = modeLabel
    }

    private fun setupObservers() {
        viewModel.initError.observe(this) { err ->
            err?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.sessionModeLive.observe(this) { mode ->
            val isChecklist = mode == "checklist" || mode == "checklist_epc"
            layoutSubCounters.visibility = if (isChecklist) View.VISIBLE else View.GONE
            layoutValidated.visibility   = if (isChecklist) View.GONE   else View.VISIBLE

            val (label, color) = when (mode) {
                "checklist"     -> Pair("SKU", "#4CAF50")
                "checklist_epc" -> Pair("EPC", "#FF9800")
                else            -> Pair("Normal", "#9E9E9E")
            }
            tvBadgeLabel.text = label
            llBadge.background.setTint(android.graphics.Color.parseColor(color))
        }

        viewModel.totalExpectedLive.observe(this) { total ->
            tvBadgeTotal.text = if (total > 0) "($total)" else ""
        }

        viewModel.totalTagsCount.observe(this) { count ->
            tvTotalTagsCount.text = count.toString()
        }

        viewModel.expectedCount.observe(this) { count ->
            tvExpectedCount.text  = count.toString()
            tvValidatedCount.text = count.toString()
        }

        viewModel.unexpectedCount.observe(this) { count ->
            tvUnexpectedCount.text = count.toString()
        }

        viewModel.lostCount.observe(this) { count ->
            tvLostCount.text = count.toString()
        }

        viewModel.ignoredCount.observe(this) { count ->
            tvIgnoredAnnotation.text = "$count Ignored"
        }

        viewModel.pendingCount.observe(this) { count ->
            tvPendingAnnotation.text = "$count pending • "
        }

        viewModel.readerStatus.observe(this) { status ->
            tvReaderStatus.text = status
            val connected = status.contains("Connected", ignoreCase = true)
            tvReaderStatus.setTextColor(
                if (connected) getColor(android.R.color.holo_green_dark)
                else           getColor(android.R.color.holo_red_dark)
            )
        }

        viewModel.isConnected.observe(this) { connected ->
            btnPlayPause.isEnabled = connected
        }

        viewModel.isScanning.observe(this) { scanning ->
            btnPlayPause.text = if (scanning) "⏸" else "▶"
        }

        viewModel.connectionProgress.observe(this) { connecting ->
            progressConnection.visibility  = if (connecting) View.VISIBLE else View.GONE
            tvConnectionStatus.visibility  = if (connecting) View.VISIBLE else View.GONE
        }

        viewModel.commitResult.observe(this) { result ->
            result ?: return@observe
            if (result.first) {
                Toast.makeText(this, result.second, Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, result.second, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupListeners() {
        btnPlayPause.setOnClickListener { viewModel.toggleScan() }

        btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, InventorySettingsActivity::class.java))
        }

        btnClear.setOnClickListener { showClearDialog() }
        btnConfirm.setOnClickListener { showConfirmDialog() }
    }

    private fun showClearDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Letture")
            .setMessage("Azzerare tutti i tag letti in questa sessione?")
            .setPositiveButton("Conferma") { _, _ ->
                lifecycleScope.launch {
                    try {
                        viewModel.clearSession()
                        Toast.makeText(this@LettureSessionActivity, "Sessione azzerata", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@LettureSessionActivity, "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showConfirmDialog() {
        val tagCount = viewModel.totalTagsCount.value ?: 0
        if (tagCount == 0) {
            Toast.makeText(this, "Nessun tag da confermare", Toast.LENGTH_SHORT).show()
            return
        }

        val msg = when (readingMode) {
            "transfer" -> "Trasferire $tagCount tag verso $destPlaceId / $destZoneId?"
            else       -> "Registrare la lettura di $tagCount tag?"
        }

        AlertDialog.Builder(this)
            .setTitle("Conferma Sessione")
            .setMessage(msg)
            .setPositiveButton("Conferma") { _, _ ->
                viewModel.stopScan()
                viewModel.commitSession(
                    registraTransferimento = (readingMode == "transfer"),
                    destPlaceId            = destPlaceId,
                    destZoneId             = destZoneId,
                    riferimento            = riferimento,
                    note                   = note
                )
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Uscire dalla sessione?")
            .setMessage("I tag letti NON saranno confermati. Uscire comunque?")
            .setPositiveButton("Esci") { _, _ ->
                lifecycleScope.launch {
                    try { viewModel.clearSession() } catch (_: Exception) {}
                    finish()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun checkBluetoothPermissions(): Boolean {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        }
        return perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestBluetoothPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERM_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST &&
            grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            viewModel.connectReader()
        } else {
            Toast.makeText(this, "Permessi Bluetooth necessari", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (viewModel.isConnected.value == true) {
            viewModel.disconnectReader()
        }
    }
}
