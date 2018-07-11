package com.twilio.video.examples.advancedcameracapturer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.twilio.video.CameraCapturer;
import com.twilio.video.CameraParameterUpdater;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.VideoView;

/**
 * This example demonstrates advanced use cases of {@link com.twilio.video.CameraCapturer}. Current
 * use cases shown are as follows:
 *
 * <ol>
 *     <li>Setting Custom {@link android.hardware.Camera.Parameters}</li>
 *     <li>Taking a picture while capturing</li>
 * </ol>
 */
public class AdvancedCameraCapturerActivity extends Activity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private VideoView videoView;
    private Button toggleFlashButton;
    private Button takePictureButton;
    private ImageView pictureImageView;
    private AlertDialog pictureDialog;
    private CameraCapturer cameraCapturer;
    private LocalVideoTrack localVideoTrack;
    private boolean flashOn = false;
    private final View.OnClickListener toggleFlashButtonClickListener = v -> toggleFlash();
    private final View.OnClickListener takePictureButtonClickListener = v -> takePicture();

    /**
     * An example of a {@link CameraParameterUpdater} that shows how to toggle the flash of a
     * camera if supported by the device.
     */
    private final CameraParameterUpdater flashToggler = parameters -> {
        if (parameters.getFlashMode() != null) {
            String flashMode = flashOn ?
                    Camera.Parameters.FLASH_MODE_OFF :
                    Camera.Parameters.FLASH_MODE_TORCH;
            parameters.setFlashMode(flashMode);
            flashOn = !flashOn;
        } else {
            Toast.makeText(AdvancedCameraCapturerActivity.this,
                    R.string.flash_not_supported,
                    Toast.LENGTH_LONG).show();
        }
    };

    /**
     * An example of a {@link com.twilio.video.CameraCapturer.PictureListener} that decodes the
     * image to a {@link Bitmap} and shows the result in an alert dialog.
     */
    private final CameraCapturer.PictureListener photographer =
            new CameraCapturer.PictureListener() {
                @Override
                public void onShutter() {

                }

                @Override
                public void onPictureTaken(byte[] bytes) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                    if (bitmap != null) {
                        showPicture(bitmap);
                    } else {
                        Toast.makeText(AdvancedCameraCapturerActivity.this,
                                R.string.take_picture_failed,
                                Toast.LENGTH_LONG).show();
                    }
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advanced_camera_capturer);

        videoView = (VideoView) findViewById(R.id.video_view);
        toggleFlashButton = (Button) findViewById(R.id.toggle_flash_button);
        takePictureButton = (Button) findViewById(R.id.take_picture_button);
        pictureImageView = (ImageView) getLayoutInflater().inflate(R.layout.picture_image_view,
                null);
        pictureDialog = new AlertDialog.Builder(this)
                .setView(pictureImageView)
                .setTitle(null)
                .setPositiveButton(R.string.close, (dialog, which) -> dialog.dismiss()).create();

        if (!checkPermissionForCamera()) {
            requestPermissionForCamera();
        } else {
            addCameraVideo();
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
                addCameraVideo();
            } else {
                Toast.makeText(this,
                        R.string.permissions_needed,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        localVideoTrack.removeRenderer(videoView);
        if (localVideoTrack != null) {
            localVideoTrack.release();
            localVideoTrack = null;
        }
        super.onDestroy();
    }

    private boolean checkPermissionForCamera(){
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        return resultCamera == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForCamera(){
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE);
    }

    private void addCameraVideo() {
        cameraCapturer = new CameraCapturer(this, CameraCapturer.CameraSource.BACK_CAMERA);
        localVideoTrack = LocalVideoTrack.create(this, true, cameraCapturer);
        localVideoTrack.addRenderer(videoView);
        toggleFlashButton.setOnClickListener(toggleFlashButtonClickListener);
        takePictureButton.setOnClickListener(takePictureButtonClickListener);
    }

    private void toggleFlash() {
        // Request an update to camera parameters with flash toggler
        cameraCapturer.updateCameraParameters(flashToggler);
    }

    private void takePicture() {
        cameraCapturer.takePicture(photographer);
    }

    private void showPicture(final Bitmap bitmap) {
        pictureImageView.setImageBitmap(bitmap);
        pictureDialog.show();
    }
}
