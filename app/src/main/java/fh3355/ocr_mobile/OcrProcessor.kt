package fh3355.ocr_mobile

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class OcrProcessor {

    private val tessBaseApi: TessBaseAPI = TessBaseAPI()

    fun initTesseract(context: Context, lang: String) {
        val dataPath = File(context.filesDir, "tesseract").absolutePath
        val tessdataDir = File(dataPath, "tessdata")
        if (!tessdataDir.exists()) {
            tessdataDir.mkdirs()
        }

        val trainedDataPath = File(tessdataDir, "$lang.traineddata")
        if (!trainedDataPath.exists()) {
            try {
                context.assets.open("tessdata/$lang.traineddata").use { inputStream ->
                    FileOutputStream(trainedDataPath).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        tessBaseApi.init(dataPath, lang)
    }

    fun processImage(bitmap: Bitmap): String {
        tessBaseApi.setImage(bitmap)
        val recognizedText = tessBaseApi.utF8Text
        tessBaseApi.clear()
        return recognizedText
    }

    fun release() {
        tessBaseApi.end()
    }
}
