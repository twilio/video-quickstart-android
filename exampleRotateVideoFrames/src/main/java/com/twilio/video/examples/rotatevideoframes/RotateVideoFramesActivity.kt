package com.twilio.video.examples.rotatevideoframes

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.twilio.video.CameraCapturer
import com.twilio.video.LocalVideoTrack
import com.twilio.video.VideoView
import tvi.webrtc.Camera1Enumerator

class RotateVideoFramesActivity : Activity() {
    private lateinit var videoView: VideoView
    private lateinit var recordButton: Button
    private lateinit var rotateButton: Button

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
    private val recordButtonClickListener = View.OnClickListener { startVideoRecording() }
    private val rotateButtonClickListener = View.OnClickListener { rotateVideo() }

    /**
     * An example of a [tvi.webrtc.VideoProcessor] that decodes the preview image to a [Bitmap]
     * and shows the result in an alert dialog.
     */
    private val photographer by lazy { Photographer(videoView) }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_camera_capturer)
        videoView = findViewById<View>(R.id.video_view) as VideoView
        recordButton = findViewById<View>(R.id.record_video) as Button
        rotateButton = findViewById<View>(R.id.rotate_video) as Button

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
        recordButton.setOnClickListener(recordButtonClickListener)
        rotateButton.setOnClickListener(rotateButtonClickListener)
    }

    private fun startVideoRecording() {
        // TODO
    }

    private fun rotateVideo() {
        // TODO
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
}