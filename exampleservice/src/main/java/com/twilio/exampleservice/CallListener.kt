package com.twilio.exampleservice

import com.twilio.video.VideoTrack

interface CallListener {
    fun onConnected(roomName: String)
    fun onConnectFailure(errorMessage: String?)
    fun onDisconnected(roomName: String?, errorMessage: String?, cameraCapturer: CameraCapturerWrapper)

    fun onParticipantConnected(videoTrack: VideoTrack, cameraCapturer: CameraCapturerWrapper)
    fun onParticipantDisconnected(participantIdentity: String, videoTrack: VideoTrack, cameraCapturer: CameraCapturerWrapper)

}