package com.gawasu.sillyn.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawasu.sillyn.databinding.ActivityMainBinding
import com.gawasu.sillyn.ui.viewmodel.WeatherViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: WeatherViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ActivityResultLauncher cho Permission Request (Modern way - Best practice)
    private lateinit var locationPermissionRequest: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Khởi tạo ActivityResultLauncher cho Location Permissions
        locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    // Quyền ACCESS_COARSE_LOCATION được cấp
                    Log.d(TAG, "Quyền ACCESS_COARSE_LOCATION được cấp")
                    fetchWeatherDataByLocation() // Lấy thời tiết theo vị trí
                }
                else -> {
                    // Quyền bị từ chối
                    Log.d(TAG, "Quyền vị trí bị từ chối")
                    Snackbar.make(binding.root, "Quyền vị trí bị từ chối. Vui lòng nhập tên thành phố.", Snackbar.LENGTH_LONG).show()
                    // Có thể hiển thị UI để người dùng nhập tên thành phố thủ công
                }
            }
        }


        viewModel.weatherData.observe(this) { weatherResponse ->
            if (weatherResponse != null) {
                // Cập nhật UI với dữ liệu thời tiết
                binding.textViewCityName.text = weatherResponse.name
                binding.textViewTemperature.text = weatherResponse.main?.temp?.toString() + " °C"
                binding.textViewDescription.text = weatherResponse.weather?.firstOrNull()?.description
                // ... update các view khác ...
            }
        }

        viewModel.error.observe(this) { errorMessage ->
            if (errorMessage != null) {
                // Hiển thị thông báo lỗi (Snackbar, Toast, v.v.)
                Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
            }
        }

        binding.buttonFetchWeatherLocation.setOnClickListener {
            checkLocationPermissionAndFetchWeather() // Gọi hàm kiểm tra quyền và lấy thời tiết theo vị trí
        }

        binding.buttonFetchWeatherCity.setOnClickListener {
            val cityName = binding.editTextCityName.text.toString()
            if (cityName.isNotBlank()) {
                viewModel.fetchWeatherData(cityName) // Lấy thời tiết theo tên thành phố (không cần location permission)
            } else {
                Snackbar.make(binding.root, "Vui lòng nhập tên thành phố", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkLocationPermissionAndFetchWeather() {
        // Kiểm tra xem đã có quyền ACCESS_COARSE_LOCATION chưa
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Đã có quyền -> lấy vị trí và thời tiết
            Log.d(TAG, "Đã có quyền vị trí, lấy thời tiết theo vị trí")
            fetchWeatherDataByLocation()
        } else {
            // Chưa có quyền -> yêu cầu quyền
            Log.d(TAG, "Chưa có quyền vị trí, yêu cầu quyền")
            requestLocationPermission()
        }
    }


    private fun requestLocationPermission() {
        // Kiểm tra shouldShowRequestPermissionRationale (giải thích lý do)
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            // Hiển thị Rationale Dialog (giải thích tại sao cần quyền)
            Snackbar.make(
                binding.root,
                "Ứng dụng cần quyền truy cập vị trí để hiển thị thời tiết cho vị trí hiện tại của bạn.",
                Snackbar.LENGTH_INDEFINITE
            ).setAction("OK") {
                // Sau khi người dùng đọc rationale, request permission
                locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
            }.show()
        } else {
            // Không cần rationale hoặc lần đầu request -> trực tiếp request permission
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }


    private fun fetchWeatherDataByLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Trường hợp hiếm gặp: Permission bị thu hồi giữa chừng -> cần handle lại (thường không xảy ra)
            Log.e(TAG, "Không có quyền vị trí mặc dù đã kiểm tra trước đó!")
            Snackbar.make(binding.root, "Lỗi quyền vị trí. Vui lòng thử lại.", Snackbar.LENGTH_LONG).show()
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    Log.d(TAG, "Lấy được vị trí: Lat=$latitude, Lon=$longitude")
                    viewModel.fetchWeatherDataByLocation(latitude, longitude) // Gọi ViewModel để lấy thời tiết theo tọa độ
                } else {
                    Log.w(TAG, "Không lấy được vị trí gần nhất.")
                    Snackbar.make(binding.root, "Không lấy được vị trí. Vui lòng thử lại hoặc nhập tên thành phố.", Snackbar.LENGTH_LONG).show()
                    // Có thể thử request location updates nếu cần thiết (phức tạp hơn)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Lỗi lấy vị trí: ${e.message}", e)
                Snackbar.make(binding.root, "Lỗi lấy vị trí: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }


    companion object {
        private const val TAG = "MainActivity"
    }
}