package com.twilio.video.quickstart.util;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.twilio.video.Camera2Capturer;
import com.twilio.video.CameraCapturer;
import com.twilio.video.VideoCapturer;

import org.webrtc.Camera2Enumerator;

/*
 * Simple wrapper class that uses Camera2Capturer with supported devices.
 */
public class CameraCapturerCompat {
    private static final String TAG = "CameraCapturerCompat";

    private CameraCapturer camera1Capturer;
    private Camera2Capturer camera2Capturer;
    private Pair<CameraCapturer.CameraSource, String> frontCameraPair;
    private Pair<CameraCapturer.CameraSource, String> backCameraPair;
    private final Camera2Capturer.Listener camera2Listener = new Camera2Capturer.Listener() {
        @Override
        public void onFirstFrameAvailable() {
            Log.i(TAG, "onFirstFrameAvailable");
        }

        @Override
        public void onCameraSwitched(String newCameraId) {
            Log.i(TAG, "onCameraSwitched: newCameraId = " + newCameraId);
        }

        @Override
        public void onError(Camera2Capturer.Exception camera2CapturerException) {
            Log.e(TAG, camera2CapturerException.getMessage());
        }
    };

    public CameraCapturerCompat(Context context,
                                CameraCapturer.CameraSource cameraSource) {
        if (Camera2Capturer.isSupported(context)) {
            setCameraPairs(context);
            camera2Capturer = new Camera2Capturer(context,
                    getCameraId(cameraSource),
                    camera2Listener);
        } else {
            camera1Capturer = new CameraCapturer(context, cameraSource);
        }
    }

    public CameraCapturer.CameraSource getCameraSource() {
        if (usingCamera1()) {
            return camera1Capturer.getCameraSource();
        } else {
            return getCameraSource(camera2Capturer.getCameraId());
        }
    }

    public void switchCamera() {
        if (usingCamera1()) {
            camera1Capturer.switchCamera();
        } else {
            CameraCapturer.CameraSource cameraSource = getCameraSource(camera2Capturer
                    .getCameraId());

            if (cameraSource == CameraCapturer.CameraSource.FRONT_CAMERA) {
                camera2Capturer.switchCamera(backCameraPair.second);
            } else {
                camera2Capturer.switchCamera(frontCameraPair.second);
            }
        }
    }

    /*
     * This method is required because this class is not an implementation of VideoCapturer due to
     * a shortcoming in the Video Android SDK.
     */
    public VideoCapturer getVideoCapturer() {
        if (usingCamera1()) {
            return camera1Capturer;
        } else {
            return camera2Capturer;
        }
    }

    private boolean usingCamera1() {
        return camera1Capturer != null;
    }

    private void setCameraPairs(Context context) {
        Camera2Enumerator camera2Enumerator = new Camera2Enumerator(context);
        for (String cameraId : camera2Enumerator.getDeviceNames()) {
            if (camera2Enumerator.isFrontFacing(cameraId)) {
                frontCameraPair = new Pair<>(CameraCapturer.CameraSource.FRONT_CAMERA, cameraId);
            }
            if (camera2Enumerator.isBackFacing(cameraId)) {
                backCameraPair = new Pair<>(CameraCapturer.CameraSource.BACK_CAMERA, cameraId);
            }
        }
    }

    private String getCameraId(CameraCapturer.CameraSource cameraSource) {
        if (frontCameraPair.first == cameraSource) {
            return frontCameraPair.second;
        } else {
            return backCameraPair.second;
        }
    }

    private CameraCapturer.CameraSource getCameraSource(String cameraId) {
        if (frontCameraPair.second.equals(cameraId)) {
            return frontCameraPair.first;
        } else {
            return backCameraPair.first;
        }
    }
}
