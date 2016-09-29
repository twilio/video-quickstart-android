package com.twilio.video.examples.customcapturer;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Chronometer;
import android.widget.LinearLayout;

import com.twilio.video.LocalMedia;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.VideoView;

/**
 * This example demonstrates how to implement a custom capturer. Here we capture the contents
 * of a LinearLayout using {@link ViewCapturer}. To validate we render the video frames in a
 * {@link VideoView} below.
 */
public class CustomCapturerVideoActivity extends Activity {
    private LocalMedia localMedia;
    private LinearLayout capturedView;
    private VideoView videoView;
    private Chronometer timerView;
    private LocalVideoTrack localVideoTrack;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_capturer);

        localMedia = LocalMedia.create(this);
        capturedView = (LinearLayout) findViewById(R.id.captured_view);
        videoView = (VideoView) findViewById(R.id.video_view);
        timerView = (Chronometer) findViewById(R.id.timer_view);
        timerView.start();

        // Once added we should see our linear layout rendered live below
        localVideoTrack = localMedia.addVideoTrack(true, new ViewCapturer(capturedView));
        localVideoTrack.addRenderer(videoView);
    }

    @Override protected void onDestroy() {
        localVideoTrack.removeRenderer(videoView);
        localMedia.removeVideoTrack(localVideoTrack);
        timerView.stop();
        videoView.release();
        localMedia.release();
        super.onDestroy();
    }
}
