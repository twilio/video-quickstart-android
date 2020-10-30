-keep class tvi.webrtc.SurfaceTextureHelper {
    public static tvi.webrtc.SurfaceTextureHelper create(...);
}
-keep class tvi.webrtc.WebRtcClassLoader
-keep class tvi.webrtc.JniHelper
-keep class tvi.webrtc.EglBase$Context
-keep class tvi.webrtc.VideoCapturer$CapturerObserver
-keep class tvi.webrtc.MediaCodecVideoEncoder {
    initEncode(...);
}
-keep class tvi.webrtc.MediaCodecVideoDecoder
-keep class tvi.webrtc.MediaCodecVideoDecoder$VideoCodecType
-keep class tvi.webrtc.MediaCodecVideoEncoder$VideoCodecType
-keep class tvi.webrtc.audio.WebRtcAudioManager
-keep class tvi.webrtc.voiceengine.BuildInfo
-keep class tvi.webrtc.voiceengine.WebRtcAudioRecord
-keep class tvi.webrtc.VideoRenderer$I420Frame
-keep class tvi.webrtc.VideoFrame$I420Buffer
-keep class tvi.webrtc.VideoRenderer$Callbacks {
    renderFrame(...);
}
-keep class tvi.webrtc.VideoFrame$Buffer {
    retain(...);
    cropAndScale(...);
    toI420(...);
            oigningConf
}
-keep class tvi.webrtc.ContextUtils {
    public static void initialize(...);
}
-keep @tvi.webrtc.CalledByNative class *
-keep class * {
    @tvi.webrtc.CalledByNative *;
}
-keep @tvi.webrtc.CalledByNativeUnchecked class *
-keep class * {
    @tvi.webrtc.CalledByNativeUnchecked *;
}
-keep class tvi.webrtc.voiceengine.** {
    native <methods>;
}
-keep class tvi.webrtc.voiceengine.WebRtcAudioManager {
    <init>(...);
    init(...);
    dispose(...);
    isCommunicationModeEnabled(...);
    isDeviceBlacklistedForOpenSLESUsage(...);
}
-keep class tvi.webrtc.voiceengine.WebRtcAudioRecord {
    <init>(...);
    initRecording(...);
    startRecording(...);
    stopRecording(...);
    enableBuiltInAEC(...);
    enableBuiltInNS(...);
}
-keep class tvi.webrtc.voiceengine.WebRtcAudioTrack {
    <init>(...);
    initPlayout(...);
    startPlayout(...);
    stopPlayout(...);
    setStreamVolume(...);
    getStreamVolume(...);
    getStreamMaxVolume(...);
}
-keep @tvi.webrtc.JNINamespace class *
-keep class !com.twilio.video.ScreenCapturer, com.twilio.video.** { *; }