package com.twilio.video.examples.rotatevideoframes

import android.graphics.Bitmap
import com.twilio.video.VideoView
import com.twilio.video.examples.common.toBitmap
import tvi.webrtc.VideoFrame
import tvi.webrtc.VideoProcessor
import tvi.webrtc.VideoSink
import java.util.concurrent.atomic.AtomicReference

typealias PictureListener = (Bitmap?) -> Unit

class Photographer(private val videoView: VideoView) : VideoProcessor {
    private val pictureRequest = AtomicReference<PictureListener?>(null)

    /**
     * These methods are part of the [tvi.webrtc.VideoProcessor] API, but are not required to be
     * implemented for this example.
     */
    override fun onCapturerStopped() {}
    override fun onCapturerStarted(success: Boolean) {}
    override fun setSink(videoSink: VideoSink?) {}
    override fun onFrameCaptured(frame: VideoFrame?) {}

    /**
     * This onFrameCaptured method provides an unadapted [tvi.webrtc.VideoFrame].
     *
     * The following code demonstrates how to capture the unadapted frame to a [Bitmap] and then
     * forward the adapted frame to a [VideoView].
     */
    override fun onFrameCaptured(
        videoFrame: VideoFrame?,
        parameters: VideoProcessor.FrameAdaptationParameters?
    ) {
        requireNotNull(videoFrame)
        videoFrame.retain()

        /**
         * Get the picture request and then capture the current frame to a Bitmap. The
         * picture request is cleared for subsequent calls to [takePicture].
         */
        pictureRequest.getAndSet(null)?.invoke(videoFrame.toBitmap())

        /**
         * Adapt the current frame and forward to the video view.
         */
        VideoProcessor.applyFrameAdaptationParameters(videoFrame, parameters)?.let {
            videoView.onFrame(it)
            it.release()
        } ?: videoView.onFrame(videoFrame)
        videoFrame.release()
    }

    fun takePicture(pictureListener: PictureListener) {
        this.pictureRequest.set(pictureListener)
    }
}