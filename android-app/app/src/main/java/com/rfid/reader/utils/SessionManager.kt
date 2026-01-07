package com.rfid.reader.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestisce la sessione utente tramite SharedPreferences
 */
class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "RFIDSession"
        private const val KEY_USERNAME = "username"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PLACE = "user_place"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_PLACE_NAME = "place_name"
        private const val KEY_PLACE_TYPE = "place_type"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    /**
     * Salva i dati di login dell'utente
     */
    fun saveLogin(
        username: String,
        userName: String,
        userPlace: String,
        placeName: String? = null,
        placeType: String? = null,
        userRole: String = "operator"
    ) {
        prefs.edit().apply {
            putString(KEY_USERNAME, username)
            putString(KEY_USER_NAME, userName)
            putString(KEY_USER_PLACE, userPlace)
            putString(KEY_USER_ROLE, userRole)
            putString(KEY_PLACE_NAME, placeName)
            putString(KEY_PLACE_TYPE, placeType)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
        android.util.Log.d("SessionManager", "Login saved for user: $username ($userName) - Place: $userPlace ($placeName)")
    }

    /**
     * Verifica se l'utente è loggato
     */
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    /**
     * Ottieni username (user_id)
     */
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    /**
     * Ottieni nome visualizzato
     */
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    /**
     * Ottieni place predefinito dell'utente
     */
    fun getUserPlace(): String? = prefs.getString(KEY_USER_PLACE, null)

    /**
     * Ottieni ruolo utente
     */
    fun getUserRole(): String? = prefs.getString(KEY_USER_ROLE, null)

    /**
     * Ottieni nome del place
     */
    fun getPlaceName(): String? = prefs.getString(KEY_PLACE_NAME, null)

    /**
     * Ottieni tipo del place
     */
    fun getPlaceType(): String? = prefs.getString(KEY_PLACE_TYPE, null)

    /**
     * Ottieni place details formattati: "place_id (place_name) - place_type"
     */
    fun getPlaceDetails(): String {
        val placeId = getUserPlace() ?: "N/A"
        val placeName = getPlaceName() ?: "Unknown"
        val placeType = getPlaceType() ?: ""
        return "$placeId ($placeName) - $placeType"
    }

    /**
     * Logout - cancella tutti i dati della sessione
     */
    fun logout() {
        val username = getUsername()
        prefs.edit().clear().apply()
        android.util.Log.d("SessionManager", "User logged out: $username")
    }

    /**
     * Aggiorna il place dell'utente (se cambia durante la sessione)
     */
    fun updateUserPlace(place: String) {
        prefs.edit().putString(KEY_USER_PLACE, place).apply()
        android.util.Log.d("SessionManager", "User place updated to: $place")
    }
}
