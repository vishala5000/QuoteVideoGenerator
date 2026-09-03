package com.example.videogenerator

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class AudioProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioProcessor"
    }
    
    suspend fun addBackgroundMusic(videoFiles: List<File>): List<File> =
        withContext(Dispatchers.IO) {
            val outputFiles = mutableListOf<File>()
            
            videoFiles.forEach { videoFile ->
                try {
                    val outputFile = File(
                        videoFile.parent,
                        videoFile.nameWithoutExtension + "_with_audio.mp4"
                    )
                    
                    // Copy audio from bg.mp3 and add to video
                    addAudioToVideo(videoFile, outputFile)
                    outputFiles.add(outputFile)
                    
                    // Delete original video file
                    videoFile.delete()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add audio to ${videoFile.name}", e)
                    outputFiles.add(videoFile) // Keep original
                }
            }
            
            outputFiles
        }
    
    private fun addAudioToVideo(videoFile: File, outputFile: File) {
        val videoExtractor = MediaExtractor().apply {
            setDataSource(videoFile.absolutePath)
        }
        
        val audioExtractor = MediaExtractor().apply {
            try {
                // Try to load bg.mp3 from assets
                val bgFile = File(context.filesDir, "bg.mp3")
                if (bgFile.exists()) {
                    setDataSource(bgFile.absolutePath)
                } else {
                    // Copy from assets
                    context.assets.open("bg.mp3").use { input ->
                        FileOutputStream(bgFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    setDataSource(bgFile.absolutePath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load background music", e)
                // Continue without audio
                return
            }
        }
        
        // Setup muxer
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false
        
        // Select video track
        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                videoExtractor.selectTrack(i)
                videoTrackIndex = muxer.addTrack(format)
                break
            }
        }
        
        // Select audio track from bg.mp3
        for (i in 0 until audioExtractor.trackCount) {
            val format = audioExtractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioExtractor.selectTrack(i)
                audioTrackIndex = muxer.addTrack(format)
                break
            }
        }
        
        // Start muxer
        if (videoTrackIndex >= 0 && audioTrackIndex >= 0) {
            muxer.start()
            muxerStarted = true
            
            // Copy video frames
            copyFrames(videoExtractor, muxer, videoTrackIndex, muxer)
            
            // Copy audio frames
            copyFrames(audioExtractor, muxer, audioTrackIndex, muxer)
            
            // Clean up
            muxer.stop()
            muxer.release()
        }
        
        videoExtractor.release()
        audioExtractor.release()
    }
    
    private fun copyFrames(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        trackIndex: Int,
        mediaMuxer: MediaMuxer
    ) {
        val bufferInfo = MediaMuxer.OutputFormat().also { 
            // This is a workaround - use BufferInfo class
        }
        
        val buffer = ByteBuffer.allocate(1024 * 1024) // 1MB buffer
        val bufferInfo2 = android.media.MediaCodec.BufferInfo()
        
        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            
            bufferInfo2.offset = 0
            bufferInfo2.size = sampleSize
            bufferInfo2.presentationTimeUs = extractor.sampleTime
            bufferInfo2.flags = extractor.sampleFlags
            
            muxer.writeSampleData(trackIndex, buffer, bufferInfo2)
            extractor.advance()
        }
    }
}
