package com.rfid.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rfid.reader.network.*
import kotlinx.coroutines.launch

class TagDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = RetrofitClient.apiService

    private val _product = MutableLiveData<ProductResponse?>()
    val product: LiveData<ProductResponse?> = _product

    private val _labels = MutableLiveData<Map<String, String>>(emptyMap())
    val labels: LiveData<Map<String, String>> = _labels

    private val _history = MutableLiveData<List<ItemHistoryResponse>>(emptyList())
    val history: LiveData<List<ItemHistoryResponse>> = _history

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadTagData(epc: String, productId: String?) {
        _isLoading.value = true
        viewModelScope.launch {
            // Each step is independent: failures in labels/product don't block history
            try {
                val labelsResp = apiService.getProductLabels()
                if (labelsResp.isSuccessful) {
                    _labels.value = labelsResp.body()
                        ?.associate { it.field_name to (it.label_text ?: it.field_name) }
                        ?: emptyMap()
                }
            } catch (e: Exception) {
                android.util.Log.e("TagDetailViewModel", "Error loading labels", e)
            }

            try {
                if (productId != null && productId != "NON CENSITO") {
                    val prodResp = apiService.getProductById(productId)
                    if (prodResp.isSuccessful) {
                        _product.value = prodResp.body()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TagDetailViewModel", "Error loading product", e)
            }

            try {
                val historyResp = apiService.getItemHistory(epc)
                if (historyResp.isSuccessful) {
                    _history.value = historyResp.body() ?: emptyList()
                } else {
                    android.util.Log.e("TagDetailViewModel", "History fetch failed: ${historyResp.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("TagDetailViewModel", "Error loading history", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
