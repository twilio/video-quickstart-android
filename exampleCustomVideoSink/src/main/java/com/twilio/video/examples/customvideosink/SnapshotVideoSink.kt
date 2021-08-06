package com.twilio.video.examples.customvideosink

import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import com.twilio.video.examples.common.toBitmap
import java.util.concurrent.atomic.AtomicBoolean
import tvi.webrtc.VideoFrame
import tvi.webrtc.VideoSink

/**
 * SnapshotVideoSink demonstrates how to implement a custom [tvi.webrtc.VideoSink]. Caches the
 * last frame rendered and will update the provided image view any time [takeSnapshot] is
 * invoked.
 */
class SnapshotVideoSink(private val imageView: ImageView) : VideoSink {
    private val snapshotRequsted =
        AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    override fun onFrame(videoFrame: VideoFrame) {
        videoFrame.retain()
        if (snapshotRequsted.compareAndSet(true, false)) {
            val bitmap = videoFrame.toBitmap()
            handler.post {
                imageView.setImageBitmap(bitmap)
                videoFrame.release()
            }
        } else {
            videoFrame.release()
        }
    }

    /**
     * Request a snapshot on the rendering thread.
     */
    fun takeSnapshot() {
        snapshotRequsted.set(true)
    }
}
