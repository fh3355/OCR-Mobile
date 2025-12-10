package fh3355.ocr_mobile

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File

class OcrProcessor {

    private val predictor = OCRPredictor()
    private var isInitialized = false

    /**
     * Initializes the PaddleOCR engine.
     * Copies models from assets to internal storage and then initializes the native predictor.
     * Returns null on success, or an error message string on failure.
     */
    fun init(context: Context): String? {
        if (isInitialized) return null // Already initialized

        // 1. Define paths
        val modelDirName = "models"
        val labelDirName = "labels"
        val labelFileName = "ppocr_keys_v1.txt"
        val configFileName = "config.txt"

        val appInternalPath = context.filesDir.absolutePath
        val modelRootPath = "$appInternalPath/paddle_ocr_models"

        // 2. Copy assets to internal storage if not already present
        try {
            val modelRootDir = File(modelRootPath)
            if (!modelRootDir.exists()) {
                modelRootDir.mkdirs()
                Utils.copyDirectoryFromAssets(context, modelDirName, "$modelRootPath/$modelDirName")
                Utils.copyFileFromAssets(context, "$labelDirName/$labelFileName", "$modelRootPath/$labelFileName")
                Utils.copyFileFromAssets(context, configFileName, "$modelRootPath/$configFileName")
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to copy assets: ${e.message}"
            Log.e("OcrProcessor", errorMsg, e)
            return errorMsg
        }

        // 3. Define file paths for native layer
        val detModelPath = "$modelRootPath/$modelDirName/ch_ppocr_mobile_v2.0_det_slim_opt.nb"
        val clsModelPath = "$modelRootPath/$modelDirName/ch_ppocr_mobile_v2.0_cls_slim_opt.nb"
        val recModelPath = "$modelRootPath/$modelDirName/ch_ppocr_mobile_v2.0_rec_slim_opt.nb"
        val fullConfigPath = "$modelRootPath/$configFileName"
        val fullLabelPath = "$modelRootPath/$labelFileName"

        // 4. Verify file existence before initializing
        val pathsToVerify = listOf(detModelPath, clsModelPath, recModelPath, fullConfigPath, fullLabelPath)
        for (path in pathsToVerify) {
            if (!File(path).exists()) {
                val errorMsg = "One or more model/config files are missing after copy. Please clear app data and restart."
                Log.e("OcrProcessor", errorMsg)
                return errorMsg
            }
        }

        // 5. Initialize the native predictor with verified paths
        val cpuThreadNum = 4
        val cpuPowerMode = "LITE_POWER_HIGH"

        val success = predictor.init(detModelPath, clsModelPath, recModelPath, fullConfigPath, fullLabelPath, cpuThreadNum, cpuPowerMode)
        if (!success) {
            val errorMsg = "PaddleOCR nativeInit failed. Check logcat for native errors."
            Log.e("OcrProcessor", errorMsg)
            return errorMsg
        }

        isInitialized = true
        return null // Success
    }

    /**
     * Processes a single bitmap image to extract text.
     * Returns a pair of the recognized text and the processing time in milliseconds.
     */
    fun processImage(bitmap: Bitmap): Pair<String, Long> {
        if (!isInitialized) {
            return Pair("Error: PaddleOCR not initialized.", 0L)
        }

        val startTime = System.currentTimeMillis()
        val result = predictor.process(bitmap)
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        return Pair(result, duration)
    }

    /**
     * Releases the native predictor and cleans up resources.
     */
    fun release() {
        if (isInitialized) {
            predictor.release()
            isInitialized = false
        }
    }
}