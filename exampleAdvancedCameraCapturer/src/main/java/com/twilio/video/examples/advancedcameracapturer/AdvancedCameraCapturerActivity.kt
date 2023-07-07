package com.twilio.video.examples.advancedcameracapturer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Camera
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.twilio.video.CameraCapturer
import com.twilio.video.CameraParameterUpdater
import com.twilio.video.LocalVideoTrack
import com.twilio.video.VideoView
import tvi.webrtc.Camera1Enumerator

/**
 * This example demonstrates advanced use cases of [com.twilio.video.CameraCapturer]. Current
 * use cases shown are as follows:
 *
 *  1. Setting Custom [android.hardware.Camera.Parameters]
 *  2. Taking a picture while capturing
 */
class AdvancedCameraCapturerActivity : Activity() {
    private lateinit var videoView: VideoView
    private lateinit var toggleFlashButton: Button
    private lateinit var takePictureButton: Button
    private lateinit var pictureImageView: ImageView
    private lateinit var pictureDialog: AlertDialog

    private val backCameraId by lazy {
        val camera1Enumerator = Camera1Enumerator()
        val cameraId = camera1Enumerator.deviceNames.find { camera1Enumerator.isBackFacing(it) }
        requireNotNull(cameraId)
    }
    private val cameraCapturer by lazy {
        CameraCapturer(this, backCameraId)
    }
    private val localVideoTrack by lazy {
        LocalVideoTrack.create(this, true, cameraCapturer)
    }
    private var flashOn = false
    private val toggleFlashButtonClickListener = View.OnClickListener { toggleFlash() }
    private val takePictureButtonClickListener = View.OnClickListener { takePicture() }

    /**
     * An example of a [CameraParameterUpdater] that shows how to toggle the flash of a
     * camera if supported by the device.
     */
    @Suppress("DEPRECATION")
    private val flashToggler =
        CameraParameterUpdater { parameters: Camera.Parameters ->
            if (parameters.flashMode != null) {
                val flashMode =
                    if (flashOn) Camera.Parameters.FLASH_MODE_OFF else Camera.Parameters.FLASH_MODE_TORCH
                parameters.flashMode = flashMode
                flashOn = !flashOn
            } else {
                Toast.makeText(
                    this@AdvancedCameraCapturerActivity,
                    R.string.flash_not_supported,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    /**
     * An example of a [tvi.webrtc.VideoProcessor] that decodes the preview image to a [Bitmap]
     * and shows the result in an alert dialog.
     */
    private val photographer by lazy { Photographer(videoView) }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_camera_capturer)
        videoView = findViewById<View>(R.id.video_view) as VideoView
        toggleFlashButton = findViewById<View>(R.id.toggle_flash_button) as Button
        takePictureButton = findViewById<View>(R.id.take_picture_button) as Button
        pictureImageView = layoutInflater.inflate(
            R.layout.picture_image_view,
            findViewById(android.R.id.content),
            false
        ) as ImageView
        pictureDialog = AlertDialog.Builder(this)
            .setView(pictureImageView)
            .setTitle(null)
            .setPositiveButton(
                R.string.close
            ) { dialog: DialogInterface, _: Int -> dialog.dismiss() }.create()

        if (!checkPermissionForCamera()) {
            requestPermissionForCamera()
        } else {
            addCameraVideo()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            var cameraPermissionGranted = true
            for (grantResult in grantResults) {
                cameraPermissionGranted =
                    cameraPermissionGranted and (grantResult == PackageManager.PERMISSION_GRANTED)
            }
            if (cameraPermissionGranted) {
                addCameraVideo()
            } else {
                Toast.makeText(
                    this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        localVideoTrack?.removeSink(videoView)
        localVideoTrack?.release()
        super.onDestroy()
    }

    private fun checkPermissionForCamera(): Boolean {
        val resultCamera =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        return resultCamera == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionForCamera() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun addCameraVideo() {
        localVideoTrack?.videoSource?.setVideoProcessor(photographer)
        toggleFlashButton.setOnClickListener(toggleFlashButtonClickListener)
        takePictureButton.setOnClickListener(takePictureButtonClickListener)
    }

    private fun toggleFlash() {
        // Request an update to camera parameters with flash toggler
        cameraCapturer.updateCameraParameters(flashToggler)
    }

    private fun takePicture() {
        photographer.takePicture { bitmap: Bitmap? ->
            bitmap?.let {
                runOnUiThread { showPicture(it) }
            }
        }
    }

    private fun showPicture(bitmap: Bitmap) {
        pictureImageView.setImageBitmap(bitmap)
        pictureDialog.show()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
}
