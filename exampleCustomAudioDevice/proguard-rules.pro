-keep @com.twilio.video.AccessedByNative class *
-keepclassmembers class * {
    @com.twilio.video.AccessedByNative *;
}
-keepclassmembers class com.twilio.video.TwilioException {
    <init>(...);
}
-keep class com.twilio.video.VideoFormat
-keep class com.twilio.video.AudioFormat {
    getChannelCount(...);
    getSampleRate(...);
}
-keep class com.twilio.video.AudioDevice
-keep class com.twilio.video.AudioDeviceContext
-keepclassmembers class com.twilio.video.AudioDeviceProxy {
    <init>(...);
}
-keepclassmembers class com.twilio.video.MediaFactory {
    setAudioDeviceProxy(...);
}
-keep class tvi.webrtc.DefaultVideoDecoderFactory
-keep class tvi.webrtc.VideoCodecInfo
-keep class tvi.webrtc.VideoEncoderFactory {
    getSupportedCodecs(...);
    getImplementations(...);
}
-keep class tvi.webrtc.DefaultVideoEncoderFactory
-keep class tvi.webrtc.VideoDecoderFactory {
    getSupportedCodecs(...);
}

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
}

-keep class tvi.webrtc.ContextUtils {
    public static void initialize(...);
}
-keep @tvi.webrtc.CalledByNative class *
-keepclassmembers class * {
    @tvi.webrtc.CalledByNative *;
}
-keep @tvi.webrtc.CalledByNativeUnchecked class *
-keepclassmembers class * {
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

-whyareyoukeeping class com.twilio.video.ScreenCapturer
-whyareyoukeeping class tvi.webrtc.ScreenCapturerAndroid
