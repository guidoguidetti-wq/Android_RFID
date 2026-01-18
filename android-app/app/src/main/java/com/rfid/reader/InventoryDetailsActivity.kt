package com.rfid.reader

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rfid.reader.databinding.ActivityInventoryDetailsBinding
import com.rfid.reader.databinding.ItemInventoryDetailBinding
import com.rfid.reader.network.InventoryItemDetail
import com.rfid.reader.network.RetrofitClient
import kotlinx.coroutines.launch

class InventoryDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInventoryDetailsBinding
    private lateinit var adapter: InventoryDetailsAdapter
    private var inventoryId: Int = 0
    private var inventoryName: String = ""
    private var inventoryMode: String = "normal"  // normal, checklist, last_place

    // All items loaded from server
    private var allItems: List<InventoryItemDetail> = emptyList()

    // Current filter
    private var currentFilter: FilterType = FilterType.ALL

    enum class FilterType {
        ALL, EXPECTED, UNEXPECTED, LOST
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventoryDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inventoryId = intent.getIntExtra("INVENTORY_ID", 0)
        inventoryName = intent.getStringExtra("INVENTORY_NAME") ?: ""
        inventoryMode = intent.getStringExtra("INVENTORY_MODE") ?: "normal"

        android.util.Log.d(TAG, "Opening details for inventory: $inventoryId ($inventoryName) mode: $inventoryMode")

        setupUI()
        setupRecyclerView()
        setupFilterButtons()
        loadDetails()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            android.util.Log.d(TAG, "Back button pressed")
            finish()
        }
        binding.tvInventoryName.text = inventoryName
    }

    private fun setupRecyclerView() {
        adapter = InventoryDetailsAdapter()
        binding.rvDetails.layoutManager = LinearLayoutManager(this)
        binding.rvDetails.adapter = adapter
    }

    private fun setupFilterButtons() {
        binding.btnFilterAll.setOnClickListener { applyFilter(FilterType.ALL) }
        binding.btnFilterExpected.setOnClickListener { if (it.isEnabled) applyFilter(FilterType.EXPECTED) }
        binding.btnFilterUnexpected.setOnClickListener { if (it.isEnabled) applyFilter(FilterType.UNEXPECTED) }
        binding.btnFilterLost.setOnClickListener { if (it.isEnabled) applyFilter(FilterType.LOST) }

        // Enable/disable buttons based on inventory mode
        // Normal: all disabled (except All)
        // Checklist: Lost disabled, others enabled
        // Stock (last_place): all enabled
        when (inventoryMode) {
            "normal" -> {
                binding.btnFilterExpected.isEnabled = false
                binding.btnFilterUnexpected.isEnabled = false
                binding.btnFilterLost.isEnabled = false
                binding.btnFilterExpected.alpha = 0.4f
                binding.btnFilterUnexpected.alpha = 0.4f
                binding.btnFilterLost.alpha = 0.4f
            }
            "checklist" -> {
                binding.btnFilterExpected.isEnabled = true
                binding.btnFilterUnexpected.isEnabled = true
                binding.btnFilterLost.isEnabled = false
                binding.btnFilterExpected.alpha = 1.0f
                binding.btnFilterUnexpected.alpha = 1.0f
                binding.btnFilterLost.alpha = 0.4f
            }
            "last_place" -> {
                // Stock mode: all enabled
                binding.btnFilterExpected.isEnabled = true
                binding.btnFilterUnexpected.isEnabled = true
                binding.btnFilterLost.isEnabled = true
                binding.btnFilterExpected.alpha = 1.0f
                binding.btnFilterUnexpected.alpha = 1.0f
                binding.btnFilterLost.alpha = 1.0f
            }
        }

        // Initial state: All selected
        updateFilterButtonsUI(FilterType.ALL)
    }

    private fun applyFilter(filter: FilterType) {
        currentFilter = filter
        updateFilterButtonsUI(filter)

        val filteredItems = when (filter) {
            FilterType.ALL -> allItems
            FilterType.EXPECTED -> allItems.filter { it.inv_expected == true }
            FilterType.UNEXPECTED -> allItems.filter { it.inv_unexpected == true }
            FilterType.LOST -> allItems.filter { it.inv_lost == true }
        }

        adapter.submitList(filteredItems)

        // Update item count with filter info
        val filterLabel = when (filter) {
            FilterType.ALL -> "Totale"
            FilterType.EXPECTED -> "Expected"
            FilterType.UNEXPECTED -> "Unexpected"
            FilterType.LOST -> "Lost"
        }
        binding.tvItemCount.text = "$filterLabel: ${filteredItems.size} items"

        // Show/hide empty state
        if (filteredItems.isEmpty()) {
            binding.tvEmptyState.text = when (filter) {
                FilterType.ALL -> "Nessun item nell'inventario"
                FilterType.EXPECTED -> "Nessun item expected"
                FilterType.UNEXPECTED -> "Nessun item unexpected"
                FilterType.LOST -> "Nessun item lost"
            }
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvDetails.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvDetails.visibility = View.VISIBLE
        }
    }

    private fun updateFilterButtonsUI(selected: FilterType) {
        // Reset all buttons to unselected state
        val buttons = listOf(
            binding.btnFilterAll to FilterType.ALL,
            binding.btnFilterExpected to FilterType.EXPECTED,
            binding.btnFilterUnexpected to FilterType.UNEXPECTED,
            binding.btnFilterLost to FilterType.LOST
        )

        for ((button, filterType) in buttons) {
            if (filterType == selected) {
                // Selected state
                when (filterType) {
                    FilterType.ALL -> {
                        button.setBackgroundColor(Color.parseColor("#2196F3"))
                        button.setTextColor(Color.WHITE)
                    }
                    FilterType.EXPECTED -> {
                        button.setBackgroundColor(Color.parseColor("#4CAF50"))
                        button.setTextColor(Color.WHITE)
                    }
                    FilterType.UNEXPECTED -> {
                        button.setBackgroundColor(Color.parseColor("#FF9800"))
                        button.setTextColor(Color.WHITE)
                    }
                    FilterType.LOST -> {
                        button.setBackgroundColor(Color.parseColor("#F44336"))
                        button.setTextColor(Color.WHITE)
                    }
                }
            } else {
                // Unselected state
                button.setBackgroundColor(Color.WHITE)
                when (filterType) {
                    FilterType.ALL -> button.setTextColor(Color.parseColor("#2196F3"))
                    FilterType.EXPECTED -> button.setTextColor(Color.parseColor("#4CAF50"))
                    FilterType.UNEXPECTED -> button.setTextColor(Color.parseColor("#FF9800"))
                    FilterType.LOST -> button.setTextColor(Color.parseColor("#F44336"))
                }
            }
        }
    }

    private fun loadDetails() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                android.util.Log.d(TAG, "Loading details for inventory $inventoryId")
                val response = RetrofitClient.apiService.getInventoryItemsDetails(inventoryId)

                if (response.isSuccessful) {
                    allItems = response.body() ?: emptyList()
                    android.util.Log.d(TAG, "Loaded ${allItems.size} items with details")

                    // Log status counts
                    val expectedCount = allItems.count { it.inv_expected == true }
                    val unexpectedCount = allItems.count { it.inv_unexpected == true }
                    android.util.Log.d(TAG, "Status counts - Expected: $expectedCount, Unexpected: $unexpectedCount")

                    // Apply current filter (defaults to ALL)
                    applyFilter(currentFilter)
                } else {
                    android.util.Log.e(TAG, "Failed to load details: ${response.code()}")
                    binding.tvEmptyState.text = "Errore caricamento dati"
                    binding.tvEmptyState.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading details", e)
                binding.tvEmptyState.text = "Errore: ${e.message}"
                binding.tvEmptyState.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val TAG = "InventoryDetailsActivity"
    }
}

class InventoryDetailsAdapter : RecyclerView.Adapter<InventoryDetailsAdapter.ViewHolder>() {

    private var items: List<InventoryItemDetail> = emptyList()

    companion object {
        // Background colors (very light)
        private val COLOR_EXPECTED = Color.parseColor("#E8F5E9")     // Light green
        private val COLOR_UNEXPECTED = Color.parseColor("#FFF3E0")   // Light orange
        private val COLOR_LOST = Color.parseColor("#FFEBEE")         // Light red
        private val COLOR_NORMAL = Color.WHITE
    }

    fun submitList(list: List<InventoryItemDetail>) {
        items = list
        notifyDataSetChanged()
        android.util.Log.d("InventoryDetailsAdapter", "Submitted ${list.size} items")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInventoryDetailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemInventoryDetailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InventoryItemDetail) {
            binding.tvProductId.text = item.product_id ?: "N/A"

            // Abilita copia su click lungo per Product ID
            binding.tvProductId.setOnLongClickListener {
                val clipboard = it.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Product ID", item.product_id)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(it.context, "Product ID copiato", android.widget.Toast.LENGTH_SHORT).show()
                true
            }

            // fld01 - nasconde se vuoto
            if (!item.fld01.isNullOrBlank()) {
                binding.tvFld01.text = item.fld01
                binding.tvFld01.visibility = View.VISIBLE
            } else {
                binding.tvFld01.visibility = View.GONE
            }

            // fld02 - nasconde se vuoto
            if (!item.fld02.isNullOrBlank()) {
                binding.tvFld02.text = item.fld02
                binding.tvFld02.visibility = View.VISIBLE
            } else {
                binding.tvFld02.visibility = View.GONE
            }

            // fld03 - nasconde se vuoto
            if (!item.fld03.isNullOrBlank()) {
                binding.tvFld03.text = item.fld03
                binding.tvFld03.visibility = View.VISIBLE
            } else {
                binding.tvFld03.visibility = View.GONE
            }

            // fldd01 - nasconde se vuoto
            if (!item.fldd01.isNullOrBlank()) {
                binding.tvFldd01.text = item.fldd01
                binding.tvFldd01.visibility = View.VISIBLE
            } else {
                binding.tvFldd01.visibility = View.GONE
            }

            binding.tvEpc.text = "EPC: ${item.epc}"

            // Set background color based on status
            val bgColor = when {
                item.inv_lost == true -> COLOR_LOST
                item.inv_expected == true -> COLOR_EXPECTED
                item.inv_unexpected == true -> COLOR_UNEXPECTED
                else -> COLOR_NORMAL
            }
            binding.cardView.setCardBackgroundColor(bgColor)
        }
    }
}
