package com.rfid.reader.utils

import android.content.Context
import android.util.Log
import com.rfid.reader.network.AppLogRequest
import com.rfid.reader.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Utility per loggare errori bloccanti dell'applicazione nel DB remoto (tabella log).
 * Chiamata fire-and-forget: non blocca il thread chiamante e ignora errori di rete.
 *
 * Uso:
 *   AppLogger.error(context, "LettureSession", "Init sessione fallita", exception)
 */
object AppLogger {

    private const val TAG = "AppLogger"

    // Scope dedicato: sopravvive alle coroutine dell'Activity
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Logga un errore bloccante nel DB.
     * @param context  Contesto Android (per leggere l'utente loggato)
     * @param source   Nome del componente (es. "LettureSession", "Login")
     * @param message  Descrizione breve dell'errore
     * @param cause    Eccezione opzionale — aggiunge stacktrace al log_text
     */
    fun error(context: Context, source: String, message: String, cause: Throwable? = null) {
        val userId = SessionManager(context).getUserName() ?: "unknown"

        val logText = buildString {
            append("[$source] $message")
            if (cause != null) {
                append(" | ${cause.javaClass.simpleName}: ${cause.message}")
                val relevant = cause.stackTrace.take(3).joinToString(" | ") { it.toString() }
                if (relevant.isNotEmpty()) append(" | $relevant")
            }
        }

        // Logcat sempre
        Log.e(TAG, logText)

        // DB fire-and-forget
        scope.launch {
            try {
                RetrofitClient.apiService.postLog(AppLogRequest(userId = userId, logText = logText))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write remote log: ${e.message}")
            }
        }
    }
}
