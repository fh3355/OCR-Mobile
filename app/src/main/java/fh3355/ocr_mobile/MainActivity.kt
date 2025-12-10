package fh3355.ocr_mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

enum class OcrMode {
    LOCAL,
    REMOTE_FULL,
    REMOTE_HYBRID
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ocrProcessor = remember { OcrProcessor() }
    val clipboardManager = LocalClipboardManager.current

    // --- State Management ---
    val initialText = "Select an image to start."
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var recognizedText by remember { mutableStateOf(initialText) }
    var initializationError by remember { mutableStateOf<String?>(null) }
    var isInitializing by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var isActualResult by remember { mutableStateOf(false) }

    // Timing states
    var totalTime by remember { mutableStateOf<Long?>(null) }
    var preprocessingTime by remember { mutableStateOf<Long?>(null) }
    var networkTime by remember { mutableStateOf<Long?>(null) }
    var inferenceTime by remember { mutableStateOf<Long?>(null) }

    // Mode selection state
    var selectedMode by remember { mutableStateOf(OcrMode.LOCAL) }

    // --- Initialization ---
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val error = ocrProcessor.init(context)
            initializationError = error
            isInitializing = false
            if (error != null) {
                recognizedText = "Local OCR engine initialization failed: $error"
                isActualResult = false
            }
        }
    }

    // --- OCR Launcher Logic ---
    val ocrLauncher = { uri: Uri ->
        coroutineScope.launch(Dispatchers.Default) {
            isProcessing = true
            isActualResult = false
            // Reset timings
            totalTime = null
            preprocessingTime = null
            networkTime = null
            inferenceTime = null

            try {
                when (selectedMode) {
                    OcrMode.LOCAL -> {
                        val preprocStartTime = System.currentTimeMillis()
                        val bitmap = uriToBitmap(context, uri)
                        preprocessingTime = System.currentTimeMillis() - preprocStartTime

                        val (text, inferTime) = ocrProcessor.processImage(bitmap)
                        inferenceTime = inferTime

                        recognizedText = text.ifBlank { "No text found." }
                        totalTime = (preprocessingTime ?: 0) + (inferenceTime ?: 0)
                    }

                    OcrMode.REMOTE_FULL -> {
                        // FIX: Pre-scale the image on the client to prevent server OOM errors.
                        val preprocStartTime = System.currentTimeMillis()
                        val bitmap = uriToBitmap(context, uri) // Downscale image first
                        preprocessingTime = System.currentTimeMillis() - preprocStartTime
                        
                        val imageBytes = bitmapToBytes(bitmap) // Send smaller image bytes

                        val networkStartTime = System.currentTimeMillis()
                        val requestFile = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull(), 0, imageBytes.size)
                        val body = MultipartBody.Part.createFormData("file", "image.jpg", requestFile)
                        
                        val response = RetrofitClient.instance.ocrFullPipeline(body)
                        networkTime = System.currentTimeMillis() - networkStartTime

                        recognizedText = response.recognizedText.ifBlank { "No text found." }
                        // Show both local and server preprocessing times for clarity
                        inferenceTime = response.inferenceTimeMs
                        totalTime = (preprocessingTime ?: 0) + (networkTime ?: 0)
                    }

                    OcrMode.REMOTE_HYBRID -> {
                        val preprocStartTime = System.currentTimeMillis()
                        val bitmap = uriToBitmap(context, uri)
                        preprocessingTime = System.currentTimeMillis() - preprocStartTime

                        val networkStartTime = System.currentTimeMillis()
                        val imageBytes = bitmapToBytes(bitmap)
                        val requestFile = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull(), 0, imageBytes.size)
                        val body = MultipartBody.Part.createFormData("file", "image.jpg", requestFile)
                        
                        val response = RetrofitClient.instance.ocrInferenceOnly(body)
                        networkTime = System.currentTimeMillis() - networkStartTime

                        recognizedText = response.recognizedText.ifBlank { "No text found." }
                        inferenceTime = response.inferenceTimeMs
                        totalTime = (preprocessingTime ?: 0) + (networkTime ?: 0)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error during OCR processing in mode $selectedMode", e)
                recognizedText = "Error: ${e.message}"
            }

            isProcessing = false
            isActualResult = true
        }
    }

    // --- Activity Result Launchers ---
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { uri ->
                imageUri = uri
            }
        } else {
            val exception = result.error
            Toast.makeText(context, "Image cropping failed: ${exception?.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                imageUri = it
                recognizedText = "Image selected. Crop or start recognition."
                isActualResult = false
            }
        }
    )
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        // Simplified logic, you can add getTempUri if needed
    }

    DisposableEffect(Unit) {
        onDispose {
            ocrProcessor.release()
        }
    }

    // --- UI Layout ---
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        
        if (isInitializing && selectedMode == OcrMode.LOCAL) {
            CircularProgressIndicator()
            Text("Initializing Local OCR Engine...")
        } else if (initializationError != null && selectedMode == OcrMode.LOCAL) {
            Text(text = "Local engine failed: $initializationError", color = Color.Red)
        } else {
             Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                Text("Select from Gallery")
            }
        }

        imageUri?.let {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = rememberAsyncImagePainter(it),
                    contentDescription = "Selected Image",
                    modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.Gray),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Button(onClick = {
                        val cropOptions = CropImageContractOptions(it, CropImageOptions())
                        cropLauncher.launch(cropOptions)
                    }) {
                        Text("Crop Area")
                    }
                    Button(onClick = { ocrLauncher(it) }, enabled = !isProcessing) {
                        Text("Start Recognition")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // --- Mode Selection UI ---
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    OcrMode.values().forEach { mode ->
                        Row(
                            Modifier
                                .selectable(
                                    selected = (mode == selectedMode),
                                    onClick = { selectedMode = mode },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = (mode == selectedMode),
                                onClick = null
                            )
                            Text(text = mode.name, modifier = Modifier.padding(start = 2.dp).align(Alignment.CenterVertically))
                        }
                    }
                }
            }
        }

        // --- Result and Timing UI ---
        if (isProcessing) {
            CircularProgressIndicator()
            Text("Processing...")
        } else {
            Column(modifier = Modifier.weight(if (imageUri == null) 1f else 0.5f).fillMaxWidth()) {
                 Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recognition Result:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (isActualResult) {
                        Button(onClick = {
                            clipboardManager.setText(AnnotatedString(recognizedText))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Copy")
                        }
                    }
                }

                // Timing Information
                totalTime?.let {
                    val color = Color.Gray
                    val size = 12.sp
                    Text("Total Time (Client Perspective): $it ms", fontSize = size, color = color, fontWeight = FontWeight.Bold)
                    when (selectedMode) {
                        OcrMode.LOCAL -> {
                            preprocessingTime?.let { t -> Text("  - Local Preprocessing: $t ms", fontSize = size, color = color) }
                            inferenceTime?.let { t -> Text("  - Local Inference: $t ms", fontSize = size, color = color) }
                        }
                        OcrMode.REMOTE_FULL -> {
                            preprocessingTime?.let { t -> Text("  - Local Pre-Scaling: $t ms", fontSize = size, color = color) }
                            networkTime?.let { t -> Text("  - Network & Server Time: $t ms", fontSize = size, color = color) }
                            inferenceTime?.let { t -> Text("    - Server Inference: $t ms", fontSize = size, color = color) }
                        }
                        OcrMode.REMOTE_HYBRID -> {
                            preprocessingTime?.let { t -> Text("  - Local Preprocessing: $t ms", fontSize = size, color = color) }
                            networkTime?.let { t -> Text("  - Network & Server Time: $t ms", fontSize = size, color = color) }
                            inferenceTime?.let { t -> Text("    - Server Inference: $t ms", fontSize = size, color = color) }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier.fillMaxSize().border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                ) {
                    SelectionContainer(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = recognizedText,
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// --- Utility Functions ---

@Throws(IOException::class)
fun uriToBytes(context: Context, uri: Uri): ByteArray {
    val inputStream = context.contentResolver.openInputStream(uri) ?: throw IOException("Unable to open InputStream for $uri")
    return inputStream.use { it.readBytes() }
}

@Throws(IOException::class)
fun bitmapToBytes(bitmap: Bitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    // Use JPEG for smaller size over network, PNG for lossless quality.
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
    return stream.toByteArray()
}

@Throws(IOException::class)
private fun getTempUri(context: Context): Uri {
    val tempFile = File.createTempFile("picture", ".jpg", context.cacheDir).apply { createNewFile() }
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
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
        while (halfHeight / inSampleSize >= maxDimension || halfWidth / inSampleSize >= maxDimension) {
            inSampleSize *= 2
        }
    }

    val downsampledOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    val downsampledBitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, downsampledOptions) } ?: throw IOException("Failed to decode downsampled bitmap from URI: $uri")

    return downsampledBitmap.copy(Bitmap.Config.ARGB_8888, true)
}
