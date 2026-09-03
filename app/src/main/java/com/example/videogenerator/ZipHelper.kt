package com.example.videogenerator

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "ZipHelper"
    }
    
    suspend fun createZipFile(files: List<File>, outputDir: File): File =
        withContext(Dispatchers.IO) {
            val zipFile = File(outputDir, "quote_videos_${System.currentTimeMillis()}.zip")
            
            try {
                FileOutputStream(zipFile).use { fos ->
                    ZipOutputStream(fos).use { zos ->
                        files.forEach { file ->
                            if (file.exists()) {
                                addFileToZip(file, zos)
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Created ZIP file: ${zipFile.absolutePath} (${zipFile.length()} bytes)")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create ZIP file", e)
                throw e
            }
            
            zipFile
        }
    
    private fun addFileToZip(file: File, zos: ZipOutputStream) {
        val entry = ZipEntry(file.name)
        zos.putNextEntry(entry)
        
        file.inputStream().use { input ->
            input.copyTo(zos)
        }
        
        zos.closeEntry()
    }
}
