package fh3355.ocr_mobile

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

// --- Data Classes for JSON responses ---

data class OcrFullResponse(
    @SerializedName("recognized_text") val recognizedText: String,
    @SerializedName("preprocessing_time_ms") val preprocessingTimeMs: Long,
    @SerializedName("inference_time_ms") val inferenceTimeMs: Long,
    @SerializedName("total_server_time_ms") val totalServerTimeMs: Long
)

data class OcrInferOnlyResponse(
    @SerializedName("recognized_text") val recognizedText: String,
    @SerializedName("inference_time_ms") val inferenceTimeMs: Long
)

// --- Retrofit API Interface ---

interface ApiService {

    @Multipart
    @POST("/ocr/full")
    suspend fun ocrFullPipeline(
        @Part file: MultipartBody.Part,
        @Part("model_version") modelVersion: RequestBody
    ): OcrFullResponse

    @Multipart
    @POST("/ocr/infer_only")
    suspend fun ocrInferenceOnly(
        @Part file: MultipartBody.Part,
        @Part("model_version") modelVersion: RequestBody
    ): OcrInferOnlyResponse

}

// --- Singleton object to get Retrofit instance ---

object RetrofitClient {
    // The BASE_URL is now dynamically provided by Gradle via BuildConfig.
    // This keeps the URL out of version control and allows for easy environment switching.
    private const val BASE_URL = BuildConfig.BASE_URL

    // Create a logging interceptor
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Custom OkHttpClient with longer timeouts and logging
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor) // Add the logging interceptor
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // Set the custom client
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}
