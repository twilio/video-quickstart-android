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
-keepclassmembers class !tvi.webrtc.NetworkMonitor, !tvi.webrtc.NetworkMonitorAutoDetect, * {
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

-keep class com.twilio.video.AudioTrackPublication { *; }
-keep class com.twilio.video.BaseTrackStats { *; }
-keep class com.twilio.video.Camera2Utils { *; }
-keep class com.twilio.video.CameraParameterUpdater { *; }
-keep class com.twilio.video.CaptureRequestUpdater { *; }
-keep class com.twilio.video.DataTrack { *; }
-keep class com.twilio.video.DataTrackOptions { *; }
-keep class com.twilio.video.DataTrackPublication { *; }
-keep class com.twilio.video.H264Codec { *; }
-keep class com.twilio.video.IceOptions { *; }
-keep class com.twilio.video.IceServer { *; }
-keep class com.twilio.video.IceTransportPolicy { *; }
-keep class com.twilio.video.LocalTrackStats { *; }
-keep class com.twilio.video.LogLevel { *; }
-keep class com.twilio.video.LogModule { *; }
-keep class com.twilio.video.Logger { *; }
-keep class com.twilio.video.MediaOptions { *; }
-keep class com.twilio.video.PlatformInfo { *; }
-keep class com.twilio.video.Preconditions { *; }
-keep class com.twilio.video.RemoteTrackStats { *; }
-keep class com.twilio.video.StatsListener { *; }
-keep class com.twilio.video.ThreadChecker { *; }
-keep class com.twilio.video.TrackPublication { *; }
-keep class com.twilio.video.Util { *; }
-keep class com.twilio.video.VideoTrackPublication { *; }
-keep class com.twilio.video.Vp9Codec { *; }
-keep class com.twilio.video.AudioOptions { *; }
-keep class com.twilio.video.AudioSink { *; }
-keep class com.twilio.video.AudioTrack { *; }
-keep class com.twilio.video.G722Codec { *; }
-keep class com.twilio.video.IsacCodec { *; }
-keep class com.twilio.video.JniUtils { *; }
-keep class com.twilio.video.OpusCodec { *; }
-keep class com.twilio.video.PcmaCodec { *; }
-keep class com.twilio.video.PcmuCodec { *; }
-keep class com.twilio.video.Track { *; }
-keep class com.twilio.video.AudioSinkProxy { *; }
-keep class com.twilio.video.Participant { *; }
-keep class com.twilio.video.AudioDevice { *; }
-keep class com.twilio.video.AudioDeviceCapturer { *; }
-keep class com.twilio.video.AudioDeviceContext { *; }
-keep class com.twilio.video.AudioDeviceRenderer { *; }
-keep class com.twilio.video.Camera2Capturer { *; }
-keep class com.twilio.video.CameraCapturer { *; }
-keep class com.twilio.video.CameraCapturerFormatProvider { *; }
-keep class com.twilio.video.DefaultAudioDevice { *; }
-keep class com.twilio.video.EglBaseProvider { *; }
-keep class com.twilio.video.I420Frame { *; }
-keep class com.twilio.video.TestUtils { *; }
-keep class com.twilio.video.VideoCapturerListenerAdapter { *; }
-keep class com.twilio.video.VideoFrame { *; }
-keep class com.twilio.video.VideoRenderer { *; }
-keep class com.twilio.video.VideoScaleType { *; }
-keep class com.twilio.video.VideoTextureView { *; }
-keep class com.twilio.video.VideoTrack { *; }
-keep class com.twilio.video.VideoView { *; }
-keep class com.twilio.video.AccessedByNative { *; }
-keep class com.twilio.video.AspectRatio { *; }
-keep class com.twilio.video.AudioCodec { *; }
-keep class com.twilio.video.AudioDeviceProxy { *; }
-keep class com.twilio.video.AudioFormat { *; }
-keep class com.twilio.video.BandwidthProfileMode { *; }
-keep class com.twilio.video.BandwidthProfileOptions { *; }
-keep class com.twilio.video.ConnectOptions { *; }
-keep class com.twilio.video.EncodingParameters { *; }
-keep class com.twilio.video.IceCandidatePairState { *; }
-keep class com.twilio.video.IceCandidatePairStats { *; }
-keep class com.twilio.video.IceCandidateStats { *; }
-keep class com.twilio.video.LocalAudioTrack { *; }
-keep class com.twilio.video.LocalAudioTrackPublication { *; }
-keep class com.twilio.video.LocalAudioTrackStats { *; }
-keep class com.twilio.video.LocalDataTrack { *; }
-keep class com.twilio.video.LocalDataTrackPublication { *; }
-keep class com.twilio.video.LocalParticipant { *; }
-keep class com.twilio.video.LocalParticipant$Listener { *; }
-keep class com.twilio.video.LocalTrackPublicationOptions { *; }
-keep class com.twilio.video.LocalVideoTrack { *; }
-keep class com.twilio.video.LocalVideoTrackPublication { *; }
-keep class com.twilio.video.LocalVideoTrackStats { *; }
-keep class com.twilio.video.MediaFactory { *; }
-keep class com.twilio.video.NetworkQualityConfiguration { *; }
-keep class com.twilio.video.NetworkQualityLevel { *; }
-keep class com.twilio.video.NetworkQualityVerbosity { *; }
-keep class com.twilio.video.RemoteAudioTrack { *; }
-keep class com.twilio.video.RemoteAudioTrackPublication { *; }
-keep class com.twilio.video.RemoteAudioTrackStats { *; }
-keep class com.twilio.video.RemoteDataTrack { *; }
-keep class com.twilio.video.RemoteDataTrack$Listener { *; }
-keep class com.twilio.video.RemoteDataTrackPublication { *; }
-keep class com.twilio.video.RemoteParticipant { *; }
-keep class com.twilio.video.RemoteParticipant$Listener { *; }
-keep class com.twilio.video.RemoteVideoTrack { *; }
-keep class com.twilio.video.RemoteVideoTrackPublication { *; }
-keep class com.twilio.video.RemoteVideoTrackStats { *; }
-keep class com.twilio.video.Room { *; }
-keep class com.twilio.video.Room$Listener { *; }
-keep class com.twilio.video.StatsReport { *; }
-keep class com.twilio.video.TrackPriority { *; }
-keep class com.twilio.video.TrackSwitchOffMode { *; }
-keep class com.twilio.video.TwilioException { *; }
-keep class com.twilio.video.Video { *; }
-keep class com.twilio.video.Video$NetworkChangeEvent { *; }
-keep class com.twilio.video.VideoBandwidthProfileOptions { *; }
-keep class com.twilio.video.VideoCapturer { *; }
-keep class com.twilio.video.VideoCapturerDelegate
-keep class com.twilio.video.VideoCapturerDelegate$NativeObserver { *; }
-keepclassmembernames class com.twilio.video.VideoCapturerDelegate { *; }
-keep class com.twilio.video.VideoCodec { *; }
-keep class com.twilio.video.VideoConstraints { *; }
-keep class com.twilio.video.VideoDimensions { *; }
-keep class com.twilio.video.VideoFormat { *; }
-keep class com.twilio.video.VideoPixelFormat { *; }
-keep class com.twilio.video.Vp8Codec { *; }
