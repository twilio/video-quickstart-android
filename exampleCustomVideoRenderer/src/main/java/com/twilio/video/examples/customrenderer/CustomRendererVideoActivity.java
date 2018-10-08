package com.twilio.video.examples.customrenderer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.twilio.video.CameraCapturer;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.VideoView;

/**
 * This example demonstrates how to implement a custom renderer. Here we render the contents
 * of our {@link CameraCapturer} to a video view and to a snapshot renderer which allows user to
 * grab the latest frame rendered. When the camera view is tapped the frame is updated.
 */
public class CustomRendererVideoActivity extends Activity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private VideoView localVideoView;
    private ImageView snapshotImageView;
    private TextView tapForSnapshotTextView;
    private SnapshotVideoRenderer snapshotVideoRenderer;
    private LocalVideoTrack localVideoTrack;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_renderer);

        localVideoView = (VideoView) findViewById(R.id.local_video);
        snapshotImageView = (ImageView) findViewById(R.id.image_view);
        tapForSnapshotTextView = (TextView) findViewById(R.id.tap_video_snapshot);

        /*
         * Check camera permissions. Needed in Android M.
         */
        if (!checkPermissionForCamera()) {
            requestPermissionForCamera();
        } else {
            addVideo();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            boolean cameraPermissionGranted = true;

            for (int grantResult : grantResults) {
                cameraPermissionGranted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }

            if (cameraPermissionGranted) {
                addVideo();
            } else {
                Toast.makeText(this,
                        R.string.permissions_needed,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        localVideoTrack.removeRenderer(localVideoView);
        localVideoTrack.removeRenderer(snapshotVideoRenderer);
        localVideoTrack.release();
        localVideoTrack = null;
        super.onDestroy();
    }

    private void addVideo() {
        localVideoTrack = LocalVideoTrack.create(this, true, new CameraCapturer(this,
                CameraCapturer.CameraSource.FRONT_CAMERA, null));
        snapshotVideoRenderer = new SnapshotVideoRenderer(snapshotImageView);
        localVideoTrack.addRenderer(localVideoView);
        localVideoTrack.addRenderer(snapshotVideoRenderer);
        localVideoView.setOnClickListener(v -> {
            tapForSnapshotTextView.setVisibility(View.GONE);
            snapshotVideoRenderer.takeSnapshot();
        });
    }

    private boolean checkPermissionForCamera(){
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        return resultCamera == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForCamera(){
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE);
    }
}
