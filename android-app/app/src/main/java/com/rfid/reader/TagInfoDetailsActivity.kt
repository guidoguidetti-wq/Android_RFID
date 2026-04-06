package com.rfid.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rfid.reader.databinding.ActivityTagInfoDetailsBinding
import com.rfid.reader.databinding.ItemInventoryDetailBinding
import com.rfid.reader.network.InventoryItemDetail
import com.rfid.reader.viewmodel.TagInfoViewModel

class TagInfoDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTagInfoDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTagInfoDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = TagInfoDetailsAdapter { item ->
            val intent = Intent(this, TagDetailActivity::class.java)
            intent.putExtra("EPC", item.epc)
            intent.putExtra("PRODUCT_ID", item.product_id)
            startActivity(intent)
        }

        binding.rvTagDetails.layoutManager = LinearLayoutManager(this)
        binding.rvTagDetails.adapter = adapter
        adapter.submitList(TagInfoViewModel.sharedTagList)

        binding.btnBack.setOnClickListener { finish() }
    }
}

class TagInfoDetailsAdapter(private val onItemClick: (InventoryItemDetail) -> Unit) :
    RecyclerView.Adapter<TagInfoDetailsAdapter.ViewHolder>() {

    private var items: List<InventoryItemDetail> = emptyList()

    fun submitList(list: List<InventoryItemDetail>) {
        items = list
        notifyDataSetChanged()
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

    inner class ViewHolder(private val binding: ItemInventoryDetailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InventoryItemDetail) {
            binding.tvProductId.text = item.product_id ?: "N/A"
            binding.tvEpc.text = "EPC: ${item.epc}"

            binding.tvFld01.text = item.fld01 ?: ""
            binding.tvFld01.visibility = if (item.fld01.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.tvFld02.text = item.fld02 ?: ""
            binding.tvFld02.visibility = if (item.fld02.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.tvFld03.text = item.fld03 ?: ""
            binding.tvFld03.visibility = if (item.fld03.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.tvFldd01.text = item.fldd01 ?: ""
            binding.tvFldd01.visibility = if (item.fldd01.isNullOrBlank()) View.GONE else View.VISIBLE

            // Place / Zone last seen
            binding.tvPlaceLast.text = item.place_last ?: ""
            binding.tvPlaceLast.visibility = if (item.place_last.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.tvZoneLast.text = item.zone_last ?: ""
            binding.tvZoneLast.visibility = if (item.zone_last.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.tvMovCount.text = "${item.movCount} mov"

            binding.tvProductId.setOnClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Product ID", item.product_id ?: "")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "Product ID copiato", Toast.LENGTH_SHORT).show()
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
