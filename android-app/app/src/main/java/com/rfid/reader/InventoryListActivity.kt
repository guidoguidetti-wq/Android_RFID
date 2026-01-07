package com.rfid.reader

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rfid.reader.databinding.ActivityInventoryListBinding
import com.rfid.reader.databinding.ItemInventoryBinding
import com.rfid.reader.network.InventoryResponse
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class InventoryListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInventoryListBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: InventoryAdapter
    private var allInventories: List<InventoryResponse> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventoryListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupUI()
        setupRecyclerView()
        loadInventories()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        // Search functionality
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterInventories(s.toString())
            }
        })
    }

    private fun setupRecyclerView() {
        adapter = InventoryAdapter { inventory ->
            // Naviga alla pagina scan
            android.util.Log.d(TAG, "Opening inventory: ${inventory.inv_id}")
            val intent = Intent(this, InventoryScanActivity::class.java)
            intent.putExtra("INVENTORY_ID", inventory.inv_id)
            intent.putExtra("INVENTORY_NAME", inventory.inv_name)
            intent.putExtra("INVENTORY_START_DATE", inventory.inv_start_date)
            intent.putExtra("INVENTORY_COUNT", inventory.items_count)
            startActivity(intent)
        }
        binding.rvInventories.layoutManager = LinearLayoutManager(this)
        binding.rvInventories.adapter = adapter
    }

    private fun loadInventories() {
        val userPlace = sessionManager.getUserPlace()
        if (userPlace == null) {
            Toast.makeText(this, "Errore: Place utente non trovato", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        android.util.Log.d(TAG, "Loading inventories for place: $userPlace")
        showLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getOpenInventories(userPlace)

                if (response.isSuccessful) {
                    allInventories = response.body() ?: emptyList()
                    android.util.Log.d(TAG, "Loaded ${allInventories.size} inventories")
                    adapter.submitList(allInventories)

                    // Show empty state if no inventories
                    if (allInventories.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvInventories.visibility = View.GONE
                    } else {
                        binding.tvEmptyState.visibility = View.GONE
                        binding.rvInventories.visibility = View.VISIBLE
                    }
                } else {
                    Toast.makeText(
                        this@InventoryListActivity,
                        "Errore caricamento inventari: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading inventories", e)
                Toast.makeText(
                    this@InventoryListActivity,
                    "Errore connessione: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun filterInventories(query: String) {
        val filtered = if (query.isBlank()) {
            allInventories
        } else {
            allInventories.filter {
                it.inv_name.contains(query, ignoreCase = true) ||
                        it.inv_id.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.rvInventories.visibility = if (loading) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        // Ricarica inventari quando si torna a questa activity
        loadInventories()
    }

    companion object {
        private const val TAG = "InventoryListActivity"
    }
}

/**
 * RecyclerView Adapter per la lista inventari
 */
class InventoryAdapter(
    private val onItemClick: (InventoryResponse) -> Unit
) : RecyclerView.Adapter<InventoryAdapter.ViewHolder>() {

    private var inventories: List<InventoryResponse> = emptyList()

    fun submitList(list: List<InventoryResponse>) {
        inventories = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInventoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(inventories[position])
    }

    override fun getItemCount() = inventories.size

    inner class ViewHolder(private val binding: ItemInventoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(inventory: InventoryResponse) {
            binding.tvInventoryName.text = inventory.inv_name
            binding.tvInventoryState.text = inventory.inv_state.uppercase()
            binding.tvInventoryCount.text = inventory.items_count.toString()

            // Format date: "2025-12-20T10:30:00" -> "20.12.2025"
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val date = inputFormat.parse(inventory.inv_start_date)
                binding.tvInventoryDate.text = date?.let { outputFormat.format(it) } ?: inventory.inv_start_date
            } catch (e: Exception) {
                binding.tvInventoryDate.text = inventory.inv_start_date
            }

            // Color stato
            binding.tvInventoryState.setTextColor(
                if (inventory.inv_state == "open") {
                    binding.root.context.getColor(android.R.color.holo_green_dark)
                } else {
                    binding.root.context.getColor(android.R.color.darker_gray)
                }
            )

            binding.root.setOnClickListener {
                onItemClick(inventory)
            }
        }
    }
}
