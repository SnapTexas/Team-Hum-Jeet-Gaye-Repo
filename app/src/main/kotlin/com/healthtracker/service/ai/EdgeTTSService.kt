package com.healthtracker.service.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Edge TTS Service - Microsoft's Free Text-to-Speech with Android TTS fallback
 * 
 * Features:
 * - Natural sounding voices (better than Android TTS)
 * - Multiple language support (English + Hindi)
 * - Free to use (no API key needed)
 * - Fast response time
 * - High quality audio
 * - Automatic fallback to Android TTS if Edge fails
 */
@Singleton
class EdgeTTSService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "EdgeTTSService"
        
        // Edge TTS endpoint
        private const val TTS_URL = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        
        // Voice options (best quality)
        private const val VOICE_EN_US_FEMALE = "en-US-AriaNeural"  // Natural female voice
        private const val VOICE_EN_US_MALE = "en-US-GuyNeural"     // Natural male voice
        private const val VOICE_EN_IN_MALE = "en-IN-PrabhatNeural" // Indian English male
        private const val VOICE_HI_IN_FEMALE = "hi-IN-SwaraNeural" // Hindi female
        private const val VOICE_HI_IN_MALE = "hi-IN-MadhurNeural"  // Hindi male
        
        // Default voice - Indian male accent (Prabhat)
        private const val DEFAULT_VOICE = VOICE_EN_IN_MALE
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private var mediaPlayer: MediaPlayer? = null
    private var androidTTS: TextToSpeech? = null
    private var ttsReady = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        // Initialize Android TTS as fallback with Indian male accent
        androidTTS = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Try to set Indian English locale (male voice)
                val indianLocale = Locale("en", "IN")
                val result = androidTTS?.setLanguage(indianLocale)
                
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Timber.w("$TAG: Indian English not supported, using US English")
                    androidTTS?.language = Locale.US
                } else {
                    Timber.d("$TAG: Indian English male accent set successfully")
                }
                
                // Set voice parameters for natural male voice
                androidTTS?.setSpeechRate(1.0f)  // Normal speed
                androidTTS?.setPitch(0.9f)       // Slightly lower pitch for male voice
                ttsReady = true
                Timber.d("$TAG: Android TTS initialized as fallback (Indian male)")
            }
        }
    }
    
    /**
     * Speak text using Edge TTS (with Android TTS fallback)
     */
    suspend fun speak(
        text: String,
        voice: String = DEFAULT_VOICE,
        rate: String = "+0%",  // Speed: -50% to +100%
        pitch: String = "+0Hz"  // Pitch adjustment
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("$TAG: Speaking: $text")
            
            // Stop any current playback
            stopSpeaking()
            
            // Try Edge TTS first
            val edgeResult = tryEdgeTTS(text, voice, rate, pitch)
            
            if (edgeResult.isSuccess) {
                return@withContext Result.success(Unit)
            }
            
            // Fallback to Android TTS
            Timber.w("$TAG: Edge TTS failed, using Android TTS fallback")
            return@withContext useAndroidTTS(text)
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to speak")
            // Last resort - try Android TTS
            return@withContext useAndroidTTS(text)
        }
    }
    
    /**
     * Try Edge TTS
     */
    private suspend fun tryEdgeTTS(
        text: String,
        voice: String,
        rate: String,
        pitch: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("$TAG: Attempting Edge TTS for: $text")
            
            // Generate SSML
            val ssml = generateSSML(text, voice, rate, pitch)
            
            // Request audio from Edge TTS
            val audioData = requestTTS(ssml)
            
            if (audioData == null) {
                Timber.w("$TAG: Edge TTS returned null audio data")
                return@withContext Result.failure(Exception("Failed to get audio data from Edge TTS"))
            }
            
            Timber.d("$TAG: Edge TTS audio received: ${audioData.size} bytes")
            
            // Save to temp file
            val audioFile = saveTempAudio(audioData)
            
            // Play audio
            playAudio(audioFile)
            
            Timber.d("$TAG: Edge TTS playback started successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Edge TTS failed")
            Result.failure(e)
        }
    }
    
    /**
     * Use Android TTS as fallback
     */
    private suspend fun useAndroidTTS(text: String): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            if (ttsReady && androidTTS != null) {
                androidTTS?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
                Timber.d("$TAG: Using Android TTS fallback")
                Result.success(Unit)
            } else {
                Timber.e("$TAG: Android TTS not ready")
                Result.failure(Exception("TTS not available"))
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Android TTS failed")
            Result.failure(e)
        }
    }
    
    /**
     * Generate SSML for Edge TTS
     */
    private fun generateSSML(
        text: String,
        voice: String,
        rate: String,
        pitch: String
    ): String {
        return """
            <speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>
                <voice name='$voice'>
                    <prosody rate='$rate' pitch='$pitch'>
                        $text
                    </prosody>
                </voice>
            </speak>
        """.trimIndent()
    }
    
    /**
     * Request TTS audio from Edge
     */
    private suspend fun requestTTS(ssml: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            Timber.d("$TAG: Requesting TTS from Edge API...")
            
            val requestBody = ssml.toRequestBody("application/ssml+xml".toMediaType())
            
            val request = Request.Builder()
                .url("$TTS_URL?trustedclienttoken=6A5AA1D4EAFF4E9FB37E23D68491D6F4&Sec-MS-GEC=0")
                .addHeader("Content-Type", "application/ssml+xml")
                .addHeader("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0")
                .addHeader("Origin", "https://www.bing.com")
                .addHeader("Referer", "https://www.bing.com/")
                .post(requestBody)
                .build()
            
            Timber.d("$TAG: Sending request to: ${request.url}")
            
            val response = client.newCall(request).execute()
            
            Timber.d("$TAG: Edge TTS response code: ${response.code}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Timber.e("$TAG: Edge TTS request failed: ${response.code} - $errorBody")
                return@withContext null
            }
            
            val audioBytes = response.body?.bytes()
            Timber.d("$TAG: Received ${audioBytes?.size ?: 0} bytes from Edge TTS")
            
            audioBytes
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to request TTS from Edge")
            null
        }
    }
    
    /**
     * Save audio to temp file
     */
    private fun saveTempAudio(audioData: ByteArray): File {
        val tempFile = File(context.cacheDir, "tts_${UUID.randomUUID()}.mp3")
        tempFile.writeBytes(audioData)
        return tempFile
    }
    
    /**
     * Play audio file
     */
    private suspend fun playAudio(audioFile: File) = withContext(Dispatchers.Main) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                
                setDataSource(audioFile.absolutePath)
                
                setOnCompletionListener {
                    // Clean up after playback
                    release()
                    mediaPlayer = null
                    audioFile.delete()
                }
                
                setOnErrorListener { _, what, extra ->
                    Timber.e("$TAG: MediaPlayer error: what=$what, extra=$extra")
                    release()
                    mediaPlayer = null
                    audioFile.delete()
                    true
                }
                
                prepare()
                start()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to play audio")
            audioFile.delete()
        }
    }
    
    /**
     * Stop current speech
     */
    fun stopSpeaking() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
                mediaPlayer = null
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to stop speaking")
        }
    }
    
    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
    
    /**
     * Speak with fast rate (for quick responses)
     */
    suspend fun speakFast(text: String): Result<Unit> {
        return speak(text, rate = "+20%")
    }
    
    /**
     * Speak with slow rate (for important info)
     */
    suspend fun speakSlow(text: String): Result<Unit> {
        return speak(text, rate = "-10%")
    }
    
    /**
     * Speak in Hindi
     */
    suspend fun speakHindi(text: String): Result<Unit> {
        return speak(text, voice = VOICE_HI_IN_FEMALE)
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopSpeaking()
        androidTTS?.shutdown()
        androidTTS = null
        scope.cancel()
    }
}
