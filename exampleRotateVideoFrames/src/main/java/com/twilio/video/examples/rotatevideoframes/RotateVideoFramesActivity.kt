package com.twilio.video.examples.rotatevideoframes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.twilio.video.CameraCapturer
import com.twilio.video.LocalVideoTrack
import com.twilio.video.VideoDimensions
import com.twilio.video.VideoFormat
import com.twilio.video.VideoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import tvi.webrtc.Camera1Enumerator

class RotateVideoFramesActivity : ComponentActivity() {
    private lateinit var videoView: VideoView
    private lateinit var recordButton: Button
    private var mediaScope = CoroutineScope(lifecycleScope.coroutineContext + Dispatchers.Default)
    private lateinit var mediaHandler: MediaHandler
    private val videoFormat = VideoFormat(
            VideoDimensions(VideoDimensions.HD_720P_VIDEO_WIDTH, VideoDimensions.HD_720P_VIDEO_HEIGHT),
            30)

    private val backCameraId by lazy {
        val camera1Enumerator = Camera1Enumerator()
        val cameraId = camera1Enumerator.deviceNames.find { camera1Enumerator.isBackFacing(it) }
        requireNotNull(cameraId)
    }
    private val cameraCapturer by lazy {
        CameraCapturer(this, backCameraId)
    }
    private val localVideoTrack by lazy {
        LocalVideoTrack.create(this, true, cameraCapturer, videoFormat)
    }
    private val recordButtonClickListener = View.OnClickListener {
        recordButton.text = if(recordButton.text == getString(R.string.start_recording)) {
            startVideoRecording()
            getString(R.string.stop_recording)
        } else {
            stopVideoRecording()
            getString(R.string.start_recording)
        }
    }
    private val videoRecorder by lazy { VideoRecorder(videoView) }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_camera_capturer)
        mediaHandler = MediaHandler(
                this,
                videoFormat,
                mediaScope
        )
        videoView = findViewById<View>(R.id.video_view) as VideoView
        recordButton = findViewById<View>(R.id.record_video) as Button

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
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
        localVideoTrack?.videoSource?.setVideoProcessor(videoRecorder)
        recordButton.setOnClickListener(recordButtonClickListener)
    }

    private fun startVideoRecording() {
        mediaHandler.encodeVideo(videoRecorder.videoFrameFlow)
    }

    private fun stopVideoRecording() {
        mediaHandler.close()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
}