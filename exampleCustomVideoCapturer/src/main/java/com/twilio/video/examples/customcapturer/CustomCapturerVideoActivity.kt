package com.twilio.video.examples.customcapturer

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Chronometer
import android.widget.LinearLayout
import com.twilio.video.LocalVideoTrack
import com.twilio.video.VideoView

/**
 * This example demonstrates how to implement a custom capturer. Here we capture the contents
 * of a LinearLayout using [ViewCapturer]. To validate we render the video frames in a
 * [VideoView] below.
 */
class CustomCapturerVideoActivity : Activity() {
    private lateinit var capturedView: LinearLayout
    private lateinit var videoView: VideoView
    private lateinit var timerView: Chronometer
    private val localVideoTrack by lazy {
        LocalVideoTrack.create(this, true, ViewCapturer(capturedView))
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_capturer)
        capturedView = findViewById<View>(R.id.captured_view) as LinearLayout
        videoView = findViewById<View>(R.id.video_view) as VideoView
        timerView = findViewById<View>(R.id.timer_view) as Chronometer
        timerView.start()

        // Once added we should see our linear layout rendered live below
        localVideoTrack?.addSink(videoView)
    }

    override fun onDestroy() {
        localVideoTrack?.removeSink(videoView)
        localVideoTrack?.release()
        timerView.stop()
        super.onDestroy()
    }
}