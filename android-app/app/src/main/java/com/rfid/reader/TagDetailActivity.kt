package com.rfid.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rfid.reader.databinding.ActivityTagDetailBinding
import com.rfid.reader.databinding.ItemTagHistoryBinding
import com.rfid.reader.network.ItemHistoryResponse
import com.rfid.reader.viewmodel.TagDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

class TagDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTagDetailBinding
    private lateinit var viewModel: TagDetailViewModel
    private lateinit var adapter: TagHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTagDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val epc = intent.getStringExtra("EPC") ?: ""
        val productId = intent.getStringExtra("PRODUCT_ID")

        binding.tvEpc.text = "EPC: $epc"
        binding.tvProductId.text = productId ?: "NON CENSITO"

        viewModel = ViewModelProvider(this)[TagDetailViewModel::class.java]

        setupRecyclerView()
        setupObservers()

        viewModel.loadTagData(epc, productId)

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = TagHistoryAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.product.observe(this) { product ->
            if (product != null) {
                updateProductFields(product, viewModel.labels.value ?: emptyMap())
            }
        }

        viewModel.labels.observe(this) { labels ->
            val product = viewModel.product.value
            if (product != null) {
                updateProductFields(product, labels)
            }
        }

        viewModel.history.observe(this) { history ->
            adapter.submitList(history)
        }
    }

    private fun updateProductFields(product: com.rfid.reader.network.ProductResponse, labels: Map<String, String>) {
        binding.glProductFields.removeAllViews()

        listOf(
            product.fldd01,
            product.fld01,
            product.fld02,
            product.fld03,
            product.fld04,
            product.fld05
        ).forEach { value ->
            if (!value.isNullOrBlank()) {
                binding.glProductFields.addView(TextView(this).apply {
                    text = value
                    setPadding(0, 4, 0, 4)
                })
            }
        }
    }
}

class TagHistoryAdapter : RecyclerView.Adapter<TagHistoryAdapter.ViewHolder>() {
    private var items: List<ItemHistoryResponse> = emptyList()

    fun submitList(list: List<ItemHistoryResponse>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTagHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemTagHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ItemHistoryResponse) {
            // Format timestamp: yyyy-MM-dd'T'HH:mm:ss.SSSZ to dd.MM.yyyy HH:mm:ss
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd.MM.yyyy - HH:mm:ss", Locale.getDefault())
                val date = inputFormat.parse(item.mov_timestamp)
                binding.tvTimestamp.text = date?.let { outputFormat.format(it) } ?: item.mov_timestamp
            } catch (e: Exception) {
                binding.tvTimestamp.text = item.mov_timestamp
            }

            binding.tvPlace.text = "Place: ${item.place_name ?: item.mov_dest_place ?: "N/A"}"
            binding.tvZone.text = "Zone: ${item.zone_name ?: item.mov_dest_zone ?: "N/A"}"
            binding.tvCausal.text = item.reference_desc ?: "N/A"
        }
    }
}
