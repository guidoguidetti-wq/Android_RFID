package com.rfid.reader

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.rfid.reader.databinding.ActivityDashboardBinding
import com.rfid.reader.utils.SessionManager

class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Verifica login
        if (!sessionManager.isLoggedIn()) {
            android.util.Log.w(TAG, "User not logged in, redirecting to LoginActivity")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // Mostra nome utente e place details completi
        val userName = sessionManager.getUserName() ?: "User"
        val placeDetails = sessionManager.getPlaceDetails()

        binding.tvUserName.text = userName
        binding.tvUserPlace.text = placeDetails

        android.util.Log.d(TAG, "Dashboard loaded for user: $userName - Place: $placeDetails")
    }

    private fun setupListeners() {
        // Menu hamburger per logout
        binding.btnMenu.setOnClickListener {
            showLogoutDialog()
        }

        // Locate Tag
        binding.btnLocateTag.setOnClickListener {
            android.util.Log.d(TAG, "Navigating to LocateActivity")
            val intent = Intent(this, LocateActivity::class.java)
            startActivity(intent)
        }

        // Tag Info
        binding.btnTagInfo.setOnClickListener {
            android.util.Log.d(TAG, "Navigating to TagInfoActivity")
            val intent = Intent(this, TagInfoActivity::class.java)
            startActivity(intent)
        }

        // Inventario - Naviga a InventoryListActivity
        binding.btnInventory.setOnClickListener {
            android.util.Log.d(TAG, "Navigating to InventoryListActivity")
            val intent = Intent(this, InventoryListActivity::class.java)
            startActivity(intent)
        }

        // Settings - Navigate to SettingsActivity
        binding.btnSettings.setOnClickListener {
            android.util.Log.d(TAG, "Navigating to SettingsActivity")
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Letture
        binding.btnTransfers.setOnClickListener {
            android.util.Log.d(TAG, "Navigating to LettureActivity")
            startActivity(Intent(this, LettureActivity::class.java))
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Vuoi effettuare il logout?")
            .setPositiveButton("Sì") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun performLogout() {
        android.util.Log.d(TAG, "User logged out")
        sessionManager.logout()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        // Mostra dialog di conferma logout invece di tornare indietro
        showLogoutDialog()
    }

    companion object {
        private const val TAG = "DashboardActivity"
    }
}
