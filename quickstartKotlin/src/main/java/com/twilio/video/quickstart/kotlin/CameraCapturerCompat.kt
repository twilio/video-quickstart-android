package com.twilio.video.quickstart.kotlin

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.Log
import android.util.Pair
import com.twilio.video.Camera2Capturer
import com.twilio.video.CameraCapturer
import com.twilio.video.VideoCapturer
import org.webrtc.Camera2Enumerator

class CameraCapturerCompat(context: Context, cameraSource: CameraCapturer.CameraSource) {
    private val TAG = "CameraCapturerCompat"

    private var camera1Capturer: CameraCapturer? = null
    private var camera2Capturer: Camera2Capturer? = null
    private var frontCameraPair: Pair<CameraCapturer.CameraSource, String>? = null
    private var backCameraPair: Pair<CameraCapturer.CameraSource, String>? = null
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private var cameraManager: CameraManager? = null
    private val camera2Listener = object : Camera2Capturer.Listener {
        override fun onFirstFrameAvailable() {
            Log.i(TAG, "onFirstFrameAvailable")
        }

        override fun onCameraSwitched(newCameraId: String) {
            Log.i(TAG, "onCameraSwitched: newCameraId = $newCameraId")
        }

        override fun onError(camera2CapturerException: Camera2Capturer.Exception) {
            Log.e(TAG, camera2CapturerException.message)
        }
    }
    val cameraSource: CameraCapturer.CameraSource
        get() {
            if (usingCamera1()) {
                return camera1Capturer!!.cameraSource
            } else {
                return getCameraSource(camera2Capturer!!.cameraId)
            }
        }
    /*
     * This property is required because this class is not an implementation of VideoCapturer due to
     * a shortcoming in the Video Android SDK.
     */
    val videoCapturer: VideoCapturer
        get() {
            if (usingCamera1()) {
                return camera1Capturer!!
            } else {
                return camera2Capturer!!
            }
        }


    init {
        if (Camera2Capturer.isSupported(context) && isLollipopApiSupported()) {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager?;
            setCameraPairs(context)
            camera2Capturer = Camera2Capturer(context,
                    getCameraId(cameraSource),
                    camera2Listener)
        } else {
            camera1Capturer = CameraCapturer(context, cameraSource)
        }
    }

    fun switchCamera() {
        if (usingCamera1()) {
            camera1Capturer!!.switchCamera()
        } else {
            val cameraSource = getCameraSource(camera2Capturer!!
                    .cameraId)

            if (cameraSource == CameraCapturer.CameraSource.FRONT_CAMERA) {
                camera2Capturer!!.switchCamera(backCameraPair!!.second)
            } else {
                camera2Capturer!!.switchCamera(frontCameraPair!!.second)
            }
        }
    }

    private fun usingCamera1(): Boolean {
        return camera1Capturer != null
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setCameraPairs(context: Context) {
        val camera2Enumerator = Camera2Enumerator(context)
        for (cameraId in camera2Enumerator.deviceNames) {
            if (isCameraIdSupported(cameraId)) {
                if (camera2Enumerator.isFrontFacing(cameraId)) {
                    frontCameraPair = Pair(CameraCapturer.CameraSource.FRONT_CAMERA, cameraId)
                }
                if (camera2Enumerator.isBackFacing(cameraId)) {
                    backCameraPair = Pair(CameraCapturer.CameraSource.BACK_CAMERA, cameraId)
                }
            }
        }
    }

    private fun getCameraId(cameraSource: CameraCapturer.CameraSource): String {
        return if (frontCameraPair!!.first == cameraSource) {
            frontCameraPair!!.second
        } else {
            backCameraPair!!.second
        }
    }

    private fun getCameraSource(cameraId: String): CameraCapturer.CameraSource {
        return if (frontCameraPair!!.second == cameraId) {
            frontCameraPair!!.first
        } else {
            backCameraPair!!.first
        }
    }

    private fun isLollipopApiSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun isCameraIdSupported(cameraId: String): Boolean {
        var isMonoChromeSupported = false
        val isPrivateImageFormatSupported: Boolean
        val cameraCharacteristics: CameraCharacteristics
        try {
            cameraCharacteristics = cameraManager!!.getCameraCharacteristics(cameraId)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            return false
        }

        /*
         * This is a temporary work around for a RuntimeException that occurs on devices which contain cameras
         * that do not support ImageFormat.PRIVATE output formats. A long term fix is currently in development.
         * https://github.com/twilio/video-quickstart-android/issues/431
         */
        val streamMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        isPrivateImageFormatSupported = streamMap!!.isOutputSupportedFor(ImageFormat.PRIVATE)

        /*
         * Read the color filter arrangements of the camera to filter out the ones that support
         * SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO or SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR.
         * Visit this link for details on supported values - https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SENSOR_INFO_COLOR_FILTER_ARRANGEMENT
         */
        val colorFilterArrangement = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)!!

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isMonoChromeSupported = if (colorFilterArrangement == CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO
                    || colorFilterArrangement == CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR) true else false
        }
        return isPrivateImageFormatSupported && !isMonoChromeSupported!!
    }
}