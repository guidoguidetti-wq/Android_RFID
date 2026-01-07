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

        // Default values
        const val DEFAULT_TAG_MODE = "mode_c" // All tags
        const val DEFAULT_POWER = 270
        const val DEFAULT_MIN_RSSI = -70
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
}
