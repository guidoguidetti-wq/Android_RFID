package com.rfid.reader.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Default URL - usato se non configurato nelle SharedPreferences
    private const val DEFAULT_BASE_URL = "http://192.168.0.55:3000/"
    private const val PREFS_NAME = "RFIDSettings"
    private const val KEY_BACKEND_URL = "backend_url"

    private var currentBaseUrl: String = DEFAULT_BASE_URL
    private var retrofit: Retrofit? = null
    private var _apiService: ApiService? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Inizializza RetrofitClient con il Context per leggere le SharedPreferences
     * Chiamare in Application.onCreate() o nella prima Activity
     */
    fun init(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentBaseUrl = prefs.getString(KEY_BACKEND_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        rebuildRetrofit()
        android.util.Log.d("RetrofitClient", "Initialized with URL: $currentBaseUrl")
    }

    /**
     * Aggiorna l'URL del backend e ricostruisce il client Retrofit
     */
    fun updateBaseUrl(newUrl: String) {
        if (newUrl != currentBaseUrl) {
            currentBaseUrl = newUrl
            rebuildRetrofit()
            android.util.Log.d("RetrofitClient", "Updated URL to: $currentBaseUrl")
        }
    }

    /**
     * Ritorna l'URL corrente del backend
     */
    fun getCurrentBaseUrl(): String = currentBaseUrl

    private fun rebuildRetrofit() {
        retrofit = Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        _apiService = retrofit!!.create(ApiService::class.java)
    }

    val apiService: ApiService
        get() {
            if (_apiService == null) {
                // Fallback: crea con URL di default se non inizializzato
                rebuildRetrofit()
            }
            return _apiService!!
        }
}
