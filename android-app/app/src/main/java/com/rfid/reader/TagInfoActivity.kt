package com.rfid.reader

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rfid.reader.databinding.ActivityTagInfoBinding
import com.rfid.reader.databinding.ItemInventoryDetailBinding
import com.rfid.reader.network.InventoryItemDetail
import com.rfid.reader.viewmodel.TagInfoViewModel

class TagInfoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTagInfoBinding
    private lateinit var viewModel: TagInfoViewModel
    private lateinit var adapter: TagInfoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTagInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[TagInfoViewModel::class.java]

        setupRecyclerView()
        setupObservers()
        setupListeners()

        // Check permissions prima di connettere
        if (checkBluetoothPermissions()) {
            viewModel.connectReader()
        } else {
            requestBluetoothPermissions()
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        ActivityCompat.requestPermissions(
            this,
            permissions.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                viewModel.connectReader()
            } else {
                Toast.makeText(this, "Permessi Bluetooth necessari per usare il reader RFID", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = TagInfoAdapter { item ->
            val intent = Intent(this, TagDetailActivity::class.java)
            intent.putExtra("EPC", item.epc)
            intent.putExtra("PRODUCT_ID", item.product_id)
            startActivity(intent)
        }
        binding.rvTags.layoutManager = LinearLayoutManager(this)
        binding.rvTags.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.foundTags.observe(this) { tags ->
            adapter.submitList(tags)
            binding.tvFoundCount.text = tags.size.toString()
        }

        viewModel.isConnected.observe(this) { connected ->
            binding.btnPlayPause.isEnabled = connected
        }

        viewModel.isScanning.observe(this) { scanning ->
            binding.btnPlayPause.text = if (scanning) "⏸" else "▶"
        }

        viewModel.readerStatus.observe(this) { status ->
            binding.tvReaderStatus.text = status
            val isConnected = status.contains("Connected", ignoreCase = true)
            binding.tvReaderStatus.setTextColor(
                if (isConnected) getColor(android.R.color.holo_green_dark)
                else getColor(android.R.color.holo_red_dark)
            )
        }

        viewModel.connectionProgress.observe(this) { isConnecting ->
            binding.progressConnection.visibility = if (isConnecting) View.VISIBLE else View.GONE
            binding.tvConnectionStatus.visibility = if (isConnecting) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnPlayPause.setOnClickListener {
            viewModel.toggleScan()
        }

        binding.btnClear.setOnClickListener {
            viewModel.clearTags()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d(TAG, "Activity destroying, disconnecting reader")
        viewModel.disconnectReader()
    }

    companion object {
        private const val TAG = "TagInfoActivity"
        private const val PERMISSION_REQUEST_CODE = 100
    }
}

class TagInfoAdapter(private val onItemClick: (InventoryItemDetail) -> Unit) : 
    RecyclerView.Adapter<TagInfoAdapter.ViewHolder>() {

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

            // Show extra fields based on content
            binding.tvFld01.text = item.fld01 ?: ""
            binding.tvFld01.visibility = if (item.fld01.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.tvFld02.text = item.fld02 ?: ""
            binding.tvFld02.visibility = if (item.fld02.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.tvFld03.text = item.fld03 ?: ""
            binding.tvFld03.visibility = if (item.fld03.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.tvFldd01.text = item.fldd01 ?: ""
            binding.tvFldd01.visibility = if (item.fldd01.isNullOrBlank()) View.GONE else View.VISIBLE

            // Copy product_id to clipboard on click
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
