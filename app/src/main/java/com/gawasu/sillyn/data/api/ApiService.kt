package com.gawasu.sillyn.data.api

import WeatherResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("weather") // API endpoint relative to base URL
    suspend fun getWeather(
        @Query("q") city: String, // Query parameter: city name
        @Query("appid") apiKey: String, // Query parameter: API key
        @Query("units") units: String = "metric" // Query parameter: units (default metric)
    ): Response<WeatherResponse> // Retrofit Response object, suspend function for coroutines

    @GET("weather") // API endpoint relative to base URL - for weather by coordinates
    suspend fun getWeatherByLocation(
        @Query("lat") lat: Double, // Query parameter: latitude
        @Query("lon") lon: Double, // Query parameter: longitude
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): Response<WeatherResponse>
}