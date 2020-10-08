package com.twilio.video.examples.examplecustomaudiodevice

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.koushikdutta.ion.Ion
import com.twilio.video.examples.examplecustomaudiodevice.dialog.Dialog
import com.twilio.video.*
import java.util.*

class CustomAudioDeviceActivity : AppCompatActivity() {
    /*
         * Access token used to connect. This field will be set either from the console generated token
         * or the request to the token server.
         */
    private var accessToken: String? = null

    /*
     * A Room represents communication between a local participant and one or more participants.
     */
    private var room: Room? = null

    /*
     * Android application UI elements
     */
    private var localAudioTrack: LocalAudioTrack? = null
    private var connectActionFab: FloatingActionButton? = null
    private var inputSwitchFab: FloatingActionButton? = null
    private var customAudioDeviceStatusText: TextView? = null
    private var connectDialog: AlertDialog? = null
    private var audioManager: AudioManager? = null
    private var previousAudioMode = 0
    private var previousMicrophoneMute = false
    private var disconnectedFromOnDestroy = false
    private val isSpeakerPhoneEnabled = true
    var fileAndMicAudioDevice: FileAndMicAudioDevice? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_audio_device)

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */volumeControlStream = AudioManager.STREAM_VOICE_CALL

        /*
         * Needed for setting/abandoning audio focus during call
         */audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager!!.isSpeakerphoneOn = isSpeakerPhoneEnabled

        /*
         * Check microphone permissions. Needed in Android M.
         */if (!checkPermissionForMicrophone()) {
            requestPermissionForMicrophone()
        } else {
            /*
             * Create custom audio device FileAndMicAudioDevice and set the audio device
             */
            fileAndMicAudioDevice = FileAndMicAudioDevice(applicationContext)
            Video.setAudioDevice(fileAndMicAudioDevice!!)
            createAudioTrack()
            setAccessToken()
        }

        /*
         * Set the initial state of the UI
         */initializeUI()
    }

    override fun onResume() {
        super.onResume()

        /*
         * Route audio through cached value.
         */audioManager!!.isSpeakerphoneOn = isSpeakerPhoneEnabled
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        if (room?.state != Room.State.DISCONNECTED) {
             room?.disconnect()
        }

        /*
         * Release the local audio track ensuring any memory allocated to audio
         * is freed.
         */if (localAudioTrack != null) {
            localAudioTrack!!.release()
            localAudioTrack = null
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == MIC_PERMISSION_REQUEST_CODE) {
            var micPermissionGranted = true
            for (grantResult in grantResults) {
                micPermissionGranted = micPermissionGranted and (grantResult == PackageManager.PERMISSION_GRANTED)
            }
            if (micPermissionGranted) {
                createAudioTrack()
                setAccessToken()
            } else {
                Toast.makeText(this,
                        R.string.permissions_needed,
                        Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setAccessToken() {
        if (!BuildConfig.USE_TOKEN_SERVER) {
            /*
             * OPTION 1 - Generate an access token from the getting started portal
             * https://www.twilio.com/console/video/dev-tools/testing-tools and add
             * the variable TWILIO_ACCESS_TOKEN setting it equal to the access token
             * string in your local.properties file.
             */
            accessToken = TWILIO_ACCESS_TOKEN
        } else {
            /*
             * OPTION 2 - Retrieve an access token from your own web app.
             * Add the variable ACCESS_TOKEN_SERVER assigning it to the url of your
             * token server and the variable USE_TOKEN_SERVER=true to your
             * local.properties file.
             */
            retrieveAccessTokenfromServer()
        }
    }

    private fun connectToRoom(roomName: String) {
        configureAudio(true)
        val connectOptionsBuilder = ConnectOptions.Builder(accessToken!!)
                .roomName(roomName)

        /*
         * Add local audio track to connect options to share with participants.
         */if (localAudioTrack != null) {
            connectOptionsBuilder
                    .audioTracks(listOf(localAudioTrack))
        }
        room = Video.connect(this, connectOptionsBuilder.build(), roomListener())
        setDisconnectAction()
    }

    private fun retrieveAccessTokenfromServer() {
        Ion.with(this)
                .load(String.format("%s?identity=%s", ACCESS_TOKEN_SERVER,
                        UUID.randomUUID().toString()))
                .asString()
                .setCallback { e: Exception?, token: String? ->
                    if (e == null) {
                        accessToken = token
                    } else {
                        Toast.makeText(this@CustomAudioDeviceActivity,
                                R.string.error_retrieving_access_token, Toast.LENGTH_LONG)
                                .show()
                    }
                }
    }

    private fun checkPermissionForMicrophone(): Boolean {
        val resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return resultMic == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionForMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG).show()
        } else {
            ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.RECORD_AUDIO),
                    MIC_PERMISSION_REQUEST_CODE)
        }
    }

    private fun createAudioTrack() {
        // Share your microphone
        localAudioTrack = LocalAudioTrack.create(this, true, LOCAL_AUDIO_TRACK_NAME)
    }

    private fun configureAudio(enable: Boolean) {
        if (enable) {
            previousAudioMode = audioManager!!.mode
            // Request audio focus before making any device switch
            requestAudioFocus()
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */audioManager!!.mode = AudioManager.MODE_IN_COMMUNICATION
            /*
             * Always disable microphone mute during a WebRTC call.
             */previousMicrophoneMute = audioManager!!.isMicrophoneMute
            audioManager!!.isMicrophoneMute = false
        } else {
            audioManager!!.mode = previousAudioMode
            audioManager!!.abandonAudioFocus(null)
            audioManager!!.isMicrophoneMute = previousMicrophoneMute
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { i: Int -> }
                    .build()
            audioManager!!.requestAudioFocus(focusRequest)
        } else {
            audioManager!!.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    /*
     * Room events listener
     */
    private fun roomListener(): Room.Listener {
        return object : Room.Listener {
            override fun onConnected(room: Room) {
                customAudioDeviceStatusText!!.text = String.format("Connected to %s", room.name)
                title = room.name
                if (hasNecessaryParticipants(room)) {
                    applyFabState(inputSwitchFab, true)
                } else {
                    applyFabState(inputSwitchFab, false)
                }
            }

            override fun onReconnecting(room: Room, twilioException: TwilioException) {
                customAudioDeviceStatusText!!.text = String.format("Reconnecting to %s", room.name)
            }

            override fun onReconnected(room: Room) {
                customAudioDeviceStatusText!!.text = String.format("Connected to %s", room.name)
            }

            override fun onConnectFailure(room: Room, e: TwilioException) {
                customAudioDeviceStatusText!!.text = getString(R.string.connect_failed)
                configureAudio(false)
                initializeUI()
            }

            override fun onDisconnected(room: Room, e: TwilioException?) {
                customAudioDeviceStatusText!!.text = String.format("Disconnected from %s", room.name)
                this@CustomAudioDeviceActivity.room = null
                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy) {
                    configureAudio(false)
                    initializeUI()
                }
            }

            override fun onParticipantConnected(room: Room, remoteParticipant: RemoteParticipant) {
                if (hasNecessaryParticipants(room)) {
                    customAudioDeviceStatusText!!.text = getString(R.string.status_capture_ready)
                    applyFabState(inputSwitchFab, true)
                }
            }

            override fun onParticipantDisconnected(room: Room, remoteParticipant: RemoteParticipant) {
                if (!hasNecessaryParticipants(room)) {
                    customAudioDeviceStatusText!!.text = getString(R.string.status_two_particpants_needed)
                    applyFabState(inputSwitchFab, false)
                }
            }

            override fun onRecordingStarted(room: Room) {
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStarted")
            }

            override fun onRecordingStopped(room: Room) {
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStopped")
            }
        }
    }

    fun hasNecessaryParticipants(room: Room): Boolean {
        if (room.remoteParticipants.size > 1) {
            throw RuntimeException(String.format(UNSUPPORTED_PARTICIPANT_COUNT_MSG, MAX_PARTICIPANTS))
        }
        return room.remoteParticipants.size > 0
    }

    private fun initializeUI() {
        customAudioDeviceStatusText = findViewById(R.id.status_text)
        connectActionFab = findViewById(R.id.connect_action_fab)
        connectActionFab!!.setImageDrawable(ContextCompat.getDrawable(this,
                android.R.drawable.sym_call_outgoing))
        connectActionFab!!.show()
        connectActionFab!!.setOnClickListener(connectActionClickListener())
        inputSwitchFab = findViewById(R.id.input_switch_fab)
        inputSwitchFab!!.setOnClickListener(inputSwitchActionFabClickListener())
        inputSwitchFab!!.hide()
    }

    /*
     * Creates a connect UI dialog
     */
    private fun showConnectDialog() {
        val roomEditText = EditText(this)
        connectDialog = Dialog.createConnectDialog(roomEditText,
                connectClickListener(roomEditText),
                cancelConnectDialogClickListener(),
                this)
        connectDialog!!.show()
    }

    /*
     * The actions performed during disconnect.
     */
    private fun setDisconnectAction() {
        connectActionFab!!.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_end_white_24px))
        connectActionFab!!.show()
        connectActionFab!!.setOnClickListener(disconnectClickListener())
        inputSwitchFab!!.show()
    }

    private fun connectClickListener(roomEditText: EditText): DialogInterface.OnClickListener {
        return DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
            /*
             * Connect to room
             */connectToRoom(roomEditText.text.toString())
        }
    }

    private fun disconnectClickListener(): View.OnClickListener {
        return View.OnClickListener { v: View? ->
            /*
             * Disconnect from room
             */if (room != null) {
            room!!.disconnect()
        }
            initializeUI()
        }
    }

    private fun connectActionClickListener(): View.OnClickListener {
        return View.OnClickListener { v: View? -> showConnectDialog() }
    }

    private fun cancelConnectDialogClickListener(): DialogInterface.OnClickListener {
        return DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
            initializeUI()
            connectDialog!!.dismiss()
        }
    }

    private fun applyFabState(button: FloatingActionButton?, enabled: Boolean) {
        if (enabled) button!!.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_pause_white_24dp)) else button!!.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_play))
    }

    private fun inputSwitchActionFabClickListener(): View.OnClickListener {
        return View.OnClickListener { v: View? ->
            val enable = fileAndMicAudioDevice!!.isMusicPlaying
            applyFabState(inputSwitchFab, !enable)
            fileAndMicAudioDevice!!.switchInput(!enable)
        }
    }

    companion object {
        private const val MAX_PARTICIPANTS = 2
        private const val MIC_PERMISSION_REQUEST_CODE = 5
        private const val TAG = "CustomAudioDevice"
        private const val UNSUPPORTED_PARTICIPANT_COUNT_MSG = "This example only supports %d participants"

        /*
     * Audio and video tracks can be created with names. This feature is useful for categorizing
     * tracks of participants. For example, if one participant publishes a video track with
     * ScreenCapturer and CameraCapturer with the names "screen" and "camera" respectively then
     * other participants can use RemoteVideoTrack#getName to determine which video track is
     * produced from the other participant's screen or camera.
     */
        private const val LOCAL_AUDIO_TRACK_NAME = "mic"

        /*
     * You must provide a Twilio Access Token to connect to the Video service
     */
        private const val TWILIO_ACCESS_TOKEN = BuildConfig.TWILIO_ACCESS_TOKEN
        private const val ACCESS_TOKEN_SERVER = BuildConfig.TWILIO_ACCESS_TOKEN_SERVER
    }
}
