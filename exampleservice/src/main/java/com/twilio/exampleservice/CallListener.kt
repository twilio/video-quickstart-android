package com.twilio.exampleservice

import com.twilio.video.VideoTrack

/**
 * Callback interface to communicate with a [CallServiceAPI]
 */
interface CallListener {
    /**
     * Called when Twilio connects to a room.
     */
    fun onConnected(roomName: String)

    /**
     * Called when Twilio couldn't connect to a room.
     */
    fun onConnectFailure(errorMessage: String?)

    /**
     * Called after you are disconnected from a Room. The [CameraCapturerWrapper] is useful to determine which camera
     * is used.
     */
    fun onDisconnected(roomName: String?, errorMessage: String?, cameraCapturer: CameraCapturerWrapper)

    /**
     * Called when a participant connects to a room; you get its [VideoTrack] and a [CameraCapturerWrapper] which the UI
     * can use to display and accomodate the widgets.
     */
    fun onParticipantConnected(videoTrack: VideoTrack, cameraCapturer: CameraCapturerWrapper)

    /**
     * Called when a participant disconnects from a room; you get its [VideoTrack] and a [CameraCapturerWrapper] which
     * the UI can use to display and accomodate the widgets.
     */
    fun onParticipantDisconnected(participantIdentity: String, videoTrack: VideoTrack, cameraCapturer: CameraCapturerWrapper)

}