package fh3355.ocr_mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

enum class ProcessingMode { LOCAL, REMOTE_FULL, REMOTE_HYBRID }
enum class ServerModel(val key: String) { V2("v2"), V5("v5") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainScreen() }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ocrProcessor = remember { OcrProcessor() }
    val clipboardManager = LocalClipboardManager.current

    val initialText = "Select an image to start."
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var recognizedText by remember { mutableStateOf(initialText) }
    var initializationError by remember { mutableStateOf<String?>(null) }
    var isInitializing by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var isActualResult by remember { mutableStateOf(false) }

    // State for timings and modes
    var totalTime by remember { mutableStateOf<Long?>(null) }
    var localPreprocessingTime by remember { mutableStateOf<Long?>(null) }
    var networkTime by remember { mutableStateOf<Long?>(null) }
    var serverInferenceTime by remember { mutableStateOf<Long?>(null) }
    var serverPreprocessingTime by remember { mutableStateOf<Long?>(null) }
    var selectedMode by remember { mutableStateOf(ProcessingMode.LOCAL) }
    var selectedServerModel by remember { mutableStateOf(ServerModel.V5) }

    // Effect to clear results when the mode changes
    LaunchedEffect(selectedMode) {
        recognizedText = "Select image and start recognition."
        isActualResult = false
        // FIX: Replaced complex syntax with simple assignments for better compiler compatibility.
        totalTime = null
        localPreprocessingTime = null
        networkTime = null
        serverInferenceTime = null
        serverPreprocessingTime = null
    }

    LaunchedEffect(Unit) {
        ocrProcessor.init(context)?.let { error ->
            initializationError = error
        }
        isInitializing = false
    }

    val ocrLauncher = { uri: Uri ->
        coroutineScope.launch(Dispatchers.Default) {
            isProcessing = true
            isActualResult = false
            // FIX: Replaced complex syntax with simple assignments.
            totalTime = null
            localPreprocessingTime = null
            networkTime = null
            serverInferenceTime = null
            serverPreprocessingTime = null

            try {
                val modelVersionBody = selectedServerModel.key.toRequestBody("text/plain".toMediaTypeOrNull())

                when (selectedMode) {
                    ProcessingMode.LOCAL -> {
                        val preprocStartTime = System.currentTimeMillis()
                        val bitmap = uriToBitmap(context, uri)
                        localPreprocessingTime = System.currentTimeMillis() - preprocStartTime
                        val (text, inferTime) = ocrProcessor.processImage(bitmap)
                        serverInferenceTime = inferTime // Use same state for simplicity
                        recognizedText = text.ifBlank { "No text found." }
                        totalTime = (localPreprocessingTime ?: 0) + (serverInferenceTime ?: 0)
                    }
                    ProcessingMode.REMOTE_FULL, ProcessingMode.REMOTE_HYBRID -> {
                        val preprocStartTime = System.currentTimeMillis()
                        val bitmap = uriToBitmap(context, uri)
                        localPreprocessingTime = System.currentTimeMillis() - preprocStartTime
                        val imageBytes = bitmapToBytes(bitmap)
                        val requestFile = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("file", "image.jpg", requestFile)
                        
                        val networkStartTime = System.currentTimeMillis()
                        if (selectedMode == ProcessingMode.REMOTE_FULL) {
                            val response = RetrofitClient.instance.ocrFullPipeline(body, modelVersionBody)
                            networkTime = System.currentTimeMillis() - networkStartTime
                            recognizedText = response.recognizedText.ifBlank { "No text found." }
                            serverPreprocessingTime = response.preprocessingTimeMs
                            serverInferenceTime = response.inferenceTimeMs
                            totalTime = (localPreprocessingTime ?: 0) + (networkTime ?: 0)
                        } else { // REMOTE_HYBRID
                            val response = RetrofitClient.instance.ocrInferenceOnly(body, modelVersionBody)
                            networkTime = System.currentTimeMillis() - networkStartTime
                            recognizedText = response.recognizedText.ifBlank { "No text found." }
                            serverInferenceTime = response.inferenceTimeMs
                            totalTime = (localPreprocessingTime ?: 0) + (networkTime ?: 0)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in mode $selectedMode ($selectedServerModel)", e)
                recognizedText = "Error: ${e.message}"
            }
            isProcessing = false
            isActualResult = true
        }
    }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) result.uriContent?.let { imageUri = it }
        else Toast.makeText(context, "Cropping failed: ${result.error?.message}", Toast.LENGTH_SHORT).show()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { 
            imageUri = it
            recognizedText = "Image selected."
            isActualResult = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { if (initializationError == null) ocrProcessor.release() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isInitializing && selectedMode == ProcessingMode.LOCAL) {
            CircularProgressIndicator()
            Text("Initializing Local OCR Engine...")
        } else {
            Button(onClick = { imagePickerLauncher.launch("image/*") }) { Text("Select from Gallery") }
        }

        imageUri?.let {
            // Image preview section with a smaller weight
            Column(Modifier.weight(0.7f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(rememberAsyncImagePainter(it), "Selected Image", Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.Gray), contentScale = ContentScale.Fit)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { cropLauncher.launch(CropImageContractOptions(it, CropImageOptions())) }) { Text("Crop Area") }
                    Button(onClick = { ocrLauncher(it) }, enabled = !isProcessing) { Text("Start Recognition") }
                }
                Spacer(Modifier.height(12.dp))
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Processing Mode:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        ProcessingMode.values().forEach { mode ->
                            Row(Modifier.selectable(selected = (mode == selectedMode), onClick = { selectedMode = mode }, role = Role.RadioButton).padding(horizontal = 2.dp)) {
                                RadioButton(selected = (mode == selectedMode), onClick = null, modifier = Modifier.size(20.dp))
                                Text(text = mode.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 1.dp).align(Alignment.CenterVertically))
                            }
                        }
                    }
                    if (selectedMode == ProcessingMode.LOCAL && initializationError != null) {
                         Text("Local engine failed to initialize.", fontSize = 10.sp, color = Color.Red)
                    }
                }

                if (selectedMode != ProcessingMode.LOCAL) {
                    Spacer(Modifier.height(4.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Server Model:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            ServerModel.values().forEach { model ->
                                Row(Modifier.selectable(selected = (model == selectedServerModel), onClick = { selectedServerModel = model }, role = Role.RadioButton).padding(horizontal = 4.dp)) {
                                    RadioButton(selected = (model == selectedServerModel), onClick = null, modifier = Modifier.size(20.dp))
                                    Text(text = model.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 2.dp).align(Alignment.CenterVertically))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Result section with a larger weight
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isProcessing) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                }
            } else {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Recognition Result:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (isActualResult) {
                        Button(onClick = {
                            clipboardManager.setText(AnnotatedString(recognizedText))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy") }
                    }
                }

                totalTime?.let {
                    val color = Color.Gray
                    val size = 12.sp
                    Text("Total Time (Client Perspective): $it ms", fontSize = size, color = color, fontWeight = FontWeight.Bold)
                    when (selectedMode) {
                        ProcessingMode.LOCAL -> {
                            localPreprocessingTime?.let { t -> Text("  - Local Preprocessing: $t ms", fontSize = size, color = color) }
                            serverInferenceTime?.let { t -> Text("  - Local Inference: $t ms", fontSize = size, color = color) }
                        }
                        ProcessingMode.REMOTE_FULL -> {
                            localPreprocessingTime?.let { t -> Text("  - Local Pre-Scaling: $t ms", fontSize = size, color = color) }
                            networkTime?.let { t -> Text("  - Network & Server Time: $t ms", fontSize = size, color = color) }
                            serverPreprocessingTime?.let { p -> Text("    - Server Preprocessing: $p ms", fontSize = size, color = color) }
                            serverInferenceTime?.let { i -> Text("    - Server Inference: $i ms", fontSize = size, color = color) }
                        }
                        ProcessingMode.REMOTE_HYBRID -> {
                            localPreprocessingTime?.let { t -> Text("  - Local Preprocessing: $t ms", fontSize = size, color = color) }
                            networkTime?.let { t -> Text("  - Network & Server Time: $t ms", fontSize = size, color = color) }
                            serverInferenceTime?.let { i -> Text("    - Server Inference: $i ms", fontSize = size, color = color) }
                        }
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxSize().border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))) {
                    SelectionContainer(Modifier.fillMaxSize()) {
                        Text(recognizedText, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// --- Utility Functions ---
@Throws(IOException::class)
fun bitmapToBytes(bitmap: Bitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
    return stream.toByteArray()
}

@Throws(IOException::class)
private fun uriToBitmap(context: Context, uri: Uri): Bitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    val srcWidth = options.outWidth
    val srcHeight = options.outHeight
    if (srcWidth <= 0 || srcHeight <= 0) throw IOException("Failed to decode image bounds for URI: $uri")

    val maxDimension = 1280
    var inSampleSize = 1
    if (srcHeight > maxDimension || srcWidth > maxDimension) {
        val halfHeight = srcHeight / 2
        val halfWidth = srcWidth / 2
        while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
            inSampleSize *= 2
        }
    }

    val downsampledOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    val downsampledBitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, downsampledOptions) } ?: throw IOException("Failed to decode downsampled bitmap from URI: $uri")

    return downsampledBitmap.copy(Bitmap.Config.ARGB_8888, true)
}
