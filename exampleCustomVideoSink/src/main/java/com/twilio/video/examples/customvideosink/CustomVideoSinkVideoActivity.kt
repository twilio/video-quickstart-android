package com.twilio.video.examples.customvideosink

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.twilio.video.CameraCapturer
import com.twilio.video.LocalVideoTrack
import com.twilio.video.VideoView
import tvi.webrtc.Camera1Enumerator

/**
 * This example demonstrates how to implement a custom renderer. Here we render the contents
 * of our [CameraCapturer] to a video view and to a snapshot renderer which allows user to
 * grab the latest frame rendered. When the camera view is tapped the frame is updated.
 */
class CustomVideoSinkVideoActivity : Activity() {
    private lateinit var localVideoView: VideoView
    private lateinit var snapshotImageView: ImageView
    private lateinit var tapForSnapshotTextView: TextView
    private val snapshotVideoRenderer by lazy {
        SnapshotVideoSink(snapshotImageView)
    }
    private val frontCameraId by lazy {
        val camera1Enumerator = Camera1Enumerator()
        val cameraId = camera1Enumerator.deviceNames.find { camera1Enumerator.isFrontFacing(it) }
        requireNotNull(cameraId)
    }
    private val localVideoTrack by lazy {
        LocalVideoTrack.create(
            this, true, CameraCapturer(
                this,
                frontCameraId, null
            )
        )
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_renderer)
        localVideoView =
            findViewById<View>(R.id.local_video) as VideoView
        snapshotImageView =
            findViewById<View>(R.id.image_view) as ImageView
        tapForSnapshotTextView =
            findViewById<View>(R.id.tap_video_snapshot) as TextView

        /*
         * Check camera permissions. Needed in Android M.
         */
        if (!checkPermissionForCamera()) {
            requestPermissionForCamera()
        } else {
            addVideo()
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
                addVideo()
            } else {
                Toast.makeText(
                    this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        localVideoTrack?.removeSink(localVideoView)
        localVideoTrack?.removeSink(snapshotVideoRenderer)
        localVideoTrack?.release()
        super.onDestroy()
    }

    private fun addVideo() {
        localVideoTrack?.addSink(localVideoView)
        localVideoTrack?.addSink(snapshotVideoRenderer)
        localVideoView.setOnClickListener {
            tapForSnapshotTextView.visibility = View.GONE
            snapshotVideoRenderer.takeSnapshot()
        }
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

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
}
