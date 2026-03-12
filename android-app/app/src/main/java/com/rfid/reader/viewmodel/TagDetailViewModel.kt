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
            try {
                // 1. Load Labels
                val labelsResp = apiService.getProductLabels()
                if (labelsResp.isSuccessful) {
                    val labelMap = labelsResp.body()?.associate { it.field_name to (it.label_text ?: it.field_name) } ?: emptyMap()
                    _labels.value = labelMap
                }

                // 2. Load Product Details
                if (productId != null && productId != "NON CENSITO") {
                    val prodResp = apiService.getProductById(productId)
                    if (prodResp.isSuccessful) {
                        _product.value = prodResp.body()
                    }
                }

                // 3. Load History
                val historyResp = apiService.getItemHistory(epc)
                if (historyResp.isSuccessful) {
                    _history.value = historyResp.body() ?: emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("TagDetailViewModel", "Error loading tag data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
