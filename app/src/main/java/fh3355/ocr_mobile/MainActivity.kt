package fh3355.ocr_mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    val initialText = "Select an image to start."
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var recognizedText by remember { mutableStateOf(initialText) }
    var initializationError by remember { mutableStateOf<String?>(null) }
    var isInitializing by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var isActualResult by remember { mutableStateOf(false) }
    var processingTime by remember { mutableStateOf<Long?>(null) }

    // Initialize Tesseract in a side effect
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val error = ocrProcessor.initTesseract(context, "eng")
            initializationError = error
            isInitializing = false
            if (error != null) {
                recognizedText = "Initialization Failed: $error"
                isActualResult = false
            }
        }
    }

    val ocrLauncher = { uri: Uri ->
        coroutineScope.launch(Dispatchers.Default) {
            isProcessing = true
            isActualResult = false
            processingTime = null
            try {
                val bitmap = uriToBitmap(context, uri)
                val (text, duration) = ocrProcessor.processImage(bitmap)
                recognizedText = text.ifBlank { "No text found." }
                processingTime = duration
            } catch (e: Exception) {
                e.printStackTrace()
                recognizedText = "Error processing image."
            }
            isProcessing = false
            isActualResult = true
        }
    }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let {
                imageUri = it
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
                recognizedText = "Image selected. Crop the image or start recognition."
                isActualResult = false
                processingTime = null
            }
        }
    )

    val tempImageUri = remember { getTempUri(context) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success: Boolean ->
            if (success) {
                tempImageUri?.let {
                    imageUri = it
                    recognizedText = "Image captured. Crop the image or start recognition."
                    isActualResult = false
                    processingTime = null
                }
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch(tempImageUri)
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ocrProcessor.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isInitializing) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator()
                Text("Initializing OCR Engine...")
            }
        } else if (initializationError != null) {
            Text(text = "Initialization Failed: $initializationError")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Text("从图库选择")
                }
                Button(onClick = {
                    val permission = Manifest.permission.CAMERA
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        cameraLauncher.launch(tempImageUri)
                    } else {
                        permissionLauncher.launch(permission)
                    }
                }) {
                    Text("用相机拍摄")
                }
            }
        }

        imageUri?.let {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Text("OCR识别图片:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    painter = rememberAsyncImagePainter(it),
                    contentDescription = "Selected Image",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Button(onClick = {
                        val cropOptions = CropImageContractOptions(it, CropImageOptions())
                        cropLauncher.launch(cropOptions)
                    }) {
                        Text("选择识别区域")
                    }
                    Button(onClick = { ocrLauncher(it) }) {
                        Text("开始识别")
                    }
                }
            }
        }

        if (isProcessing) {
            Column(
                modifier = Modifier.weight(if (imageUri == null) 1f else 0.5f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Processing Image...")
            }
        } else {
            Column(modifier = Modifier.weight(if (imageUri == null) 1f else 0.5f).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("OCR识别结果:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (isActualResult) {
                        Button(onClick = {
                            clipboardManager.setText(AnnotatedString(recognizedText))
                            Toast.makeText(context, "已复制到剪切板", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("复制")
                        }
                    }
                }
                processingTime?.let {
                    Text("识别用时: $it ms", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                ) {
                    SelectionContainer(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = recognizedText,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

private fun getTempUri(context: Context): Uri {
    val tempFile = File.createTempFile("picture", ".jpg", context.cacheDir).apply {
        createNewFile()
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        tempFile
    )
}

private fun uriToBitmap(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }.copy(Bitmap.Config.ARGB_8888, true)
}
