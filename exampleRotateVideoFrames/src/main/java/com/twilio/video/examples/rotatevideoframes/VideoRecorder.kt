package com.twilio.video.examples.rotatevideoframes

import android.util.Log
import com.twilio.video.VideoView
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onCompletion
import tvi.webrtc.VideoFrame
import tvi.webrtc.VideoProcessor
import tvi.webrtc.VideoSink

class VideoRecorder(private val videoView: VideoView) : VideoProcessor {
    private val _videoFrameFlow = MutableSharedFlow<VideoFrame>(extraBufferCapacity = 10)
    val videoFrameFlow = _videoFrameFlow.asSharedFlow()

    /**
     * These methods are part of the [tvi.webrtc.VideoProcessor] API, but are not required to be
     * implemented for this example.
     */
    override fun onCapturerStopped() {}
    override fun onCapturerStarted(success: Boolean) {}
    override fun setSink(videoSink: VideoSink?) {}
    override fun onFrameCaptured(frame: VideoFrame?) {}

    override fun onFrameCaptured(
        videoFrame: VideoFrame?,
        parameters: VideoProcessor.FrameAdaptationParameters?
    ) {
        requireNotNull(videoFrame)
        videoFrame.retain()

        /**
         * Get information needed from frame and emit to flow for asynchronous processing.
         */
        if (_videoFrameFlow.subscriptionCount.value > 0) {
            _videoFrameFlow.tryEmit(videoFrame)
        }

        /**
         * Adapt the current frame and forward to the video view.
         */
        val adaptedFrame = VideoProcessor.applyFrameAdaptationParameters(videoFrame, parameters) ?: videoFrame
        videoView.onFrame(adaptedFrame)
        adaptedFrame.release()
        Log.d("VideoRecorder", "Passed adaptive frame to view")
    }
}