package com.twilio.exampleservice

import com.twilio.video.CameraCapturer

/**
 * Simple wrapper to leak less dependencies outside of Twilio and facilitate Unit Test.
 */
class CameraCapturerWrapper(val cameraCapturerCompat: CameraCapturerCompat) {

    fun isFrontCamera(): Boolean {
        return cameraCapturerCompat.cameraSource == CameraCapturer.CameraSource.FRONT_CAMERA
    }

    fun switchCamera() {
        cameraCapturerCompat.switchCamera()
    }
}