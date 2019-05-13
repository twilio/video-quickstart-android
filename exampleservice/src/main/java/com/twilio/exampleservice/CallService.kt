package com.twilio.exampleservice

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.app.ActivityCompat
import android.util.Log
import com.twilio.video.*

/**
 * Android Service to maintain a Twilio Room connection outside of an Activity.
 * See [VideoActivity] for more info on how to use this service.
 */
class CallService : Service(), CallServiceAPI {

    //region WebRTC / Twilio dependencies
    private var room: Room? = null
    private var localParticipant: LocalParticipant? = null
    private var localAudioTrack: LocalAudioTrack? = null
    private var localVideoTrack: LocalVideoTrack? = null
    private var participantIdentity: String? = null
    private var callListener: CallListener? = null

    private var previousAudioMode = 0
    private var previousMicrophoneMute = false

    private val audioCodec: AudioCodec = OpusCodec()
    private val videoCodec: VideoCodec = Vp9Codec()
    private val encodingParameters: EncodingParameters = EncodingParameters(0, 0)
    //endregion

    //region Service Binding
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): CallServiceAPI {
            return this@CallService
        }
    }
    //endregion

    //region Service Lifecycle
    override fun onCreate() {
        super.onCreate()

        //Needed for setting/abandoning audio focus during call
        audioManager.isSpeakerphoneOn = true
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d("call-service", "onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    //endregion

    //region Internal Callback Implementation
    private var roomListener: Room.Listener = object : Room.Listener {
        override fun onConnected(room: Room) {
            localParticipant = room.localParticipant
            room.remoteParticipants.firstOrNull()?.let { addRemoteParticipant(it) }

            callListener?.onConnected(room.name)
        }

        override fun onConnectFailure(room: Room, twilioException: TwilioException) {
            configureAudio(false)
            callListener?.onConnectFailure(twilioException.explanation)
        }

        override fun onDisconnected(room: Room, twilioException: TwilioException?) {
            localParticipant = null
            this@CallService.room = null
            configureAudio(false)

            callListener?.onDisconnected(room.name, twilioException?.explanation, cameraCapturer)
        }

        override fun onParticipantConnected(room: Room, remoteParticipant: RemoteParticipant) {
            addRemoteParticipant(remoteParticipant)
        }

        override fun onParticipantDisconnected(room: Room, remoteParticipant: RemoteParticipant) {
            removeRemoteParticipant(remoteParticipant)
        }

        override fun onRecordingStarted(room: Room) {
            //no-op
        }

        override fun onReconnected(room: Room) {
            //no-op
        }

        override fun onRecordingStopped(room: Room) {
            //no-op
        }

        override fun onReconnecting(room: Room, twilioException: TwilioException) {
            //no-op
        }

    }
    //endregion

    //region Public API via Service Binding (CallServiceAPI)
    override val cameraCapturer by lazy {
        CameraCapturerWrapper(cameraCapturerCompat)
    }

    override fun getLocalAudioTrack(): LocalAudioTrack? {
        return localAudioTrack
    }

    override fun getLocalVideoTrack(): LocalVideoTrack? {
        return localVideoTrack
    }

    override fun disconnectFromRoom() {
        localVideoTrack?.let { localParticipant?.unpublishTrack(it) }
        localVideoTrack?.release()
        localVideoTrack = null
        localAudioTrack?.release()
        localAudioTrack = null

        room?.disconnect()
        room = null
    }

    override fun initializeLocalVideoTrack(localVideoView: VideoRenderer) {
        Log.d("call-service", "Initialize Local Video Track")
        ensureAudioAndVideoTracks()
        localVideoTrack?.addRenderer(localVideoView)
        localVideoTrack?.let { localParticipant?.publishTrack(it) }
        localParticipant?.setEncodingParameters(encodingParameters)
    }

    override fun restoreCallAfterBinding(localVideoView: VideoRenderer, callListener: CallListener) {

        Log.d("call-service", "Restore Call After Binding")
        this.callListener = callListener
        ensureAudioAndVideoTracks()
        localVideoTrack?.addRenderer(localVideoView)
        localVideoTrack?.let { localParticipant?.publishTrack(it) }
        localParticipant?.setEncodingParameters(encodingParameters)

        // Rebind the Remote Views (if any)
        room?.remoteParticipants?.firstOrNull()?.let {
            addRemoteParticipant(it)
        }

        sendServiceToBackgroundAndRemoveNotification()
    }

    override fun isCallInProgress(): Boolean {
        return room != null && room?.state != Room.State.DISCONNECTED
    }

    // The containing activity is being closed, but we want the call to continue
    override fun minimize() {
        Log.d("call-service", "Minimize")
        // If there's no room/call in progress, there's nothing to do.
        if (room == null) {
            return
        }

        // In a minimized state video feeds must be stripped from their VideoView.
        localVideoTrack?.let { localParticipant?.unpublishTrack(it) }
        localVideoTrack?.release()
        localVideoTrack = null

        // Can't call back anymore, we're switching to headless mode (no UI)
        callListener = null

        sendCallServiceToForeground()
    }

    override fun recordingAndCameraPermissionsGranted() {
        if (ensurePermissionsGranted()) {
            ensureAudioAndVideoTracks()
        }
    }

    override fun connectToRoom(accessToken: String,
                               roomName: String,
                               callListener: CallListener

    ) {

        if (!ensurePermissionsGranted()) {
            return
        }

        this.callListener = callListener

        ensureAudioAndVideoTracks()
        configureAudio(true)

        val connectOptionsBuilder = ConnectOptions.Builder(accessToken)
                .roomName(roomName)

        // Add local audio track to connect options to share with participants.
        localAudioTrack?.let { connectOptionsBuilder.audioTracks(listOf(it)) }

        // Add local video track to connect options to share with participants.
        localVideoTrack?.let { connectOptionsBuilder.videoTracks(listOf(it)) }

        // Set the preferred audio and video codec for media.
        connectOptionsBuilder.preferAudioCodecs(listOf(audioCodec))
        connectOptionsBuilder.preferVideoCodecs(listOf(videoCodec))

        // Set the sender side encoding parameters.
        connectOptionsBuilder.encodingParameters(encodingParameters)

        room = Video.connect(this, connectOptionsBuilder.build(), this.roomListener)
    }

    override fun onActivityIsStopping() {
        // If this local video track is being shared in a Room, remove from local
        // participant before releasing the video track. Participants will be notified that
        // the track has been removed.
        localVideoTrack?.let { localParticipant?.unpublishTrack(it) }

        // Release the local video track before going in the background. This ensures that the
        // camera can be used by other applications while this app is in the background.
        localVideoTrack?.release()
        localVideoTrack = null

        if (isCallInProgress()) {
            sendCallServiceToForeground()
        }
    }
    //endregion

    //region Private API
    private fun ensurePermissionsGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }


    private fun removeRemoteParticipant(remoteParticipant: RemoteParticipant) {
        if (remoteParticipant.identity != participantIdentity) {
            return
        }


        // Remove participant renderer
        remoteParticipant.remoteVideoTracks.firstOrNull()?.let { remoteVideoTrackPublication ->
            if (remoteVideoTrackPublication.isTrackSubscribed) {
                remoteVideoTrackPublication.remoteVideoTrack?.let {
                    removeRemoteParticipantVideo(remoteParticipant.identity, it)
                }
            }
        }
    }

    private fun addRemoteParticipant(remoteParticipant: RemoteParticipant) {
        participantIdentity = remoteParticipant.identity

        // Add participant renderer
        remoteParticipant.remoteVideoTracks.firstOrNull()?.let { remoteVideoTrackPublication ->
            if (remoteVideoTrackPublication.isTrackSubscribed) {
                remoteVideoTrackPublication.remoteVideoTrack?.let { addRemoteParticipantVideo(it) }
            }
        }

        remoteParticipant.setListener(participantListener)
    }


    private fun removeRemoteParticipantVideo(participantIdentity: String, videoTrack: VideoTrack) {
        callListener?.onParticipantDisconnected(participantIdentity, videoTrack, cameraCapturer)
    }

    //Set primary view as renderer for participant video track
    private fun addRemoteParticipantVideo(videoTrack: VideoTrack) {
        callListener?.onParticipantConnected(videoTrack, cameraCapturer)
    }

    private val cameraCapturerCompat by lazy {
        CameraCapturerCompat(this, getAvailableCameraSource())
    }

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val focusRequest: AudioFocusRequest? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { }
                    .build()
        } else {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocusCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocusCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        } else {
            audioManager.abandonAudioFocus { }
        }
    }

    private fun configureAudio(enable: Boolean) {
        with(audioManager) {
            if (enable) {
                previousAudioMode = audioManager.mode
                // Request audio focus before making any device switch
                requestAudioFocusCompat()
                /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */
                mode = AudioManager.MODE_IN_COMMUNICATION
                /*
             * Always disable microphone mute during a WebRTC call.
             */
                previousMicrophoneMute = isMicrophoneMute
                isMicrophoneMute = false
            } else {
                mode = previousAudioMode
                abandonAudioFocusCompat()
                isMicrophoneMute = previousMicrophoneMute
            }
        }
    }

    private fun ensureAudioAndVideoTracks() {
        Log.d("call-service", "createAudioAndVideoTracks")
        if (localAudioTrack == null) {
            // Share your microphone
            localAudioTrack = LocalAudioTrack.create(this, true)
        }

        if (localVideoTrack == null) {
            // Share your camera
            localVideoTrack = LocalVideoTrack.create(this,
                    true,
                    cameraCapturerCompat.videoCapturer)
        }
    }


    private fun getAvailableCameraSource(): CameraCapturer.CameraSource {
        return if (CameraCapturer.isSourceAvailable(CameraCapturer.CameraSource.FRONT_CAMERA))
            CameraCapturer.CameraSource.FRONT_CAMERA
        else
            CameraCapturer.CameraSource.BACK_CAMERA
    }

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    @Suppress("DEPRECATION")
    private fun sendCallServiceToForeground() {
        val CHANNEL_ID = "WebRTCCall"
        val pendingIntent: PendingIntent =
                Intent(this, VideoActivity::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(this, 0, notificationIntent, 0)
                }

        @Suppress("DEPRECATION") //for the NotificationBuilder < API26 ctor
        val notificationBuilder: Notification.Builder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                    NotificationChannel(
                            CHANNEL_ID,
                            "WebRTC Call",
                            NotificationManager.IMPORTANCE_DEFAULT
                    )
            )
            notificationBuilder = Notification.Builder(this, CHANNEL_ID)
        } else notificationBuilder = Notification.Builder(this)


        val notification = notificationBuilder
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.video_call))
                .setSmallIcon(R.drawable.ic_call_end_white_24dp)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.call_in_progress))
                .build()


        startForeground(666, notification)
    }

    private fun sendServiceToBackgroundAndRemoveNotification() {
        Log.d("call-service", "sendServiceToBackground")
        stopForeground(true)
    }

    //endregion

    //region RemoteParticipant events listener
    private val participantListener = object : RemoteParticipant.Listener {
        private val TAG = "md-pListener"
        override fun onAudioTrackPublished(remoteParticipant: RemoteParticipant,
                                           remoteAudioTrackPublication: RemoteAudioTrackPublication) {
            Log.i(TAG, "onAudioTrackPublished: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteAudioTrackPublication: sid=${remoteAudioTrackPublication.trackSid}, " +
                    "enabled=${remoteAudioTrackPublication.isTrackEnabled}, " +
                    "subscribed=${remoteAudioTrackPublication.isTrackSubscribed}, " +
                    "name=${remoteAudioTrackPublication.trackName}]")
        }

        override fun onAudioTrackUnpublished(remoteParticipant: RemoteParticipant,
                                             remoteAudioTrackPublication: RemoteAudioTrackPublication) {
            Log.i(TAG, "onAudioTrackUnpublished: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteAudioTrackPublication: sid=${remoteAudioTrackPublication.trackSid}, " +
                    "enabled=${remoteAudioTrackPublication.isTrackEnabled}, " +
                    "subscribed=${remoteAudioTrackPublication.isTrackSubscribed}, " +
                    "name=${remoteAudioTrackPublication.trackName}]")
        }

        override fun onDataTrackPublished(remoteParticipant: RemoteParticipant,
                                          remoteDataTrackPublication: RemoteDataTrackPublication) {
            Log.i(TAG, "onDataTrackPublished: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteDataTrackPublication: sid=${remoteDataTrackPublication.trackSid}, " +
                    "enabled=${remoteDataTrackPublication.isTrackEnabled}, " +
                    "subscribed=${remoteDataTrackPublication.isTrackSubscribed}, " +
                    "name=${remoteDataTrackPublication.trackName}]")
        }

        override fun onDataTrackUnpublished(remoteParticipant: RemoteParticipant,
                                            remoteDataTrackPublication: RemoteDataTrackPublication) {
            Log.i(TAG, "onDataTrackUnpublished: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteDataTrackPublication: sid=${remoteDataTrackPublication.trackSid}, " +
                    "enabled=${remoteDataTrackPublication.isTrackEnabled}, " +
                    "subscribed=${remoteDataTrackPublication.isTrackSubscribed}, " +
                    "name=${remoteDataTrackPublication.trackName}]")
        }

        override fun onVideoTrackPublished(remoteParticipant: RemoteParticipant,
                                           remoteVideoTrackPublication: RemoteVideoTrackPublication) {
            Log.i(TAG, "onVideoTrackPublished: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteVideoTrackPublication: sid=${remoteVideoTrackPublication.trackSid}, " +
                    "enabled=${remoteVideoTrackPublication.isTrackEnabled}, " +
                    "subscribed=${remoteVideoTrackPublication.isTrackSubscribed}, " +
                    "name=${remoteVideoTrackPublication.trackName}]")
        }

        override fun onVideoTrackUnpublished(remoteParticipant: RemoteParticipant,
                                             remoteVideoTrackPublication: RemoteVideoTrackPublication) {
            Log.i(TAG, "onVideoTrackUnpublished: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteVideoTrackPublication: sid=${remoteVideoTrackPublication.trackSid}, " +
                    "enabled=${remoteVideoTrackPublication.isTrackEnabled}, " +
                    "subscribed=${remoteVideoTrackPublication.isTrackSubscribed}, " +
                    "name=${remoteVideoTrackPublication.trackName}]")
        }

        override fun onAudioTrackSubscribed(remoteParticipant: RemoteParticipant,
                                            remoteAudioTrackPublication: RemoteAudioTrackPublication,
                                            remoteAudioTrack: RemoteAudioTrack) {
            Log.i(TAG, "onAudioTrackSubscribed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteAudioTrack: enabled=${remoteAudioTrack.isEnabled}, " +
                    "playbackEnabled=${remoteAudioTrack.isPlaybackEnabled}, " +
                    "name=${remoteAudioTrack.name}]")
        }

        override fun onAudioTrackUnsubscribed(remoteParticipant: RemoteParticipant,
                                              remoteAudioTrackPublication: RemoteAudioTrackPublication,
                                              remoteAudioTrack: RemoteAudioTrack) {
            Log.i(TAG, "onAudioTrackUnsubscribed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteAudioTrack: enabled=${remoteAudioTrack.isEnabled}, " +
                    "playbackEnabled=${remoteAudioTrack.isPlaybackEnabled}, " +
                    "name=${remoteAudioTrack.name}]")

        }

        override fun onAudioTrackSubscriptionFailed(remoteParticipant: RemoteParticipant,
                                                    remoteAudioTrackPublication: RemoteAudioTrackPublication,
                                                    twilioException: TwilioException) {
            Log.i(TAG, "onAudioTrackSubscriptionFailed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteAudioTrackPublication: sid=${remoteAudioTrackPublication.trackSid}, " +
                    "name=${remoteAudioTrackPublication.trackName}]" +
                    "[TwilioException: code=${twilioException.code}, " +
                    "message=${twilioException.message}]")
        }

        override fun onDataTrackSubscribed(remoteParticipant: RemoteParticipant,
                                           remoteDataTrackPublication: RemoteDataTrackPublication,
                                           remoteDataTrack: RemoteDataTrack) {
            Log.i(TAG, "onDataTrackSubscribed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteDataTrack: enabled=${remoteDataTrack.isEnabled}, " +
                    "name=${remoteDataTrack.name}]")
        }

        override fun onDataTrackUnsubscribed(remoteParticipant: RemoteParticipant,
                                             remoteDataTrackPublication: RemoteDataTrackPublication,
                                             remoteDataTrack: RemoteDataTrack) {
            Log.i(TAG, "onDataTrackUnsubscribed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteDataTrack: enabled=${remoteDataTrack.isEnabled}, " +
                    "name=${remoteDataTrack.name}]")
        }

        override fun onDataTrackSubscriptionFailed(remoteParticipant: RemoteParticipant,
                                                   remoteDataTrackPublication: RemoteDataTrackPublication,
                                                   twilioException: TwilioException) {
            Log.i(TAG, "onDataTrackSubscriptionFailed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteDataTrackPublication: sid=${remoteDataTrackPublication.trackSid}, " +
                    "name=${remoteDataTrackPublication.trackName}]" +
                    "[TwilioException: code=${twilioException.code}, " +
                    "message=${twilioException.message}]")
        }

        override fun onVideoTrackSubscribed(remoteParticipant: RemoteParticipant,
                                            remoteVideoTrackPublication: RemoteVideoTrackPublication,
                                            remoteVideoTrack: RemoteVideoTrack) {
            Log.i(TAG, "onVideoTrackSubscribed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteVideoTrack: enabled=${remoteVideoTrack.isEnabled}, " +
                    "name=${remoteVideoTrack.name}]")
            addRemoteParticipantVideo(remoteVideoTrack)
        }

        override fun onVideoTrackUnsubscribed(remoteParticipant: RemoteParticipant,
                                              remoteVideoTrackPublication: RemoteVideoTrackPublication,
                                              remoteVideoTrack: RemoteVideoTrack) {
            Log.i(TAG, "onVideoTrackUnsubscribed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteVideoTrack: enabled=${remoteVideoTrack.isEnabled}, " +
                    "name=${remoteVideoTrack.name}]")
            removeRemoteParticipantVideo(remoteParticipant.identity, remoteVideoTrack)
        }

        override fun onVideoTrackSubscriptionFailed(remoteParticipant: RemoteParticipant,
                                                    remoteVideoTrackPublication: RemoteVideoTrackPublication,
                                                    twilioException: TwilioException) {
            Log.i(TAG, "onVideoTrackSubscriptionFailed: " +
                    "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                    "[RemoteVideoTrackPublication: sid=${remoteVideoTrackPublication.trackSid}, " +
                    "name=${remoteVideoTrackPublication.trackName}]" +
                    "[TwilioException: code=${twilioException.code}, " +
                    "message=${twilioException.message}]")
        }

        override fun onAudioTrackEnabled(remoteParticipant: RemoteParticipant,
                                         remoteAudioTrackPublication: RemoteAudioTrackPublication) {
        }

        override fun onVideoTrackEnabled(remoteParticipant: RemoteParticipant,
                                         remoteVideoTrackPublication: RemoteVideoTrackPublication) {
        }

        override fun onVideoTrackDisabled(remoteParticipant: RemoteParticipant,
                                          remoteVideoTrackPublication: RemoteVideoTrackPublication) {
        }

        override fun onAudioTrackDisabled(remoteParticipant: RemoteParticipant,
                                          remoteAudioTrackPublication: RemoteAudioTrackPublication) {
        }
    }
//endregion
}

