package fh3355.ocr_mobile

import android.graphics.Bitmap

class OCRPredictor {

    private var nativePointer: Long = 0

    fun init(detModelPath: String, clsModelPath: String, recModelPath: String, configPath: String, labelPath: String, cpuThreadNum: Int, cpuPowerMode: String): Boolean {
        nativePointer = nativeInit(detModelPath, clsModelPath, recModelPath, configPath, labelPath, cpuThreadNum, cpuPowerMode)
        return nativePointer != 0L
    }

    fun process(bitmap: Bitmap): String {
        if (nativePointer == 0L) {
            return ""
        }
        val result = nativeProcess(nativePointer, bitmap)
        return result
    }

    fun release() {
        if (nativePointer != 0L) {
            nativeRelease(nativePointer)
            nativePointer = 0
        }
    }

    private external fun nativeInit(detModelPath: String, clsModelPath: String, recModelPath: String, configPath: String, labelPath: String, cpuThreadNum: Int, cpuPowerMode: String): Long
    private external fun nativeProcess(nativePointer: Long, bitmap: Bitmap): String
    private external fun nativeRelease(nativePointer: Long): Boolean

    companion object {
        init {
            try {
                // Load the core libraries in the correct order
                System.loadLibrary("opencv_java4")
                System.loadLibrary("paddle_light_api_shared")
                // Load our own JNI library
                System.loadLibrary("Native")
            } catch (e: UnsatisfiedLinkError) {
                System.err.println("Failed to load native libraries: " + e.message)
            }
        }
    }
}