package fh3355.ocr_mobile

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class OcrProcessor {

    private var tessBaseApi: TessBaseAPI? = null

    // Returns null on success, or an error message string on failure.
    fun initTesseract(context: Context, lang: String): String? {
        if (tessBaseApi != null) return null // Already initialized

        try {
            val dataPath = File(context.filesDir, "tesseract").absolutePath
            val tessdataDir = File(dataPath, "tessdata")
            if (!tessdataDir.exists()) {
                if (!tessdataDir.mkdirs()) {
                    val errorMsg = "Failed to create directory: ${tessdataDir.absolutePath}"
                    Log.e("OcrProcessor", errorMsg)
                    return errorMsg
                }
            }

            val trainedDataPath = File(tessdataDir, "$lang.traineddata")
            if (!trainedDataPath.exists()) {
                try {
                    context.assets.open("$lang.traineddata").use { inputStream ->
                        FileOutputStream(trainedDataPath).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                } catch (e: IOException) {
                    val errorMsg = "Error copying '$lang.traineddata' from assets"
                    Log.e("OcrProcessor", errorMsg, e)
                    return "$errorMsg: ${e.message}"
                }
            }

            val api = TessBaseAPI()
            // The init method is the most likely point of failure for native reasons.
            if (api.init(dataPath, lang)) {
                tessBaseApi = api
                return null // Success
            } else {
                api.end()
                val errorMsg = "TessBaseAPI.init() failed. Check logcat for native errors. Possible reasons: incorrect data path, missing/corrupt traineddata, or incompatible native library for this device architecture."
                Log.e("OcrProcessor", errorMsg)
                return errorMsg
            }
        } catch (e: Exception) {
            val errorMsg = "Unexpected error during Tesseract initialization"
            Log.e("OcrProcessor", errorMsg, e)
            return "$errorMsg: ${e.message}"
        }
    }

    // Returns a pair of the recognized text and the processing time in milliseconds.
    fun processImage(bitmap: Bitmap): Pair<String, Long> {
        val api = tessBaseApi ?: return Pair("Error: Tesseract not initialized.", 0L)

        val startTime = System.currentTimeMillis()

        api.setImage(bitmap)
        val recognizedText = api.utF8Text
        api.clear()

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        return Pair(recognizedText, duration)
    }

    fun release() {
        tessBaseApi?.end()
        tessBaseApi = null
    }
}
