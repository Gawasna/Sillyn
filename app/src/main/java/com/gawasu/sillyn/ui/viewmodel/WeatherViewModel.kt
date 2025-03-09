package com.gawasu.sillyn.ui.viewmodel

import WeatherResponse
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawasu.sillyn.data.remote.ApiClient
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class WeatherViewModel : ViewModel() {
    private val _weatherData = MutableLiveData<WeatherResponse?>()
    val weatherData: LiveData<WeatherResponse?> = _weatherData

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val weatherApi = ApiClient.weatherApiService
    private val apiKey = "8ef663b95689e77daef90ce5aff108b7"

    fun fetchWeatherData(city: String) {
        viewModelScope.launch {
            try {
                val response = weatherApi.getWeather(city = city, apiKey = apiKey)
                if (response.isSuccessful) {
                    _weatherData.postValue(response.body())
                    _error.postValue(null) // Clear error message
                } else {
                    _weatherData.postValue(null)
                    _error.postValue("Lỗi API: ${response.code()} - ${response.message()}")
                    Log.e(TAG, "API Error: ${response.code()} - ${response.message()}")
                }
            } catch (e: IOException) {
                _weatherData.postValue(null)
                _error.postValue("Lỗi mạng: ${e.message}")
                Log.e(TAG, "Network Error: ${e.message}")
            } catch (e: HttpException) {
                _weatherData.postValue(null)
                _error.postValue("Lỗi HTTP: ${e.code()} - ${e.message}")
                Log.e(TAG, "HTTP Error: ${e.code()} - ${e.message}")
            } catch (e: JsonSyntaxException) {
                _weatherData.postValue(null)
                _error.postValue("Lỗi parse JSON: ${e.message}")
                Log.e(TAG, "JSON Parsing Error: ${e.message}")
            } catch (e: Exception) {
                _weatherData.postValue(null)
                _error.postValue("Lỗi không xác định: ${e.message}")
                Log.e(TAG, "Unknown Error: ${e.message}")
            }
        }
    }

    fun fetchWeatherDataByLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                val response = weatherApi.getWeatherByLocation(
                    lat = latitude,
                    lon = longitude,
                    apiKey = apiKey
                )
                if (response.isSuccessful) {
                    _weatherData.postValue(response.body())
                    _error.postValue(null)
                } else {
                    _weatherData.postValue(null)
                    _error.postValue("Lỗi API (Vị trí): ${response.code()} - ${response.message()}")
                    Log.e(TAG, "API Error (Location): ${response.code()} - ${response.message()}")
                }
            } catch (e: IOException) {
                _weatherData.postValue(null)
                _error.postValue("Lỗi mạng (Vị trí): ${e.message}")
                Log.e(TAG, "Network Error (Location): ${e.message}")
            } catch (e: HttpException) {
                _weatherData.postValue(null)
                _error.postValue("Lỗi HTTP (Vị trí): ${e.code()} - ${e.message}")
                Log.e(TAG, "HTTP Error (Location): ${e.code()} - ${e.message}")
            } catch (e: JsonSyntaxException) {
                _weatherData.postValue(null)
                _error.postValue("Lỗi parse JSON (Vị trí): ${e.message}")
                Log.e(TAG, "JSON Parsing Error (Location): ${e.message}")
            } catch (e: SecurityException) {
                _weatherData.postValue(null)
                _error.postValue("Lỗi quyền vị trí (Vị trí): ${e.message}")
                Log.e(
                    TAG,
                    "Security Exception (Location): ${e.message}"
                ) // Có thể xảy ra nếu permission bị thu hồi
            } catch (e: Exception) {
                _weatherData.postValue(null)
                _error.postValue("Lỗi không xác định (Vị trí): ${e.message}")
                Log.e(TAG, "Unknown Error (Location): ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "WeatherViewModel"
    }
}