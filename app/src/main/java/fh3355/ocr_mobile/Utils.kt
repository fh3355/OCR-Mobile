package fh3355.ocr_mobile

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object Utils {

    fun copyFileFromAssets(context: Context, sourceName: String, destPath: String) {
        val assetManager = context.assets
        try {
            assetManager.open(sourceName).use { inputStream ->
                FileOutputStream(destPath).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun copyDirectoryFromAssets(context: Context, sourceDir: String, destDir: String) {
        val assetManager = context.assets
        val destDirFile = File(destDir)
        if (!destDirFile.exists()) {
            destDirFile.mkdirs()
        }

        try {
            val files = assetManager.list(sourceDir)
            if (files != null) {
                for (filename in files) {
                    val sourceSubPath = if (sourceDir.isEmpty()) filename else "$sourceDir/$filename"
                    val destSubPath = "$destDir/$filename"
                    
                    // Check if it's a directory or a file
                    var isDirectory = false
                    try {
                        // This is a bit of a hack. assetManager.list() doesn't distinguish files from directories.
                        // If listing the subpath is successful and returns a non-empty array, it's a directory.
                        val subFiles = assetManager.list(sourceSubPath)
                        if (subFiles != null && subFiles.isNotEmpty()) {
                            isDirectory = true
                        }
                    } catch (e: IOException) {
                        // If an IOException occurs, it's likely a file.
                        isDirectory = false
                    }

                    if (isDirectory) {
                        copyDirectoryFromAssets(context, sourceSubPath, destSubPath)
                    } else {
                        copyFileFromAssets(context, sourceSubPath, destSubPath)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
