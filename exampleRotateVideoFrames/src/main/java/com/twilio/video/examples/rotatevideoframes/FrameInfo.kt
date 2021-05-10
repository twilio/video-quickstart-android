package com.twilio.video.examples.rotatevideoframes

import tvi.webrtc.VideoFrame

data class FrameInfo(
    val I420Buffer: VideoFrame.I420Buffer,
    val rotation: Int,
    val timestampNs: Long
)