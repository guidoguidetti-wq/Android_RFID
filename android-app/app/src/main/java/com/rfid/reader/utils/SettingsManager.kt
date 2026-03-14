package com.rfid.reader.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestisce le impostazioni dell'applicazione tramite SharedPreferences
 */
class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "RFIDSettings"
        private const val KEY_TAG_MODE = "tag_reading_mode"
        private const val KEY_READER_POWER = "reader_power"
        private const val KEY_MIN_RSSI = "min_rssi"
        private const val KEY_EPC_PREFIX_FILTER = "epc_prefix_filter"
        private const val KEY_INVENTORY_ZONE = "inventory_zone"
        private const val KEY_BEEP_VOLUME = "beep_volume"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_BATCH_DELAY_MS = "batch_delay_ms"
        private const val KEY_TAG_INFO_DELAY_MS = "tag_info_delay_ms"

        // Product filter keys
        private const val KEY_FILTER_FLD01 = "filter_fld01"
        private const val KEY_FILTER_FLD02 = "filter_fld02"
        private const val KEY_FILTER_FLD03 = "filter_fld03"
        private const val KEY_FILTER_FLDD01 = "filter_fldd01"
        private const val KEY_FILTER_FLD01_ENABLED = "filter_fld01_enabled"
        private const val KEY_FILTER_FLD02_ENABLED = "filter_fld02_enabled"
        private const val KEY_FILTER_FLD03_ENABLED = "filter_fld03_enabled"
        private const val KEY_FILTER_FLDD01_ENABLED = "filter_fldd01_enabled"

        // Default values
        const val DEFAULT_TAG_MODE = "mode_c" // All tags
        const val DEFAULT_POWER = 270
        const val DEFAULT_MIN_RSSI = -70
        const val DEFAULT_BEEP_VOLUME = "medium" // low, medium, high
        const val DEFAULT_BACKEND_URL = "http://192.168.0.55:3000/"
        const val DEFAULT_BATCH_DELAY_MS = 300L
        const val DEFAULT_TAG_INFO_DELAY_MS = 100L
    }

    // Tag Reading Mode
    fun getTagReadingMode(): String = prefs.getString(KEY_TAG_MODE, DEFAULT_TAG_MODE) ?: DEFAULT_TAG_MODE
    fun setTagReadingMode(mode: String) = prefs.edit().putString(KEY_TAG_MODE, mode).apply()

    // Reader Power
    fun getReaderPower(): Int = prefs.getInt(KEY_READER_POWER, DEFAULT_POWER)
    fun setReaderPower(power: Int) = prefs.edit().putInt(KEY_READER_POWER, power).apply()

    // Min RSSI
    fun getMinRssi(): Int = prefs.getInt(KEY_MIN_RSSI, DEFAULT_MIN_RSSI)
    fun setMinRssi(rssi: Int) = prefs.edit().putInt(KEY_MIN_RSSI, rssi).apply()

    // EPC Prefix Filter
    fun getEpcPrefixFilter(): String = prefs.getString(KEY_EPC_PREFIX_FILTER, "") ?: ""
    fun setEpcPrefixFilter(prefix: String) = prefs.edit().putString(KEY_EPC_PREFIX_FILTER, prefix).apply()

    // Inventory Zone
    fun getInventoryZone(): String? = prefs.getString(KEY_INVENTORY_ZONE, null)
    fun setInventoryZone(zone: String) = prefs.edit().putString(KEY_INVENTORY_ZONE, zone).apply()

    // Beep Volume
    fun getBeepVolume(): String = prefs.getString(KEY_BEEP_VOLUME, DEFAULT_BEEP_VOLUME) ?: DEFAULT_BEEP_VOLUME
    fun setBeepVolume(volume: String) = prefs.edit().putString(KEY_BEEP_VOLUME, volume).apply()

    // Backend URL
    fun getBackendUrl(): String = prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
    fun setBackendUrl(url: String) = prefs.edit().putString(KEY_BACKEND_URL, url).apply()

    // Batch Delay (ms): debounce per il batch-scan verso il backend
    fun getBatchDelayMs(): Long = prefs.getLong(KEY_BATCH_DELAY_MS, DEFAULT_BATCH_DELAY_MS)
    fun setBatchDelayMs(ms: Long) = prefs.edit().putLong(KEY_BATCH_DELAY_MS, ms).apply()

    // Tag Info Delay (ms): ritardo per validazione DB nel Tag Info
    fun getTagInfoDelayMs(): Long = prefs.getLong(KEY_TAG_INFO_DELAY_MS, DEFAULT_TAG_INFO_DELAY_MS)
    fun setTagInfoDelayMs(ms: Long) = prefs.edit().putLong(KEY_TAG_INFO_DELAY_MS, ms).apply()

    // Product Filters
    fun getProductFilter(field: String): String {
        return prefs.getString("filter_$field", "") ?: ""
    }

    fun setProductFilter(field: String, value: String) {
        prefs.edit().putString("filter_$field", value).apply()
    }

    fun isProductFilterEnabled(field: String): Boolean {
        return prefs.getBoolean("filter_${field}_enabled", false)
    }

    fun setProductFilterEnabled(field: String, enabled: Boolean) {
        prefs.edit().putBoolean("filter_${field}_enabled", enabled).apply()
    }

    // Get all active product filters as a map
    fun getActiveProductFilters(): Map<String, String> {
        val filters = mutableMapOf<String, String>()
        val fields = listOf("fld01", "fld02", "fld03", "fldd01")

        fields.forEach { field ->
            if (isProductFilterEnabled(field)) {
                val value = getProductFilter(field)
                if (value.isNotEmpty()) {
                    filters[field] = value
                }
            }
        }

        return filters
    }
}
