package com.example.videogenerator

import android.content.Context
import android.graphics.*
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class VideoGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoGenerator"
        private const val VIDEO_WIDTH = 1080
        private const val VIDEO_HEIGHT = 1920
        private const val TEXT_WRAP_WIDTH = 680
        private const val TEXT_WRAP_HEIGHT = 1320
        private const val VIDEO_DURATION_MS = 5000 // 5 seconds
        private const val FRAME_RATE = 30
        private const val BIT_RATE = 4_000_000
        private const val I_FRAME_INTERVAL = 1
    }
    
    private val videoCodec: String = MediaFormat.MIMETYPE_VIDEO_AVC
    
    suspend fun generateVideosForQuotes(quotes: List<String>, outputDir: File): List<File> =
        withContext(Dispatchers.IO) {
            val videoFiles = mutableListOf<File>()
            
            quotes.forEachIndexed { index, quote ->
                try {
                    val fileName = "quote_${String.format("%02d", index + 1)}.mp4"
                    val outputFile = File(outputDir, fileName)
                    
                    generateVideoForQuote(quote, outputFile)
                    videoFiles.add(outputFile)
                    
                    Log.d(TAG, "Generated video ${index + 1}/${quotes.size}: ${outputFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to generate video for quote: $quote", e)
                }
            }
            
            videoFiles
        }
    
    private fun generateVideoForQuote(quote: String, outputFile: File) {
        val totalFrames = (VIDEO_DURATION_MS / 1000.0 * FRAME_RATE).toInt()
        
        // Setup media format
        val format = MediaFormat.createVideoFormat(videoCodec, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }
        
        // Initialize codec and muxer
        val codec = MediaCodec.createEncoderByType(videoCodec)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var muxerStarted = false
        
        // Load font
        val typeface = try {
            Typeface.createFromAsset(context.assets, "font.ttf")
        } catch (e: Exception) {
            Log.w(TAG, "Font not found, using default", e)
            Typeface.DEFAULT
        }
        
        // Generate frames
        var frameIndex = 0
        while (frameIndex < totalFrames) {
            val inputBufferIndex = codec.dequeueInputBuffer(10000)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.let { buffer ->
                    // Create frame bitmap
                    val bitmap = generateFrame(quote, typeface, frameIndex, totalFrames)
                    
                    // Convert to YUV
                    val yuvData = bitmapToYuv(bitmap)
                    buffer.put(yuvData)
                    
                    // Queue frame
                    val presentationTimeUs = (frameIndex * 1_000_000L / FRAME_RATE)
                    codec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        yuvData.size,
                        presentationTimeUs,
                        0
                    )
                    
                    bitmap.recycle()
                    frameIndex++
                }
            }
            
            // Process output
            processOutput(codec, muxer, videoTrackIndex, muxerStarted)
        }
        
        // Signal end of stream and finish
        codec.signalEndOfInputStream()
        processOutput(codec, muxer, videoTrackIndex, muxerStarted, true)
        
        // Cleanup
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
    }
    
    private fun generateFrame(quote: String, typeface: Typeface, frameIndex: Int, totalFrames: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Green background
        canvas.drawColor(Color.rgb(0, 128, 0)) // Full green
        
        // Draw heading
        paint.apply {
            color = Color.YELLOW
            textSize = 60f
            typeface = this@generateFrame.typeface
            textAlign = Paint.Align.CENTER
        }
        
        val heading = "Quote of the Day"
        canvas.drawText(heading, (VIDEO_WIDTH / 2).toFloat(), 200f, paint)
        
        // Draw quote text
        paint.apply {
            color = Color.WHITE
            textSize = 48f
            typeface = this@generateFrame.typeface
            textAlign = Paint.Align.CENTER
        }
        
        // Text wrapping
        val lines = wrapText(quote, paint, TEXT_WRAP_WIDTH)
        
        // Draw lines with proper spacing
        val lineHeight = 80f
        val totalTextHeight = lines.size * lineHeight
        val startY = (VIDEO_HEIGHT - totalTextHeight) / 2f
        
        lines.forEachIndexed { index, line ->
            val y = startY + index * lineHeight + 50f
            canvas.drawText(line, (VIDEO_WIDTH / 2).toFloat(), y, paint)
        }
        
        return bitmap
    }
    
    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = StringBuilder()
        
        words.forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val bounds = Rect()
            paint.getTextBounds(testLine, 0, testLine.length, bounds)
            
            if (bounds.width() > maxWidth) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    // Word is too long, force split
                    lines.add(word)
                    currentLine = StringBuilder()
                }
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        
        return lines
    }
    
    private fun bitmapToYuv(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val yuvData = ByteArray(width * height * 3 / 2)
        val pixels = IntArray(width * height)
        
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var yIndex = 0
        var uvIndex = width * height
        
        for (i in 0 until height) {
            for (j in 0 until width) {
                val pixel = pixels[i * width + j]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // Y (luminance)
                val y = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                yuvData[yIndex++] = y.toByte()
                
                // UV (chrominance) - subsample 4:2:0
                if (i % 2 == 0 && j % 2 == 0) {
                    val u = ((-0.14713 * r - 0.28886 * g + 0.436 * b) + 128).toInt()
                    val v = ((0.615 * r - 0.51499 * g - 0.10001 * b) + 128).toInt()
                    yuvData[uvIndex++] = u.toByte()
                    yuvData[uvIndex++] = v.toByte()
                }
            }
        }
        
        return yuvData
    }
    
    private fun processOutput(
        codec: MediaCodec,
        muxer: MediaMuxer,
        trackIndex: Int,
        muxerStarted: Boolean,
        isEnd: Boolean = false
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, if (isEnd) 0 else 10000)
        
        while (outputBufferIndex >= 0) {
            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
            if (outputBuffer != null && bufferInfo.size > 0) {
                if (!muxerStarted) {
                    // Start muxer
                }
                
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                
                // Write to muxer (simplified)
            }
            
            codec.releaseOutputBuffer(outputBufferIndex, false)
            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
        }
    }
}
