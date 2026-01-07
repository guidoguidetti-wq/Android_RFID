package com.rfid.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private var inventoryId: String = ""
    private var inventoryName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventoryDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inventoryId = intent.getStringExtra("INVENTORY_ID") ?: ""
        inventoryName = intent.getStringExtra("INVENTORY_NAME") ?: ""

        android.util.Log.d(TAG, "Opening details for inventory: $inventoryId ($inventoryName)")

        setupUI()
        setupRecyclerView()
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

    private fun loadDetails() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                android.util.Log.d(TAG, "Loading details for inventory $inventoryId")
                val response = RetrofitClient.apiService.getInventoryItemsDetails(inventoryId)

                if (response.isSuccessful) {
                    val items = response.body() ?: emptyList()
                    android.util.Log.d(TAG, "Loaded ${items.size} items with details")
                    adapter.submitList(items)
                    binding.tvItemCount.text = "Totale: ${items.size} items"

                    if (items.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvDetails.visibility = View.GONE
                    } else {
                        binding.tvEmptyState.visibility = View.GONE
                        binding.rvDetails.visibility = View.VISIBLE
                    }
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
        }
    }
}
