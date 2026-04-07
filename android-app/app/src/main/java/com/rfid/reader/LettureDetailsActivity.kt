package com.rfid.reader

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rfid.reader.network.LettureItemResponse
import com.rfid.reader.network.RetrofitClient
import kotlinx.coroutines.launch

/**
 * Dettagli items della sessione Letture corrente.
 * Identica a InventoryDetailsActivity: filtri All/Expected/Unexpected/Lost,
 * tap su item → Locate (RssiMonitorActivity).
 * Riceve userId via Intent extra "USER_ID".
 */
class LettureDetailsActivity : AppCompatActivity() {

    private val apiService = RetrofitClient.apiService

    private lateinit var rvItems:             RecyclerView
    private lateinit var tvEmpty:             TextView
    private lateinit var tvItemCount:         TextView
    private lateinit var tvSessionLabel:      TextView
    private lateinit var progressBar:         ProgressBar
    private lateinit var btnFilterAll:        Button
    private lateinit var btnFilterExpected:   Button
    private lateinit var btnFilterUnexpected: Button
    private lateinit var btnFilterLost:       Button

    private var userId = ""
    private var allItems: List<LettureItemResponse> = emptyList()
    private var currentFilter = FilterType.ALL

    private val adapter = LettureItemAdapter { item ->
        // Tap → Locate con RssiMonitor
        val intent = Intent(this, RssiMonitorActivity::class.java).apply {
            putExtra("TARGET_EPC", item.epc)
            putExtra("AUTO_START", true)
        }
        startActivity(intent)
    }

    enum class FilterType { ALL, EXPECTED, UNEXPECTED, LOST }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_letture_details)

        userId = intent.getStringExtra("USER_ID") ?: ""
        if (userId.isEmpty()) {
            Toast.makeText(this, "Errore: userId mancante", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        setupRecyclerView()
        setupFilterButtons()
        loadItems()
    }

    private fun bindViews() {
        rvItems             = findViewById(R.id.rvItems)
        tvEmpty             = findViewById(R.id.tvEmpty)
        tvItemCount         = findViewById(R.id.tvItemCount)
        tvSessionLabel      = findViewById(R.id.tvSessionLabel)
        progressBar         = findViewById(R.id.progressBar)
        btnFilterAll        = findViewById(R.id.btnFilterAll)
        btnFilterExpected   = findViewById(R.id.btnFilterExpected)
        btnFilterUnexpected = findViewById(R.id.btnFilterUnexpected)
        btnFilterLost       = findViewById(R.id.btnFilterLost)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        tvSessionLabel.text = "Utente: $userId"
    }

    private fun setupRecyclerView() {
        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = adapter
    }

    private fun setupFilterButtons() {
        btnFilterAll.setOnClickListener        { applyFilter(FilterType.ALL) }
        btnFilterExpected.setOnClickListener   { applyFilter(FilterType.EXPECTED) }
        btnFilterUnexpected.setOnClickListener { applyFilter(FilterType.UNEXPECTED) }
        btnFilterLost.setOnClickListener       { applyFilter(FilterType.LOST) }

        updateFilterButtonsUI(FilterType.ALL)
    }

    private fun loadItems() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = apiService.getLettureItems(userId)
                if (response.isSuccessful) {
                    allItems = response.body() ?: emptyList()
                    applyFilter(currentFilter)
                } else {
                    tvEmpty.text = "Errore caricamento dati"
                    tvEmpty.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                tvEmpty.text = "Errore: ${e.message}"
                tvEmpty.visibility = View.VISIBLE
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun applyFilter(filter: FilterType) {
        currentFilter = filter
        updateFilterButtonsUI(filter)

        val filtered = when (filter) {
            FilterType.ALL        -> allItems
            FilterType.EXPECTED   -> allItems.filter { it.inv_expected == true }
            FilterType.UNEXPECTED -> allItems.filter { it.inv_unexpected == true }
            FilterType.LOST       -> allItems.filter { it.inv_lost == true }
        }

        adapter.submitList(filtered)

        val label = when (filter) {
            FilterType.ALL        -> "Totale"
            FilterType.EXPECTED   -> "Expected"
            FilterType.UNEXPECTED -> "Unexpected"
            FilterType.LOST       -> "Lost"
        }
        tvItemCount.text = "$label: ${filtered.size} items"

        if (filtered.isEmpty()) {
            tvEmpty.text = when (filter) {
                FilterType.ALL        -> "Nessun tag nella sessione"
                FilterType.EXPECTED   -> "Nessun tag expected"
                FilterType.UNEXPECTED -> "Nessun tag unexpected"
                FilterType.LOST       -> "Nessun tag lost"
            }
            tvEmpty.visibility = View.VISIBLE
            rvItems.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvItems.visibility = View.VISIBLE
        }
    }

    private fun updateFilterButtonsUI(selected: FilterType) {
        val buttons = listOf(
            btnFilterAll        to FilterType.ALL,
            btnFilterExpected   to FilterType.EXPECTED,
            btnFilterUnexpected to FilterType.UNEXPECTED,
            btnFilterLost       to FilterType.LOST
        )

        for ((button, filterType) in buttons) {
            if (filterType == selected) {
                when (filterType) {
                    FilterType.ALL        -> { button.setBackgroundColor(Color.parseColor("#2196F3")); button.setTextColor(Color.WHITE) }
                    FilterType.EXPECTED   -> { button.setBackgroundColor(Color.parseColor("#4CAF50")); button.setTextColor(Color.WHITE) }
                    FilterType.UNEXPECTED -> { button.setBackgroundColor(Color.parseColor("#FF9800")); button.setTextColor(Color.WHITE) }
                    FilterType.LOST       -> { button.setBackgroundColor(Color.parseColor("#F44336")); button.setTextColor(Color.WHITE) }
                }
            } else {
                button.setBackgroundColor(Color.WHITE)
                when (filterType) {
                    FilterType.ALL        -> button.setTextColor(Color.parseColor("#2196F3"))
                    FilterType.EXPECTED   -> button.setTextColor(Color.parseColor("#4CAF50"))
                    FilterType.UNEXPECTED -> button.setTextColor(Color.parseColor("#FF9800"))
                    FilterType.LOST       -> button.setTextColor(Color.parseColor("#F44336"))
                }
            }
        }
    }
}

// ── Adapter ─────────────────────────────────────────────────────────────────

class LettureItemAdapter(
    private val onItemClick: (LettureItemResponse) -> Unit = {}
) : RecyclerView.Adapter<LettureItemAdapter.ViewHolder>() {

    private var items: List<LettureItemResponse> = emptyList()

    companion object {
        private val COLOR_EXPECTED   = Color.parseColor("#E8F5E9")
        private val COLOR_UNEXPECTED = Color.parseColor("#FFF3E0")
        private val COLOR_LOST       = Color.parseColor("#FFEBEE")
        private val COLOR_NORMAL     = Color.WHITE
    }

    fun submitList(list: List<LettureItemResponse>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView:    androidx.cardview.widget.CardView = view.findViewById(R.id.cardView)
        private val tvProductId: android.widget.TextView = view.findViewById(R.id.tvProductId)
        private val tvFldd01:    android.widget.TextView = view.findViewById(R.id.tvFldd01)
        private val tvFld01:     android.widget.TextView = view.findViewById(R.id.tvFld01)
        private val tvFld02:     android.widget.TextView = view.findViewById(R.id.tvFld02)
        private val tvFld03:     android.widget.TextView = view.findViewById(R.id.tvFld03)
        private val tvEpc:       android.widget.TextView = view.findViewById(R.id.tvEpc)
        private val tvMovCount:  android.widget.TextView = view.findViewById(R.id.tvMovCount)

        fun bind(item: LettureItemResponse) {
            tvProductId.text = item.product_id ?: "N/A"
            tvEpc.text = "EPC: ${item.epc}"
            tvMovCount.visibility = View.GONE

            tvFldd01.text = item.fldd01 ?: ""
            tvFldd01.visibility = if (item.fldd01.isNullOrBlank()) View.GONE else View.VISIBLE

            tvFld01.text = item.fld01 ?: ""
            tvFld01.visibility = if (item.fld01.isNullOrBlank()) View.GONE else View.VISIBLE

            tvFld02.text = item.fld02 ?: ""
            tvFld02.visibility = if (item.fld02.isNullOrBlank()) View.GONE else View.VISIBLE

            tvFld03.text = item.fld03 ?: ""
            tvFld03.visibility = if (item.fld03.isNullOrBlank()) View.GONE else View.VISIBLE

            val bgColor = when {
                item.inv_lost == true       -> COLOR_LOST
                item.inv_expected == true   -> COLOR_EXPECTED
                item.inv_unexpected == true -> COLOR_UNEXPECTED
                else                        -> COLOR_NORMAL
            }
            cardView.setCardBackgroundColor(bgColor)

            itemView.setOnClickListener { onItemClick(item) }

            tvProductId.setOnLongClickListener {
                val cb = it.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("Product ID", item.product_id))
                android.widget.Toast.makeText(it.context, "Product ID copiato", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
        }
    }
}
