import kotlinx.parcelize.Parcelize
import android.os.Parcelable
import com.google.gson.annotations.SerializedName

@Parcelize
data class WeatherResponse(
    val coord: Coord?,
    val weather: List<Weather>?,
    val base: String?,
    val main: MainWeather?,
    val visibility: Int?,
    val wind: Wind?,
    val clouds: Clouds?,
    val dt: Long?,
    val sys: Sys?,
    val timezone: Int?,
    val id: Int?,
    val name: String?,
    val cod: Int?
) : Parcelable

@Parcelize
data class Coord(
    val lon: Double?,
    val lat: Double?
) : Parcelable

@Parcelize
data class Weather(
    val id: Int?,
    val main: String?,
    val description: String?,
    val icon: String?
) : Parcelable

@Parcelize
data class MainWeather(
    val temp: Double?,
    @SerializedName("feels_like") val feelsLike: Double?,
    val temp_min: Double?,
    val temp_max: Double?,
    val pressure: Int?,
    val humidity: Int?,
    @SerializedName("sea_level") val seaLevel: Int?,
    @SerializedName("grnd_level") val grndLevel: Int?
) : Parcelable

@Parcelize
data class Wind(
    val speed: Double?,
    val deg: Int?
) : Parcelable

@Parcelize
data class Clouds(
    val all: Int?
) : Parcelable

@Parcelize
data class Sys(
    val type: Int?,
    val id: Int?,
    val country: String?,
    val sunrise: Long?,
    val sunset: Long?
) : Parcelable