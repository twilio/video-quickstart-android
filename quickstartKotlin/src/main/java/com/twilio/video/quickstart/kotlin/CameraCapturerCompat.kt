package com.twilio.video.quickstart.kotlin

import android.annotation.TargetApi
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import com.twilio.video.Camera2Capturer
import com.twilio.video.CameraCapturer
import com.twilio.video.VideoCapturer
import java.util.EnumMap
import kotlin.collections.HashMap
import tvi.webrtc.Camera1Enumerator
import tvi.webrtc.Camera2Enumerator
import tvi.webrtc.CapturerObserver
import tvi.webrtc.SurfaceTextureHelper

/*
 * Simple wrapper class that uses Camera2Capturer with supported devices.
 */
class CameraCapturerCompat(context: Context, cameraSource: Source) : VideoCapturer {
    private val camera1Capturer: CameraCapturer?
    private val camera2Capturer: Camera2Capturer?
    private val activeCapturer: VideoCapturer
    private val camera1IdMap: MutableMap<Source, String> = EnumMap(Source::class.java)
    private val camera1SourceMap: MutableMap<String, Source> = HashMap()
    private val camera2IdMap: MutableMap<Source, String> = EnumMap(Source::class.java)
    private val camera2SourceMap: MutableMap<String, Source> = HashMap()

    enum class Source {
        FRONT_CAMERA, BACK_CAMERA
    }

    val cameraSource: Source
        get() {
            val source = if (usingCamera1()) {
                requireNotNull(camera1Capturer)
                camera1SourceMap[camera1Capturer.cameraId]
            } else {
                requireNotNull(camera2Capturer)
                camera2SourceMap[camera2Capturer.cameraId]
            }
            requireNotNull(source)
            return source
        }

    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper,
        context: Context,
        capturerObserver: CapturerObserver
    ) {
        activeCapturer.initialize(surfaceTextureHelper, context, capturerObserver)
    }

    override fun startCapture(width: Int, height: Int, framerate: Int) {
        activeCapturer.startCapture(width, height, framerate)
    }

    @Throws(InterruptedException::class)
    override fun stopCapture() {
        activeCapturer.stopCapture()
    }

    override fun isScreencast(): Boolean {
        return activeCapturer.isScreencast
    }

    override fun dispose() {
        activeCapturer.dispose()
    }

    fun switchCamera() {
        val cameraSource = cameraSource
        val idMap: Map<Source, String> = if (usingCamera1()) camera1IdMap else camera2IdMap
        val newCameraId =
            if (cameraSource == Source.FRONT_CAMERA) idMap[Source.BACK_CAMERA] else idMap[Source.FRONT_CAMERA]
        if (usingCamera1()) {
            newCameraId?.let { camera1Capturer?.switchCamera(it) }
        } else {
            newCameraId?.let { camera2Capturer?.switchCamera(it) }
        }
    }

    private fun usingCamera1(): Boolean {
        return camera1Capturer != null
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setCamera2Maps(context: Context) {
        val camera2Enumerator = Camera2Enumerator(context)
        for (cameraId in camera2Enumerator.deviceNames) {
            if (isCameraIdSupported(context, cameraId)) {
                if (camera2Enumerator.isFrontFacing(cameraId)) {
                    camera2IdMap[Source.FRONT_CAMERA] = cameraId
                    camera2SourceMap[cameraId] = Source.FRONT_CAMERA
                }
                if (camera2Enumerator.isBackFacing(cameraId)) {
                    camera2IdMap[Source.BACK_CAMERA] = cameraId
                    camera2SourceMap[cameraId] = Source.BACK_CAMERA
                }
            }
        }
    }

    private fun setCamera1Maps() {
        val camera1Enumerator = Camera1Enumerator()
        for (deviceName in camera1Enumerator.deviceNames) {
            if (camera1Enumerator.isFrontFacing(deviceName)) {
                camera1IdMap[Source.FRONT_CAMERA] = deviceName
                camera1SourceMap[deviceName] = Source.FRONT_CAMERA
            }
            if (camera1Enumerator.isBackFacing(deviceName)) {
                camera1IdMap[Source.BACK_CAMERA] = deviceName
                camera1SourceMap[deviceName] = Source.BACK_CAMERA
            }
        }
    }

    private val isLollipopApiSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun isCameraIdSupported(context: Context, cameraId: String): Boolean {
        var isMonoChromeSupported = false
        var isPrivateImageFormatSupported = false
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraCharacteristics: CameraCharacteristics
        cameraCharacteristics = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        /*
         * This is a temporary work around for a RuntimeException that occurs on devices which contain cameras
         * that do not support ImageFormat.PRIVATE output formats. A long term fix is currently in development.
         * https://github.com/twilio/video-quickstart-android/issues/431
         */
        val streamMap =
            cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (streamMap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isPrivateImageFormatSupported = streamMap.isOutputSupportedFor(ImageFormat.PRIVATE)
        }

        /*
         * Read the color filter arrangements of the camera to filter out the ones that support
         * SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO or SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR.
         * Visit this link for details on supported values - https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SENSOR_INFO_COLOR_FILTER_ARRANGEMENT
         */
        val colorFilterArrangement = cameraCharacteristics.get(
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && colorFilterArrangement != null) {
            isMonoChromeSupported = (colorFilterArrangement
                    == CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO ||
                    colorFilterArrangement
                    == CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR)
        }
        return isPrivateImageFormatSupported && !isMonoChromeSupported
    }

    init {
        if (Camera2Capturer.isSupported(context) && isLollipopApiSupported) {
            setCamera2Maps(context)
            camera2Capturer = Camera2Capturer(context, camera2IdMap[cameraSource]!!)
            activeCapturer = camera2Capturer
            camera1Capturer = null
        } else {
            setCamera1Maps()
            camera1Capturer = CameraCapturer(context, camera1IdMap[cameraSource]!!)
            activeCapturer = camera1Capturer
            camera2Capturer = null
        }
    }
}
