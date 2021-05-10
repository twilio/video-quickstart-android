package com.twilio.video.examples.rotatevideoframes

import android.content.Context
import android.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import tvi.webrtc.*
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class MediaHandler(
        context: Context,
        private val externalScope: CoroutineScope
) {
    private var videoEncoderDone = CompletableDeferred<Unit>()
    private lateinit var encodeVideoJob: Job
    private val videoMediaFormat = MediaFormat().apply {
        setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_AVC)
        setInteger(MediaFormat.KEY_WIDTH, 1080) // hardcoded
        setInteger(MediaFormat.KEY_HEIGHT, 1920) // hardcoded
        setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        setInteger(MediaFormat.KEY_BIT_RATE, 1080 * 1920 * 5)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }

    private val videoCodec by lazy {
        val encoder = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(videoMediaFormat)
                ?: throw IllegalStateException("No matching codecs available on device")

        MediaCodec.createByCodecName(encoder).apply {
            videoMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            configure(videoMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    private val endOfStream = AtomicBoolean(false)
    private val pendingVideoEncoderInputBufferIndicesChannel = Channel<Int>(capacity = Channel.BUFFERED)
    private val localFileName = "${UUID.randomUUID()}.mp4"
    private val filePath = "${context.filesDir}/$localFileName"
    private var videoTrackIndex: Int? = null
    private lateinit var mediaMuxer: MediaMuxer

    private val videoEncoderProcessor = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            pendingVideoEncoderInputBufferIndicesChannel.offer(index)
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            muxVideo(index, info)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {}

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            mediaMuxer = MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            videoTrackIndex = mediaMuxer.addTrack(format)
            mediaMuxer.start()
        }
    }

    init {
        videoCodec.apply {
            setCallback(videoEncoderProcessor)
            start()
        }
    }

    private fun waitForCompletion() = externalScope.launch {
        videoEncoderDone.await()

        videoCodec.stop()
        videoCodec.release()
        mediaMuxer.stop()
        mediaMuxer.release()
    }

    private fun muxVideo(index: Int, bufferInfo: MediaCodec.BufferInfo) {
        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            videoCodec.releaseOutputBuffer(index, false)
            return
        }

        val encodedBuffer = try {
            videoCodec.getOutputBuffer(index)
        } catch (e: IllegalStateException) {
            return
        }

        if (bufferInfo.size != 0 && encodedBuffer != null) {
            videoTrackIndex?.let { mediaMuxer.writeSampleData(it, encodedBuffer, bufferInfo) }
        }

        videoCodec.releaseOutputBuffer(index, false)

        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            videoEncoderDone.complete(Unit)
        }
    }

    fun encodeVideo(flow: Flow<FrameInfo>) {
        encodeVideoJob = flow.onEach { frameInfo ->
            if (endOfStream.get()) {
                return@onEach
            }

            encode(sample = frameInfo.I420Buffer, rotation = frameInfo.rotation, codec = videoCodec, pts = frameInfo.timestampNs / 1000, availableIndex = pendingVideoEncoderInputBufferIndicesChannel.receive())
        }.launchIn(externalScope)
    }

    private fun encode(sample: VideoFrame.I420Buffer, rotation: Int, pts: Long, codec: MediaCodec, availableIndex: Int) {
        val size = sample.height * sample.width * 3 / 2
        val input = try {
            codec.getInputBuffer(availableIndex)
        } catch (e: IllegalStateException) {
            return
        }
        input?.apply {
            YuvHelper.I420Copy(sample.dataY, sample.strideY, sample.dataU, sample.strideU, sample.dataV, sample.strideV, this, sample.width, sample.height)
//            YuvHelper.I420Rotate(sample.dataY, sample.strideY, sample.dataU, sample.strideU, sample.dataV, sample.strideV, this, sample.width, sample.height, rotation)
            codec.queueInputBuffer(availableIndex, 0, size, pts, 0)
        }
        sample.release()
    }

    fun close() {
        endOfStream.set(true) // stop encoder from receiving data
        if (this::encodeVideoJob.isInitialized) {
            encodeVideoJob.cancel()
        }
        waitForCompletion() // launch job to await deferred value to be called
        queueEos() // launch jobs to wait for available input buffer and signal EoS
    }

    private fun queueEos() = externalScope.apply {
        launch {
            val index = pendingVideoEncoderInputBufferIndicesChannel.receive()
            videoCodec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
    }
}