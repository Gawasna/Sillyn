package com.gawasu.sillyn.data.remote

import com.gawasu.sillyn.data.api.WeatherApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"

    val weatherApiService: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // Gson Converter
            .build()
            .create(WeatherApiService::class.java)
    }
}