package com.twilio.video.examples.customcapturer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import com.twilio.video.Rgba8888Buffer
import com.twilio.video.VideoCapturer
import com.twilio.video.VideoDimensions
import com.twilio.video.VideoFormat
import tvi.webrtc.CapturerObserver
import tvi.webrtc.SurfaceTextureHelper
import tvi.webrtc.VideoFrame
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ViewCapturer demonstrates how to implement a custom [VideoCapturer]. This class
 * captures the contents of a provided view and signals the [tvi.webrtc.CapturerObserver] when
 * the frame is available.
 */
class ViewCapturer(private val view: View) : VideoCapturer {
    private val handler = Handler(Looper.getMainLooper())
    private var capturerObserver: CapturerObserver? = null
    private val started =
        AtomicBoolean(false)

    private val viewCapturer = {
        val dropFrame = view.width == 0 || view.height == 0

        // Only capture the view if the dimensions have been established
        if (!dropFrame) {
            // Draw view into bitmap backed canvas
            val measuredWidth = View.MeasureSpec.makeMeasureSpec(
                view.width,
                View.MeasureSpec.EXACTLY
            )
            val measuredHeight = View.MeasureSpec.makeMeasureSpec(
                view.height,
                View.MeasureSpec.EXACTLY
            )
            view.measure(measuredWidth, measuredHeight)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val viewBitmap = Bitmap.createBitmap(
                view.width, view.height,
                Bitmap.Config.ARGB_8888
            )
            val viewCanvas = Canvas(viewBitmap)
            view.draw(viewCanvas)

            // Extract the frame from the bitmap
            val bytes = viewBitmap.byteCount
            val buffer = ByteBuffer.allocate(bytes)
            viewBitmap.copyPixelsToBuffer(buffer)

            // Create video frame
            val captureTimeNs =
                TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime())
            val videoBuffer: VideoFrame.Buffer =
                Rgba8888Buffer(buffer, view.width, view.height)
            val videoFrame = VideoFrame(videoBuffer, 0, captureTimeNs)

            // Notify the observer
            if (started.get()) {
                capturerObserver!!.onFrameCaptured(videoFrame)
                videoFrame.release()
            }
        }

        // Schedule the next capture
        if (started.get()) {
            scheduleNextCapture()
        }
    }

    override fun getCaptureFormat(): VideoFormat {
        val videoDimensions = VideoDimensions(view.width, view.height)
        return VideoFormat(videoDimensions, 30)
    }

    /**
     * Returns true because we are capturing screen content.
     */
    override fun isScreencast(): Boolean {
        return true
    }

    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper,
        context: Context,
        capturerObserver: CapturerObserver
    ) {
        this.capturerObserver = capturerObserver
    }

    override fun startCapture(width: Int, height: Int, framerate: Int) {
        started.set(true)

        // Notify capturer API that the capturer has started
        val capturerStarted = handler.postDelayed(viewCapturer, VIEW_CAPTURER_FRAMERATE_MS.toLong())
        capturerObserver?.onCapturerStarted(capturerStarted)
    }

    /**
     * Stop capturing frames. Note that the SDK cannot receive frames once this has been invoked.
     */
    override fun stopCapture() {
        started.set(false)
        handler.removeCallbacks(viewCapturer)
        capturerObserver?.onCapturerStopped()
    }

    private fun scheduleNextCapture() {
        handler.postDelayed(viewCapturer, VIEW_CAPTURER_FRAMERATE_MS.toLong())
    }

    companion object {
        private const val VIEW_CAPTURER_FRAMERATE_MS = 100
    }
}