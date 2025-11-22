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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
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

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var recognizedText by remember { mutableStateOf("Text will be displayed here.") }

    // Initialize Tesseract in a side effect
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            ocrProcessor.initTesseract(context, "eng")
        }
    }

    // Release Tesseract when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            ocrProcessor.release()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            imageUri = uri
            uri?.let {
                coroutineScope.launch(Dispatchers.Default) {
                    try {
                        val bitmap = uriToBitmap(context, it)
                        val text = ocrProcessor.processImage(bitmap)
                        recognizedText = text.ifBlank { "No text found." }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        recognizedText = "Error processing image."
                    }
                }
            }
        }
    )

    val tempImageUri = remember { getTempUri(context) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success: Boolean ->
            if (success) {
                imageUri = tempImageUri
                imageUri?.let {
                     coroutineScope.launch(Dispatchers.Default) {
                        try {
                            val bitmap = uriToBitmap(context, it)
                            val text = ocrProcessor.processImage(bitmap)
                            recognizedText = text.ifBlank { "No text found." }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            recognizedText = "Error processing image."
                        }
                    }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                Text("From Gallery")
            }
            Button(onClick = {
                val permission = Manifest.permission.CAMERA
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    cameraLauncher.launch(tempImageUri)
                } else {
                    permissionLauncher.launch(permission)
                }
            }) {
                Text("From Camera")
            }
        }

        imageUri?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = "Selected Image",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        }

        Text(text = recognizedText, modifier = Modifier.weight(1f))
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

// Helper function to convert Uri to Bitmap
private fun uriToBitmap(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }.copy(Bitmap.Config.ARGB_8888, true)
}
