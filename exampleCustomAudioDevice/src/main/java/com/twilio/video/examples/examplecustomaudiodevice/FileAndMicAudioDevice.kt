package com.twilio.video.examples.examplecustomaudiodevice

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import com.twilio.video.AudioDevice
import com.twilio.video.AudioDeviceContext
import com.twilio.video.AudioFormat
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import tvi.webrtc.ThreadUtils

class FileAndMicAudioDevice(private val context: Context) : AudioDevice {
    private var keepAliveRendererRunnable = true

    // Average number of callbacks per second.
    private val BUFFERS_PER_SECOND = 1000 / CALLBACK_BUFFER_SIZE_MS
    private lateinit var fileWriteByteBuffer: ByteBuffer
    private var writeBufferSize = 0
    private lateinit var inputStream: InputStream
    private lateinit var dataInputStream: DataInputStream
    private lateinit var audioRecord: AudioRecord
    private lateinit var micWriteBuffer: ByteBuffer
    private lateinit var readByteBuffer: ByteBuffer
    private lateinit var audioTrack: AudioTrack

    // Handlers and Threads
    private lateinit var capturerHandler: Handler
    private lateinit var capturerThread: HandlerThread
    private lateinit var rendererHandler: Handler
    private lateinit var rendererThread: HandlerThread
    private lateinit var renderingAudioDeviceContext: AudioDeviceContext
    private lateinit var capturingAudioDeviceContext: AudioDeviceContext

    // By default music capturer is enabled
    var isMusicPlaying = false
        private set

    /*
     * This Runnable reads a music file and provides the audio frames to the AudioDevice API via
     * AudioDevice.audioDeviceWriteCaptureData(..) until there is no more data to be read, the
     * capturer input switches to the microphone, or the call ends.
     */

    val fileCapturerRunnable = object : Runnable {
        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            var bytesRead = 0
            try {
                if (dataInputStream != null && dataInputStream!!.read(fileWriteByteBuffer!!.array(), 0, writeBufferSize).also { bytesRead = it } > -1) {
                    if (bytesRead == fileWriteByteBuffer!!.capacity()) {
                        AudioDevice.audioDeviceWriteCaptureData(capturingAudioDeviceContext!!, fileWriteByteBuffer!!)
                    } else {
                        processRemaining(fileWriteByteBuffer, fileWriteByteBuffer!!.capacity())
                        AudioDevice.audioDeviceWriteCaptureData(capturingAudioDeviceContext!!, fileWriteByteBuffer!!)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            capturerHandler?.postDelayed(this, CALLBACK_BUFFER_SIZE_MS.toLong())
        }
    }

    /*
     * This Runnable reads data from the microphone and provides the audio frames to the AudioDevice
     * API via AudioDevice.audioDeviceWriteCaptureData(..) until the capturer input switches to
     * microphone or the call ends.
     */
    private val microphoneCapturerRunnable = Runnable {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        if (audioRecord.state != AudioRecord.STATE_UNINITIALIZED) {
            audioRecord?.startRecording()
            while (true) {
                val bytesRead = audioRecord?.read(micWriteBuffer!!, micWriteBuffer!!.capacity())
                if (bytesRead == micWriteBuffer?.capacity()) {
                    AudioDevice.audioDeviceWriteCaptureData(capturingAudioDeviceContext!!, micWriteBuffer!!)
                } else {
                    val errorMessage = "AudioRecord.read failed: $bytesRead"
                    Log.e(TAG, errorMessage)
                    if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        stopRecording()
                        Log.e(TAG, errorMessage)
                    }
                    break
                }
            }
        }
    }

    /*
     * This Runnable reads audio data from the callee perspective via AudioDevice.audioDeviceReadRenderData(...)
     * and plays out the audio data using AudioTrack.write().
     */
    private val speakerRendererRunnable = label@ Runnable {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        try {
            audioTrack?.play()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "AudioTrack.play failed: " + e.message)
            releaseAudioResources()
        }
        while (keepAliveRendererRunnable) {
            // Get 10ms of PCM data from the SDK. Audio data is written into the ByteBuffer provided.
            AudioDevice.audioDeviceReadRenderData(renderingAudioDeviceContext!!, readByteBuffer!!)
            var bytesWritten = 0
            bytesWritten = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                writeOnLollipop(audioTrack, readByteBuffer, readByteBuffer!!.capacity())
            } else {
                writePreLollipop(audioTrack, readByteBuffer, readByteBuffer!!.capacity())
            }
            if (bytesWritten != readByteBuffer?.capacity()) {
                Log.e(TAG, "AudioTrack.write failed: $bytesWritten")
                if (bytesWritten == AudioTrack.ERROR_INVALID_OPERATION) {
                    keepAliveRendererRunnable = false
                    break
                }
            }
            // The byte buffer must be rewinded since byteBuffer.position() is increased at each
            // call to AudioTrack.write(). If we don't do this, will fail the next  AudioTrack.write().
            readByteBuffer?.rewind()
        }
    }

    /*
     * This method enables a capturer switch between a file and the microphone.
     * @param playMusic
     */
    fun switchInput(playMusic: Boolean) {
        isMusicPlaying = playMusic
        if (playMusic) {
            initializeStreams()
            capturerHandler?.removeCallbacks(microphoneCapturerRunnable)
            stopRecording()
            capturerHandler?.post(fileCapturerRunnable)
        } else {
            capturerHandler?.removeCallbacks(fileCapturerRunnable)
            capturerHandler?.post(microphoneCapturerRunnable)
        }
    }

    /*
     * Return the AudioFormat used the capturer. This custom device uses 44.1kHz sample rate and
     * STEREO channel configuration both for microphone and the music file.
     */
    override fun getCapturerFormat(): AudioFormat? {
        return AudioFormat(AudioFormat.AUDIO_SAMPLE_RATE_44100,
                AudioFormat.AUDIO_SAMPLE_STEREO)
    }

    /*
     * Init the capturer using the AudioFormat return by getCapturerFormat().
     */
    override fun onInitCapturer(): Boolean {
        val bytesPerFrame = 2 * (BITS_PER_SAMPLE / 8)
        val framesPerBuffer = capturerFormat!!.sampleRate / BUFFERS_PER_SECOND
        // Calculate the minimum buffer size required for the successful creation of
        // an AudioRecord object, in byte units.
        val channelConfig = channelCountToConfiguration(capturerFormat!!.channelCount)
        val minBufferSize = AudioRecord.getMinBufferSize(capturerFormat!!.sampleRate,
                channelConfig, android.media.AudioFormat.ENCODING_PCM_16BIT)
        micWriteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer)
        val tempMicWriteBuffer = micWriteBuffer
        val bufferSizeInBytes = Math.max(BUFFER_SIZE_FACTOR * minBufferSize, tempMicWriteBuffer!!.capacity())
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, capturerFormat!!.sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_STEREO, android.media.AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes)
        fileWriteByteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer)
        val testFileWriteByteBuffer = fileWriteByteBuffer
        writeBufferSize = testFileWriteByteBuffer!!.capacity()
        // Initialize the streams.
        initializeStreams()
        return true
    }

    override fun onStartCapturing(audioDeviceContext: AudioDeviceContext): Boolean {
        // Initialize the AudioDeviceContext
        capturingAudioDeviceContext = audioDeviceContext
        // Create the capturer thread and start
        capturerThread = HandlerThread("CapturerThread")
        capturerThread!!.start()
        // Create the capturer handler that processes the capturer Runnables.
        capturerHandler = Handler(capturerThread!!.looper)
        isMusicPlaying = true
        capturerHandler!!.post(fileCapturerRunnable)
        return true
    }

    override fun onStopCapturing(): Boolean {
        if (isMusicPlaying) {
            isMusicPlaying = false
            closeStreams()
        } else {
            stopRecording()
        }
        /*
         * When onStopCapturing is called, the AudioDevice API expects that at the completion
         * of the callback the capturer has completely stopped. As a result, quit the capturer
         * thread and explicitly wait for the thread to complete.
         */
        capturerThread?.quit()
        if (!ThreadUtils.joinUninterruptibly(capturerThread, THREAD_JOIN_TIMEOUT_MS)) {
            Log.e(TAG, "Join of capturerThread timed out")
            return false
        }
        return true
    }

    /*
     * Return the AudioFormat used the renderer. This custom device uses 44.1kHz sample rate and
     * STEREO channel configuration for audio track.
     */
    override fun getRendererFormat(): AudioFormat? {
        return AudioFormat(AudioFormat.AUDIO_SAMPLE_RATE_44100,
                AudioFormat.AUDIO_SAMPLE_STEREO)
    }

    override fun onInitRenderer(): Boolean {
        val bytesPerFrame = rendererFormat!!.channelCount * (BITS_PER_SAMPLE / 8)
        readByteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * (rendererFormat!!.sampleRate / BUFFERS_PER_SECOND))
        val channelConfig = channelCountToConfiguration(rendererFormat!!.channelCount)
        val minBufferSize = AudioRecord.getMinBufferSize(rendererFormat!!.sampleRate, channelConfig, android.media.AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack(AudioManager.STREAM_VOICE_CALL, rendererFormat!!.sampleRate, channelConfig,
                android.media.AudioFormat.ENCODING_PCM_16BIT, minBufferSize, AudioTrack.MODE_STREAM)
        keepAliveRendererRunnable = true
        return true
    }

    override fun onStartRendering(audioDeviceContext: AudioDeviceContext): Boolean {
        renderingAudioDeviceContext = audioDeviceContext
        // Create the renderer thread and start
        rendererThread = HandlerThread("RendererThread")
        rendererThread!!.start()
        // Create the capturer handler that processes the renderer Runnables.
        rendererHandler = Handler(rendererThread!!.looper)
        rendererHandler!!.post(speakerRendererRunnable)
        return true
    }

    override fun onStopRendering(): Boolean {
        stopAudioTrack()
        // Quit the rendererThread's looper to stop processing any further messages.
        rendererThread!!.quit()
        /*
         * When onStopRendering is called, the AudioDevice API expects that at the completion
         * of the callback the renderer has completely stopped. As a result, quit the renderer
         * thread and explicitly wait for the thread to complete.
         */
        if (!ThreadUtils.joinUninterruptibly(rendererThread, THREAD_JOIN_TIMEOUT_MS)) {
            Log.e(TAG, "Join of rendererThread timed out")
            return false
        }
        return true
    }

    // Capturer helper methods
    private fun initializeStreams() {
        inputStream = context.resources.openRawResource(context.resources.getIdentifier("music",
                "raw", context.packageName))
        dataInputStream = DataInputStream(inputStream)
        try {
            val bytes = dataInputStream!!.skipBytes(WAV_FILE_HEADER_SIZE)
            Log.d(TAG, "Number of bytes skipped : $bytes")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun closeStreams() {
        Log.d(TAG, "Remove any pending posts of fileCapturerRunnable that are in the message queue ")
        capturerHandler?.removeCallbacks(fileCapturerRunnable)
        try {
            dataInputStream?.close()
            inputStream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        Log.d(TAG, "Remove any pending posts of microphoneCapturerRunnable that are in the message queue ")
        capturerHandler!!.removeCallbacks(microphoneCapturerRunnable)
        try {
            audioRecord?.stop()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "AudioRecord.stop failed: " + e.message)
        }
    }

    private fun channelCountToConfiguration(channels: Int): Int {
        return if (channels == 1) android.media.AudioFormat.CHANNEL_IN_MONO else android.media.AudioFormat.CHANNEL_IN_STEREO
    }

    private fun processRemaining(bb: ByteBuffer?, chunkSize: Int) {
        bb!!.position(bb.limit()) // move at the end
        bb.limit(chunkSize) // get ready to pad with longs
        while (bb.position() < chunkSize) {
            bb.putLong(0)
        }
        bb.limit(chunkSize)
        bb.flip()
    }

    // Renderer helper methods
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun writeOnLollipop(audioTrack: AudioTrack?, byteBuffer: ByteBuffer?, sizeInBytes: Int): Int {
        return audioTrack!!.write(byteBuffer!!, sizeInBytes, AudioTrack.WRITE_BLOCKING)
    }

    private fun writePreLollipop(audioTrack: AudioTrack?, byteBuffer: ByteBuffer?, sizeInBytes: Int): Int {
        return audioTrack!!.write(byteBuffer!!.array(), byteBuffer.arrayOffset(), sizeInBytes)
    }

    fun stopAudioTrack() {
        keepAliveRendererRunnable = false
        Log.d(TAG, "Remove any pending posts of speakerRendererRunnable that are in the message queue ")
        rendererHandler?.removeCallbacks(speakerRendererRunnable)
        try {
            audioTrack?.stop()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "AudioTrack.stop failed: " + e.message)
        }
        releaseAudioResources()
    }

    private fun releaseAudioResources() {
        audioTrack?.apply {
            flush()
            release()
        }
    }

    companion object {
        private val TAG = FileAndMicAudioDevice::class.java.simpleName

        // TIMEOUT for rendererThread and capturerThread to wait for successful call to join()
        private const val THREAD_JOIN_TIMEOUT_MS: Long = 2000

        // We want to get as close to 10 msec buffers as possible because this is what the media engine prefers.
        private const val CALLBACK_BUFFER_SIZE_MS = 10

        // Default audio data format is PCM 16 bit per sample. Guaranteed to be supported by all devices.
        private const val BITS_PER_SAMPLE = 16

        // Ask for a buffer size of BUFFER_SIZE_FACTOR * (minimum required buffer size). The extra space
        // is allocated to guard against glitches under high load.
        private const val BUFFER_SIZE_FACTOR = 2
        private const val WAV_FILE_HEADER_SIZE = 44
    }
}
