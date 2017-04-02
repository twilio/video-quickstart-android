package com.twilio.video.examples.screencapturer;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.twilio.video.LocalMedia;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.ScreenCapturer;
import com.twilio.video.VideoView;

/**
 * This example demonstrates how to use the screen capturer
 */
public class ScreenCapturerActivity extends AppCompatActivity {
    private static final String TAG = "ScreenCapturer";
    private static final int REQUEST_MEDIA_PROJECTION = 100;

    private LocalMedia localMedia;
    private VideoView localVideoView;
    private LocalVideoTrack screenVideoTrack;
    private ScreenCapturer screenCapturer;
    private MenuItem screenCaptureMenuItem;
    private final ScreenCapturer.Listener screenCapturerListener = new ScreenCapturer.Listener() {
        @Override
        public void onScreenCaptureError(String errorDescription) {
            Log.e(TAG, "Screen capturer error: " + errorDescription);
            stopScreenCapture();
            Toast.makeText(ScreenCapturerActivity.this, R.string.screen_capture_error,
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void onFirstFrameAvailable() {
            Log.d(TAG, "First frame from screen capturer available");
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_capturer);
        localVideoView = (VideoView) findViewById(R.id.local_video);

        localMedia = LocalMedia.create(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.screen_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Grab menu items for updating later
        screenCaptureMenuItem = menu.findItem(R.id.share_screen_menu_item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.share_screen_menu_item:
                String shareScreen = getString(R.string.share_screen);

                if (item.getTitle().equals(shareScreen)) {
                    if (screenCapturer == null) {
                        requestScreenCapturePermission();
                    } else {
                        startScreenCapture();
                    }
                } else {
                    stopScreenCapture();
                }

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void requestScreenCapturePermission() {
        Log.d(TAG, "Requesting permission to capture screen");
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // This initiates a prompt dialog for the user to confirm screen projection.
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != AppCompatActivity.RESULT_OK) {
                Toast.makeText(this, R.string.screen_capture_permission_not_granted,
                        Toast.LENGTH_LONG).show();
                return;
            }
            screenCapturer = new ScreenCapturer(this, resultCode, data, screenCapturerListener);
            startScreenCapture();
        }
    }

    private void startScreenCapture() {
        screenVideoTrack = localMedia.addVideoTrack(true, screenCapturer);
        screenCaptureMenuItem.setIcon(R.drawable.ic_stop_screen_share_white_24dp);
        screenCaptureMenuItem.setTitle(R.string.stop_screen_share);

        localVideoView.setVisibility(View.VISIBLE);
        screenVideoTrack.addRenderer(localVideoView);
    }

    private void stopScreenCapture() {
        localVideoView.setVisibility(View.INVISIBLE);
        localMedia.removeVideoTrack(screenVideoTrack);

        screenCaptureMenuItem.setIcon(R.drawable.ic_screen_share_white_24dp);
        screenCaptureMenuItem.setTitle(R.string.share_screen);
        screenVideoTrack.removeRenderer(localVideoView);
    }

    @Override
    protected void onDestroy() {
        if (localMedia != null) {
            if (screenVideoTrack != null) {
                localMedia.removeVideoTrack(screenVideoTrack);
            }
            localMedia.release();
            localMedia = null;
        }
        super.onDestroy();
    }

}
