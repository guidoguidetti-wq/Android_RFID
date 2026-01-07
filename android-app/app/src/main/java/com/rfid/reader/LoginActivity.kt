package com.rfid.reader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rfid.reader.databinding.ActivityLoginBinding
import com.rfid.reader.network.LoginRequest
import com.rfid.reader.network.RetrofitClient
import com.rfid.reader.utils.SessionManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Se già loggato, vai al Dashboard
        if (sessionManager.isLoggedIn()) {
            android.util.Log.d(TAG, "User already logged in, navigating to Dashboard")
            navigateToDashboard()
            return
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            // Validazione input
            if (username.isBlank()) {
                showError("Inserire username")
                return@setOnClickListener
            }

            if (password.isBlank()) {
                showError("Inserire password")
                return@setOnClickListener
            }

            hideError()
            performLogin(username, password)
        }

        // Enter key su password field
        binding.etPassword.setOnEditorActionListener { _, _, _ ->
            binding.btnLogin.performClick()
            true
        }
    }

    private fun performLogin(username: String, password: String) {
        android.util.Log.d(TAG, "Login attempt for user: $username")

        // Disabilita UI durante login
        setLoginInProgress(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.login(LoginRequest(username, password))

                if (response.isSuccessful && response.body()?.success == true) {
                    val user = response.body()!!.user
                    val placeDetails = user.place_details
                    android.util.Log.d(TAG, "Login successful: ${user.user_id} - ${user.user_name} - Place: ${user.usr_def_place} (${placeDetails?.place_name})")

                    // Salva sessione con place details
                    sessionManager.saveLogin(
                        username = user.user_id,
                        userName = user.user_name ?: user.user_id,
                        userPlace = user.usr_def_place,
                        placeName = placeDetails?.place_name,
                        placeType = placeDetails?.place_type,
                        userRole = user.user_role ?: "operator"
                    )

                    // Toast success
                    Toast.makeText(
                        this@LoginActivity,
                        "Benvenuto, ${user.user_name ?: user.user_id}!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Naviga a Dashboard
                    navigateToDashboard()
                } else {
                    // Login fallito
                    val errorMsg = response.body()?.toString() ?: "Credenziali non valide"
                    android.util.Log.e(TAG, "Login failed: $errorMsg")
                    showError("Credenziali non valide")
                    setLoginInProgress(false)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Login error", e)
                showError("Errore di connessione: ${e.message}")
                setLoginInProgress(false)
            }
        }
    }

    private fun setLoginInProgress(inProgress: Boolean) {
        binding.btnLogin.isEnabled = !inProgress
        binding.etUsername.isEnabled = !inProgress
        binding.etPassword.isEnabled = !inProgress
        binding.btnLogin.text = if (inProgress) "Autenticazione..." else "Login"
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}
