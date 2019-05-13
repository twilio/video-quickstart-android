package com.twilio.exampleservice

import com.twilio.video.LocalAudioTrack
import com.twilio.video.LocalVideoTrack
import com.twilio.video.VideoRenderer

/**
 * Call Service public API methods.
 * Use to communicate with a [CallService] once it has been bound by a context.
 */
interface CallServiceAPI {
    val cameraCapturer: CameraCapturerWrapper?
    /**
     * Call this method when you want to notify the service that the activity that started it, is going to stop.
     */
    fun onActivityIsStopping()

    /**
     * Call this method to notify the service that the user has granted the appropriate permissions.
     */
    fun recordingAndCameraPermissionsGranted()

    /**
     * Notify the service the call (if existing) should be restored. Useful when a new activity starts and wants to bind
     * with the potentially running service to take over the call. If there's no call in progress, you'll still
     * want to call this after the service is bound.
     */
    fun restoreCallAfterBinding(localVideoView: VideoRenderer, callListener: CallListener)

    /**
     * Initializes the local video feed and sets the approrpiate rendereres to a [LocalVideoTrack]
     */
    fun initializeLocalVideoTrack(localVideoView: VideoRenderer)

    /**
     * return true if there's a call in progress, false otherwise.
     */
    fun isCallInProgress(): Boolean

    /**
     * return the [LocalVideoTrack] instance, if existing.
     */
    fun getLocalVideoTrack(): LocalVideoTrack?

    /**
     * return the [LocalAudioTrack] instance, if existing.
     */
    fun getLocalAudioTrack(): LocalAudioTrack?

    /**
     * Call this method when you want the service to become a foreground service, and to remove any View references from
     * the activity. After you call this, you can destroy your activity/context.
     * Re-bind to the service and call [restoreCallAfterBinding] to take control of an ongoing call again.
     */
    fun minimize()

    /**
     * Tries to connect to a Twilio Room; communicates back via the supplied [CallListener].
     */
    fun connectToRoom(accessToken: String, roomName: String, callListener: CallListener)

    /**
     * Disconnects an ongoing call (if existing) and clears references.
     */
    fun disconnectFromRoom()
}