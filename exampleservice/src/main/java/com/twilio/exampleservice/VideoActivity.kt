package com.twilio.exampleservice

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.twilio.video.VideoRenderer
import com.twilio.video.VideoTrack
import kotlinx.android.synthetic.main.activity_video.*
import kotlinx.android.synthetic.main.content_video.*

class VideoActivity : AppCompatActivity() {
    private val CAMERA_MIC_PERMISSION_REQUEST_CODE = 1
    private val TWILIO_ACCESS_TOKEN = BuildConfig.TWILIO_ACCESS_TOKEN

    private var service: CallService? = null
    private var bound = false

    private var alertDialog: AlertDialog? = null
    private var alertDialogBack: AlertDialog? = null

    private var disconnectedFromOnDestroy = false
    private var isSpeakerPhoneEnabled = true

    private lateinit var accessToken: String
    private lateinit var localVideoView: VideoRenderer

    //region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)
        localVideoView = primaryVideoView

        // Enable changing the volume using the up/down keys during a conversation
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        audioManager.isSpeakerphoneOn = true
        setAccessToken()
        requestPermissionForCameraAndMicrophone()
        initializeUI()
    }

    override fun onStart() {
        super.onStart()
        // Bind to CallService (continues in serviceConnection callback)
        Intent(this, CallService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onBackPressed() {
        if (isCallInProgress()) {
            askBeforeGoingBack()
        } else {
            minimizeOngoingCall()
        }
    }

    override fun onStop() {
        service?.onActivityIsStopping()
        unbindService(serviceConnection)
        bound = false
        super.onStop()
    }

    override fun onDestroy() {
        alertDialog?.dismiss()
        alertDialogBack?.dismiss()

        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        // This method is only called when you don't have a toolbar menu
        // See this for more info: https://stackoverflow.com/questions/18780112/onsupportnavigateup-not-called
        onBackPressed()
        return true
    }
    //endregion

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            var cameraAndMicPermissionGranted = true

            for (grantResult in grantResults) {
                cameraAndMicPermissionGranted = cameraAndMicPermissionGranted and
                        (grantResult == PackageManager.PERMISSION_GRANTED)
            }

            if (cameraAndMicPermissionGranted) {
                service?.recordingAndCameraPermissionsGranted()
            } else {
                Toast.makeText(this,
                        R.string.permissions_needed,
                        Toast.LENGTH_LONG).show()
            }
        }
    }

    //region Private API
    private var serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            val binder = service as CallService.LocalBinder
            this@VideoActivity.service = binder.getService()
            bound = true
            onServiceBound()
        }
    }

    private fun onServiceBound() {
        service?.let {
            it.restoreCallAfterBinding(localVideoView, callListener)
            ensureServiceStartedDuringCall(it)
            if (it.isCallInProgress()) {
                setDisconnectAction()
            }
        }

        audioManager.isSpeakerphoneOn = isSpeakerPhoneEnabled
    }


    private fun isCallInProgress(): Boolean {
        return service != null && service!!.isCallInProgress()
    }

    private fun minimizeOngoingCall() {
        service?.let {
            if (!it.isCallInProgress()) {
                // No call in progress, stop the service.
                stopService(Intent(this, CallService::class.java))
                super.onBackPressed()
                return
            }


            // A call is in progress, tell the service to minimize it before going back
            it.minimize()
        }

        super.onBackPressed()
    }

    private fun requestPermissionForCameraAndMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.CAMERA) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG).show()
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                    CAMERA_MIC_PERMISSION_REQUEST_CODE)
        }
    }


    private val callListener = object : CallListener {
        @SuppressLint("SetTextI18n")
        override fun onDisconnected(roomName: String?, errorMessage: String?, cameraCapturer: CameraCapturerWrapper) {
            videoStatusTextView.text = "Disconnected from $roomName; reason->$errorMessage"
            reconnectingProgressBar.visibility = View.GONE

            // Only reinitialize the UI if disconnect was not called from onDestroy()
            if (!disconnectedFromOnDestroy) {
                initializeUI()
                moveLocalVideoToPrimaryView(cameraCapturer.isFrontCamera())
            }
        }

        override fun onConnectFailure(errorMessage: String?) {
            @SuppressLint("SetTextI18n")
            videoStatusTextView.text = "ConnFail->$errorMessage"
            initializeUI()
        }

        override fun onConnected(roomName: String) {
            //Ensure the service remains running if we unbind/die
            startService(Intent(this@VideoActivity, CallService::class.java))
            @SuppressLint("SetTextI18n")
            videoStatusTextView.text = "Connected to $roomName"
            title = roomName
        }

        override fun onParticipantConnected(videoTrack: VideoTrack, cameraCapturer: CameraCapturerWrapper) {
            // This app only displays video for one additional participant per Room
            if (thumbnailVideoView.visibility == View.VISIBLE) {
                Snackbar.make(connectActionFab,
                        "Multiple participants are not currently support in this UI",
                        Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()
                return
            }

            moveLocalVideoToThumbnailView(cameraCapturer.isFrontCamera())
            primaryVideoView.mirror = false
            //TODO: It would be better if we didn't need to have this VideoTrack here
            //Better: inject the view in the service via its primary API.
            videoTrack.addRenderer(primaryVideoView)
        }

        override fun onParticipantDisconnected(participantIdentity: String, videoTrack: VideoTrack, cameraCapturer: CameraCapturerWrapper) {
            @SuppressLint("SetTextI18n")
            videoStatusTextView.text = "Remote Participant ($participantIdentity) left."
            moveLocalVideoToPrimaryView(cameraCapturer.isFrontCamera())
            videoTrack.removeRenderer(primaryVideoView)
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
            this.accessToken = TWILIO_ACCESS_TOKEN
        } else {
            /*
             * OPTION 2 - Retrieve an access token from your own web app.
             * Add the variable ACCESS_TOKEN_SERVER assigning it to the url of your
             * token server and the variable USE_TOKEN_SERVER=true to your
             * local.properties file.
             */
            //TODO: see Quickstart sample
        }
    }

    private fun connectToRoom(roomName: String) {
        service?.connectToRoom(accessToken, roomName, callListener)
        setDisconnectAction()
    }

    /*
     * The initial state when there is no active room.
     */
    private fun initializeUI() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_video_call_white_24dp))
        connectActionFab.show()
        connectActionFab.setOnClickListener(connectActionClickListener())
        switchCameraActionFab.show()
        switchCameraActionFab.setOnClickListener(switchCameraClickListener())
        localVideoActionFab.show()
        localVideoActionFab.setOnClickListener(localVideoClickListener())
        muteActionFab.show()
        muteActionFab.setOnClickListener(muteClickListener())
    }

    /*
     * The actions performed during disconnect.
     */
    private fun setDisconnectAction() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_end_white_24px))
        connectActionFab.show()
        connectActionFab.setOnClickListener(disconnectClickListener())
    }

    /*
     * Creates an connect UI dialog
     */
    private fun showConnectDialog() {
        val roomEditText = EditText(this)
        alertDialog = createConnectDialog(roomEditText,
                connectClickListener(roomEditText), cancelConnectDialogClickListener(), this)
        alertDialog!!.show()
    }


    private fun connectClickListener(roomEditText: EditText): DialogInterface.OnClickListener {
        return DialogInterface.OnClickListener { _, _ ->
            /*
             * Connect to room
             */
            connectToRoom(roomEditText.text.toString())
        }
    }

    private fun disconnectClickListener(): View.OnClickListener {
        return View.OnClickListener {
            service?.disconnectFromRoom()
            initializeUI()
        }
    }

    private fun connectActionClickListener(): View.OnClickListener {
        return View.OnClickListener { showConnectDialog() }
    }

    private fun cancelConnectDialogClickListener(): DialogInterface.OnClickListener {
        return DialogInterface.OnClickListener { _, _ ->
            initializeUI()
            alertDialog!!.dismiss()
        }
    }

    private fun switchCameraClickListener(): View.OnClickListener {
        return View.OnClickListener {

            service?.cameraCapturer?.let {
                it.switchCamera()

                if (thumbnailVideoView.visibility == View.VISIBLE) {
                    thumbnailVideoView.mirror = !it.isFrontCamera()
                } else {
                    primaryVideoView.mirror = !it.isFrontCamera()
                }
            }
        }
    }

    private fun localVideoClickListener(): View.OnClickListener {
        return View.OnClickListener {
            /*
             * Enable/disable the local video track
             */
            service?.getLocalVideoTrack()?.let {
                val enable = !it.isEnabled
                it.enable(enable)
                val icon: Int
                if (enable) {
                    icon = R.drawable.ic_videocam_white_24dp
                    switchCameraActionFab.show()
                } else {
                    icon = R.drawable.ic_videocam_off_black_24dp
                    switchCameraActionFab.hide()
                }
                localVideoActionFab.setImageDrawable(
                        ContextCompat.getDrawable(this@VideoActivity, icon))
            }
        }
    }

    private fun muteClickListener(): View.OnClickListener {
        return View.OnClickListener {
            /*
             * Enable/disable the local audio track. The results of this operation are
             * signaled to other Participants in the same Room. When an audio track is
             * disabled, the audio is muted.
             */
            service?.getLocalAudioTrack()?.let {
                val enable = !it.isEnabled
                it.enable(enable)
                val icon = if (enable)
                    R.drawable.ic_mic_white_24dp
                else
                    R.drawable.ic_mic_off_black_24dp
                muteActionFab.setImageDrawable(ContextCompat.getDrawable(
                        this@VideoActivity, icon))
            }
        }
    }

    private fun askBeforeGoingBack() {
        val alertDialogBuilder = AlertDialog.Builder(this).apply {
            //            setIcon(R.drawable.ic_video_call_white_24dp)
            setTitle("Minimize Call?")
                    .setMessage("You're currently in a Telemedicine call.\n" +
                            "Do you want to minimize the call?")
            setPositiveButton("Yes, Minimize", { _, _ ->
                minimizeOngoingCall()
            })
            setNegativeButton("No, Cancel", null)
            setCancelable(false)
        }

        alertDialogBack = alertDialogBuilder.show()
    }

    private fun createConnectDialog(participantEditText: EditText,
                                    callParticipantsClickListener: DialogInterface.OnClickListener,
                                    cancelClickListener: DialogInterface.OnClickListener,
                                    context: Context): AlertDialog {
        val alertDialogBuilder = AlertDialog.Builder(context).apply {
            setIcon(R.drawable.ic_video_call_white_24dp)
            setTitle("Connect to a room")
            setPositiveButton("Connect", callParticipantsClickListener)
            setNegativeButton("Cancel", cancelClickListener)
            setCancelable(false)
        }

        setRoomNameFieldInDialog(participantEditText, alertDialogBuilder, context)

        return alertDialogBuilder.create()
    }

    @SuppressLint("RestrictedApi")
    private fun setRoomNameFieldInDialog(roomNameEditText: EditText,
                                         alertDialogBuilder: AlertDialog.Builder,
                                         context: Context) {
        roomNameEditText.hint = "room name"
        val horizontalPadding = context.resources.getDimensionPixelOffset(R.dimen.activity_horizontal_margin)
        val verticalPadding = context.resources.getDimensionPixelOffset(R.dimen.activity_vertical_margin)
        alertDialogBuilder.setView(roomNameEditText,
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                0)
    }

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private fun moveLocalVideoToPrimaryView(isUsingFrontCamera: Boolean) {
        if (thumbnailVideoView.visibility == View.VISIBLE) {
            thumbnailVideoView.visibility = View.GONE
            with(service?.getLocalVideoTrack()) {
                this?.removeRenderer(thumbnailVideoView)
                this?.addRenderer(primaryVideoView)
            }
            localVideoView = primaryVideoView
            primaryVideoView.mirror = isUsingFrontCamera
        }
    }

    private fun moveLocalVideoToThumbnailView(isUsingFrontCamera: Boolean) {
        if (thumbnailVideoView.visibility == View.GONE) {
            thumbnailVideoView.visibility = View.VISIBLE
            with(service?.getLocalVideoTrack()) {
                this?.removeRenderer(primaryVideoView)
                this?.addRenderer(thumbnailVideoView)
            }
            localVideoView = thumbnailVideoView
            thumbnailVideoView.mirror = isUsingFrontCamera
        }
    }

    private fun ensureServiceStartedDuringCall(service: CallService): Boolean {
        if (service.isCallInProgress()) {
            //ensure service stays on
            startService(Intent(this@VideoActivity, CallService::class.java))
            return true
        }

        return false
    }
    //endregion
}
