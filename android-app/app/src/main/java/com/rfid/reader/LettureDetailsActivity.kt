package com.rfid.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
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
 * Mostra i dettagli degli items nella sessione Letture corrente.
 * Riceve userId via Intent extra "USER_ID".
 * Supporta filtri: Tutti / Expected / Unexpected / Lost.
 */
class LettureDetailsActivity : AppCompatActivity() {

    private val apiService = RetrofitClient.apiService

    private lateinit var rvItems:   RecyclerView
    private lateinit var tvEmpty:   TextView
    private lateinit var tvItemCount: TextView
    private lateinit var btnFilterAll:        Button
    private lateinit var btnFilterExpected:   Button
    private lateinit var btnFilterUnexpected: Button
    private lateinit var btnFilterLost:       Button

    private var userId = ""
    private var allItems: List<LettureItemResponse> = emptyList()
    private var currentFilter = Filter.ALL

    private val adapter = LettureItemAdapter()

    enum class Filter { ALL, EXPECTED, UNEXPECTED, LOST }

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
        btnFilterAll        = findViewById(R.id.btnFilterAll)
        btnFilterExpected   = findViewById(R.id.btnFilterExpected)
        btnFilterUnexpected = findViewById(R.id.btnFilterUnexpected)
        btnFilterLost       = findViewById(R.id.btnFilterLost)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = adapter
    }

    private fun setupFilterButtons() {
        btnFilterAll.setOnClickListener        { applyFilter(Filter.ALL) }
        btnFilterExpected.setOnClickListener   { applyFilter(Filter.EXPECTED) }
        btnFilterUnexpected.setOnClickListener { applyFilter(Filter.UNEXPECTED) }
        btnFilterLost.setOnClickListener       { applyFilter(Filter.LOST) }
    }

    private fun loadItems() {
        lifecycleScope.launch {
            try {
                val response = apiService.getLettureItems(userId)
                if (response.isSuccessful) {
                    allItems = response.body() ?: emptyList()
                    applyFilter(currentFilter)
                } else {
                    Toast.makeText(this@LettureDetailsActivity,
                        "Errore caricamento items: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LettureDetailsActivity,
                    "Errore di rete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFilter(filter: Filter) {
        currentFilter = filter

        // Update button alpha to show active
        btnFilterAll.alpha        = if (filter == Filter.ALL)        1f else 0.5f
        btnFilterExpected.alpha   = if (filter == Filter.EXPECTED)   1f else 0.5f
        btnFilterUnexpected.alpha = if (filter == Filter.UNEXPECTED) 1f else 0.5f
        btnFilterLost.alpha       = if (filter == Filter.LOST)       1f else 0.5f

        val filtered = when (filter) {
            Filter.ALL        -> allItems
            Filter.EXPECTED   -> allItems.filter { it.inv_expected == true }
            Filter.UNEXPECTED -> allItems.filter { it.inv_unexpected == true }
            Filter.LOST       -> allItems.filter { it.inv_lost == true }
        }

        tvItemCount.text = filtered.size.toString()
        adapter.setItems(filtered)

        tvEmpty.visibility  = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rvItems.visibility  = if (filtered.isEmpty()) View.GONE    else View.VISIBLE
    }

    // ── Adapter ─────────────────────────────────────────────────────────────

    inner class LettureItemAdapter : RecyclerView.Adapter<LettureItemAdapter.VH>() {

        private var items: List<LettureItemResponse> = emptyList()

        fun setItems(list: List<LettureItemResponse>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_letture_detail, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val viewStatus:  View     = view.findViewById(R.id.viewStatus)
            private val tvProductId: TextView = view.findViewById(R.id.tvProductId)
            private val tvStatus:    TextView = view.findViewById(R.id.tvStatus)
            private val tvFldd01:    TextView = view.findViewById(R.id.tvFldd01)
            private val tvFld01:     TextView = view.findViewById(R.id.tvFld01)
            private val tvFld02:     TextView = view.findViewById(R.id.tvFld02)
            private val tvFld03:     TextView = view.findViewById(R.id.tvFld03)
            private val tvEpc:       TextView = view.findViewById(R.id.tvEpc)

            fun bind(item: LettureItemResponse) {
                tvProductId.text = item.product_id ?: "—"
                tvFldd01.text    = item.fldd01 ?: ""
                tvFld01.text     = item.fld01 ?: ""
                tvFld02.text     = item.fld02 ?: ""
                tvFld03.text     = item.fld03 ?: ""
                tvEpc.text       = item.epc

                val (label, color) = when {
                    item.inv_lost == true       -> Pair("Lost",       0xFFF44336.toInt())
                    item.inv_unexpected == true -> Pair("Unexpected", 0xFFFF9800.toInt())
                    item.inv_expected == true   -> Pair("Expected",   0xFF4CAF50.toInt())
                    else                         -> Pair("Normal",    0xFF9E9E9E.toInt())
                }
                tvStatus.text = label
                tvStatus.setBackgroundColor(color)
                viewStatus.setBackgroundColor(color)

                tvFldd01.visibility = if (item.fldd01.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}
