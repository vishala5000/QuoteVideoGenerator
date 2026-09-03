package com.example.videogenerator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.videogenerator.utils.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    
    private lateinit var quotesInput: EditText
    private lateinit var generateButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    
    private lateinit var videoGenerator: VideoGenerator
    private lateinit var audioProcessor: AudioProcessor
    private lateinit var zipHelper: ZipHelper
    private lateinit var permissionHelper: PermissionHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        initializeHelpers()
        setupListeners()
    }
    
    private fun initializeViews() {
        quotesInput = findViewById(R.id.quotesInput)
        generateButton = findViewById(R.id.generateButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
    }
    
    private fun initializeHelpers() {
        videoGenerator = VideoGenerator(this)
        audioProcessor = AudioProcessor(this)
        zipHelper = ZipHelper(this)
        permissionHelper = PermissionHelper(this)
    }
    
    private fun setupListeners() {
        generateButton.setOnClickListener {
            if (permissionHelper.hasAllPermissions()) {
                generateVideos()
            } else {
                permissionHelper.requestPermissions()
            }
        }
    }
    
    private fun generateVideos() {
        val quotes = quotesInput.text.toString()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        if (quotes.isEmpty()) {
            Toast.makeText(this, "Please enter at least one quote", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (quotes.size > 10) {
            Toast.makeText(this, "Maximum 10 quotes allowed", Toast.LENGTH_SHORT).show()
            return
        }
        
        setUIState(false)
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outputDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "QuoteVideos"
                )
                outputDir.mkdirs()
                
                updateStatus("Generating videos...")
                
                // Generate videos
                val videoFiles = videoGenerator.generateVideosForQuotes(quotes, outputDir)
                
                if (videoFiles.isNotEmpty()) {
                    updateStatus("Adding audio...")
                    
                    // Add background music to videos
                    val audioFiles = audioProcessor.addBackgroundMusic(videoFiles)
                    
                    updateStatus("Creating ZIP file...")
                    
                    // Create ZIP
                    val zipFile = zipHelper.createZipFile(audioFiles, outputDir)
                    
                    withContext(Dispatchers.Main) {
                        setUIState(true)
                        showSuccessDialog(zipFile)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        setUIState(true)
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to generate videos",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setUIState(true)
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private suspend fun updateStatus(message: String) {
        withContext(Dispatchers.Main) {
            statusText.text = message
            statusText.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun setUIState(isEnabled: Boolean) {
        generateButton.isEnabled = isEnabled
        progressBar.visibility = if (isEnabled) android.view.View.GONE else android.view.View.VISIBLE
        quotesInput.isEnabled = isEnabled
        statusText.visibility = if (isEnabled) android.view.View.GONE else android.view.View.VISIBLE
    }
    
    private fun showSuccessDialog(zipFile: File) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Success!")
        builder.setMessage("Videos generated successfully!\n\n" +
                          "File: ${zipFile.name}\n" +
                          "Size: ${formatFileSize(zipFile.length())}\n\n" +
                          "Location: ${zipFile.parent}")
        
        builder.setPositiveButton("Open") { _, _ ->
            openFile(zipFile)
        }
        
        builder.setNegativeButton("Close") { _, _ -> }
        
        builder.setNeutralButton("Share") { _, _ ->
            shareFile(zipFile)
        }
        
        builder.show()
    }
    
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
    
    private fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/zip")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        startActivity(Intent.createChooser(intent, "Open ZIP"))
    }
    
    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        startActivity(Intent.createChooser(intent, "Share ZIP"))
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHelper.handlePermissionResult(requestCode, grantResults)
    }
}
