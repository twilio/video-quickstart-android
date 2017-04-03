package com.twilio.video.examples.videonotify;

import android.Manifest;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.iid.FirebaseInstanceId;
import com.twilio.video.AudioTrack;
import com.twilio.video.CameraCapturer;
import com.twilio.video.CameraCapturer.CameraSource;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalMedia;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.Media;
import com.twilio.video.Participant;
import com.twilio.video.Room;
import com.twilio.video.RoomState;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoRenderer;
import com.twilio.video.VideoTrack;
import com.twilio.video.VideoView;
import com.twilio.video.examples.videonotify.notify.api.TwilioSDKStarterAPI;
import com.twilio.video.examples.videonotify.notify.api.model.Binding;
import com.twilio.video.examples.videonotify.notify.api.model.Token;
import com.twilio.video.examples.videonotify.notify.api.model.VideoRoomNotification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.twilio.video.examples.videonotify.R.drawable.ic_phonelink_ring_white_24dp;
import static com.twilio.video.examples.videonotify.R.drawable.ic_volume_up_white_24dp;

public class VideoNotifyActivity extends AppCompatActivity {
    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1;

    /*
     * Set your SDK Starter Server URL to get an access token with Twilio Video and Twilio Notify
     * grants and register this app instance with Twilio Notify.
     *
     * The sdk-starter projects available in C#, Java, Node, PHP, Python, or Ruby here:
     * https://github.com/TwilioDevEd?q=sdk-starter
     */
    public static final String TWILIO_SDK_STARTER_SERVER_URL = "https://23e868a3.ngrok.io";

    /*
     * The notify binding type to use. FCM & GCM are supported by the Notify Service on Android
     * You can specify either by providing the String "fcm" or "gcm" respectively.
     */
    private static final String BINDING_TYPE = "fcm";

    /*
     * The notify tag used to notify others when connecting to a Video room.
     */
    private static final String BINDING_TAG = "video";
    private static final List<String> BINDING_TAGS = new ArrayList<String>() {{
        add(BINDING_TAG);
    }};

    /*
     * Intent keys used to provide information about a video notification that has been received.
     */
    public static final String ACTION_VIDEO_NOTIFICATION = "VIDEO_NOTIFICATION";
    public static final String VIDEO_NOTIFICATION_ROOM_NAME = "VIDEO_NOTIFICATION_ROOM_NAME";
    public static final String VIDEO_NOTIFICATION_BODY = "VIDEO_NOTIFICATION_BODY";

    /*
     * Token obtained from the sdk-starter /token resource
     */
    private String token;

    /*
     * Identity obtained from the sdk-starter /token resource
     */
    private String identity;

    /*
     * A Room represents communication between a local participant and one or more participants.
     */
    private Room room;

    /*
     * A VideoView receives frames from a local or remote video track and renders them
     * to an associated view.
     */
    private VideoView primaryVideoView;
    private VideoView thumbnailVideoView;

    private boolean isReceiverRegistered;
    private VideoNotificationBroadcastReceiver videoNotificationBroadcastReceiver;
    private NotificationManager notificationManager;

    /*
     * Android application UI elements
     */
    private TextView statusTextView;
    private TextView identityTextView;
    private CameraCapturer cameraCapturer;
    private LocalMedia localMedia;
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack localVideoTrack;
    private FloatingActionButton connectActionFab;
    private FloatingActionButton switchCameraActionFab;
    private FloatingActionButton localVideoActionFab;
    private FloatingActionButton muteActionFab;
    private android.support.v7.app.AlertDialog alertDialog;
    private AudioManager audioManager;
    private String participantIdentity;

    private int previousAudioMode;
    private VideoRenderer localVideoView;
    private boolean disconnectedFromOnDestroy;
    private final static String TAG = "VideoNotifyActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        primaryVideoView = (VideoView) findViewById(R.id.primary_video_view);
        thumbnailVideoView = (VideoView) findViewById(R.id.thumbnail_video_view);
        statusTextView = (TextView) findViewById(R.id.status_textview);
        identityTextView = (TextView) findViewById(R.id.identity_textview);

        connectActionFab = (FloatingActionButton) findViewById(R.id.connect_action_fab);
        switchCameraActionFab = (FloatingActionButton) findViewById(R.id.switch_camera_action_fab);
        localVideoActionFab = (FloatingActionButton) findViewById(R.id.local_video_action_fab);
        muteActionFab = (FloatingActionButton) findViewById(R.id.mute_action_fab);

        /*
         * Hide the connect button until we successfully register with Twilio Notify
         */
        connectActionFab.hide();

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);

        /*
         * Setup the broadcast receiver to be notified of video notification messages
         */
        videoNotificationBroadcastReceiver = new VideoNotificationBroadcastReceiver();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Intent intent = getIntent();

        /*
         * Check camera and microphone permissions. Needed in Android M.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else if (intent != null && intent.getAction() == ACTION_VIDEO_NOTIFICATION) {
            createLocalMedia();
            handleVideoNotificationIntent(intent);
        } else {
            createLocalMedia();
            register();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            boolean cameraAndMicPermissionGranted = true;

            for (int grantResult : grantResults) {
                cameraAndMicPermissionGranted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }

            if (cameraAndMicPermissionGranted) {
                createLocalMedia();
                register();
            } else {
                Toast.makeText(this,
                        R.string.permissions_needed,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /*
     * Called when a notification is clicked and this activity is in the background
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleVideoNotificationIntent(intent);
    }

    private void handleVideoNotificationIntent(Intent intent) {
        notificationManager.cancelAll();
        /*
         * For now we'll only handle notifications while not in a room
         */
        if (room == null) {
            String dialogRoomName = intent.getStringExtra(VIDEO_NOTIFICATION_ROOM_NAME);
            showVideoNotificationConnectDialog("Join this room", dialogRoomName);
        }
    }

    private void registerReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_VIDEO_NOTIFICATION);
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    videoNotificationBroadcastReceiver, intentFilter);
            isReceiverRegistered = true;
        }
    }

    private void unregisterReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(videoNotificationBroadcastReceiver);
        isReceiverRegistered = false;
    }

    private class VideoNotificationBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_VIDEO_NOTIFICATION)) {
                /*
                 * Handle the video notification
                 */
                handleVideoNotificationIntent(intent);
            }
        }
    }

    /*
     * Creates a connect UI dialog to handle notifications
     */
    private void showVideoNotificationConnectDialog(String title, String roomName) {
        EditText roomEditText = new EditText(this);
        roomEditText.setText(roomName);
        // Use the default color instead of the disabled color
        int currentColor = roomEditText.getCurrentTextColor();
        roomEditText.setEnabled(false);
        roomEditText.setTextColor(currentColor);
        alertDialog = createConnectDialog(title,
                roomEditText,
                videoNotificationConnectClickListener(roomEditText),
                cancelConnectDialogClickListener(),
                this);
        alertDialog.show();
    }

    @Override
    protected  void onResume() {
        super.onResume();
        registerReceiver();
        /*
         * If the local video track was removed when the app was put in the background, add it back.
         */
        if (localMedia != null && localVideoTrack == null) {
            localVideoTrack = localMedia.addVideoTrack(true, cameraCapturer);
            localVideoTrack.addRenderer(localVideoView);
        }
    }

    @Override
    protected void onPause() {
        unregisterReceiver();
        /*
         * Remove the local video track before going in the background. This ensures that the
         * camera can be used by other applications while this app is in the background.
         *
         * If this local video track is being shared in a Room, participants will be notified
         * that the track has been removed.
         */
        if (localMedia != null && localVideoTrack != null) {
            localMedia.removeVideoTrack(localVideoTrack);
            localVideoTrack = null;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        if (room != null && room.getState() != RoomState.DISCONNECTED) {
            room.disconnect();
            disconnectedFromOnDestroy = true;
        }

        /*
         * Release the local media ensuring any memory allocated to audio or video is freed.
         */
        if (localMedia != null) {
            localMedia.release();
            localMedia = null;
        }

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.speaker_menu_item:
                if (audioManager.isSpeakerphoneOn()) {
                    audioManager.setSpeakerphoneOn(false);
                    item.setIcon(ic_phonelink_ring_white_24dp);
                } else {
                    audioManager.setSpeakerphoneOn(true);
                    item.setIcon(ic_volume_up_white_24dp);
                }
                break;
        }
        return true;
    }

    private boolean checkPermissionForCameraAndMicrophone(){
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultCamera == PackageManager.PERMISSION_GRANTED &&
               resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForCameraAndMicrophone(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_MIC_PERMISSION_REQUEST_CODE);
        }
    }

    private void createLocalMedia() {
        localMedia = LocalMedia.create(this);

        // Share your microphone
        localAudioTrack = localMedia.addAudioTrack(true);

        // Share your camera
        cameraCapturer = new CameraCapturer(this, CameraSource.FRONT_CAMERA);
        localVideoTrack = localMedia.addVideoTrack(true, cameraCapturer);
        primaryVideoView.setMirror(true);
        localVideoTrack.addRenderer(primaryVideoView);
        localVideoView = primaryVideoView;
    }

    private void register() {
        if (TWILIO_SDK_STARTER_SERVER_URL.equals(getString(R.string.twilio_sdk_starter_server_url))) {
            String message = "Error: Set a valid sdk starter server url";
            Log.e(TAG, message);
            statusTextView.setText(message);
        } else {
            TwilioSDKStarterAPI.fetchToken().enqueue(new Callback<Token>() {
                @Override
                public void onResponse(Call<Token> call, Response<Token> response) {
                    if (response.isSuccess()) {
                    /*
                     * Save and display the identity
                     */
                        identity = response.body().identity;
                        identityTextView.setText(identity);

                    /*
                     * Set the access token. This will later be used to connect to a Video room
                     */
                        VideoNotifyActivity.this.token = response.body().token;

                    /*
                     * Register binding with Notify
                     */
                        bind(identity);
                    } else {
                        String message = "Fetching token failed: " + response.code() + " " + response.message();
                        Log.e(TAG, message);
                        statusTextView.setText(message);
                    }
                }

                @Override
                public void onFailure(Call<Token> call, Throwable t) {
                    String message = "Fetching token failed: " + t.getMessage();
                    Log.e(TAG, message);
                    statusTextView.setText(message);
                }
            });
        }
    }

    private void bind(final String identity) {
        /*
         * Generate an endpoint based on the new identity and the instanceID. This ensures that we
         * maintain stability of the endpoint even if the instanceID changes without the identity
         * changing.
         */
        String endpoint = identity + "@" + FirebaseInstanceId.getInstance().getId();

        /*
         * Obtain the new address based off the Firebase instance token
         */
        String address = FirebaseInstanceId.getInstance().getToken();

        final Binding binding = new Binding(identity, endpoint, address, BINDING_TYPE, BINDING_TAGS);
        TwilioSDKStarterAPI.registerBinding(binding).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccess()) {
                    statusTextView.setText("Registered with Twilio Notify");
                    /*
                     * Set the initial state of the UI
                     */
                    intializeUI();
                } else {
                    String message = "Binding registration failed: " + response.code() + " " + response.message();
                    Log.e(TAG, message);
                    statusTextView.setText(message);
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                String message = "Binding registration failed: " + t.getMessage();
                Log.e(TAG, message);
                statusTextView.setText(message);
            }
        });
    }

    private void connectToRoom(String roomName) {
        enableAudioFocus(true);
        enableVolumeControl(true);
        ConnectOptions connectOptions = new ConnectOptions.Builder(token)
                .roomName(roomName)
                .localMedia(localMedia)
                .build();
        room = Video.connect(this, connectOptions, roomListener());
        setDisconnectBehavior();
    }

    void notify(final String roomName) {
        /*
         * Use Twilio Notify to let others know you are connecting to a Room
         */
        VideoRoomNotification videoRoomNotification = new VideoRoomNotification(
                "Join " + identity + " in room",
                roomName,
                roomName,
                BINDING_TAGS);
        TwilioSDKStarterAPI.notify(videoRoomNotification).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccess()) {
                    String message = "Sending notification failed: " + response.code() + " " + response.message();
                    Log.e(TAG, message);
                    statusTextView.setText(message);
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                String message = "Sending notification failed: " + t.getMessage();
                Log.e(TAG, message);
                statusTextView.setText(message);
            }
        });
    }

    /*
     * The initial state when there is no active conversation.
     */
    private void intializeUI() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_video_call_white_24dp));
        connectActionFab.show();
        connectActionFab.setOnClickListener(connectActionClickListener());
        switchCameraActionFab.show();
        switchCameraActionFab.setOnClickListener(switchCameraClickListener());
        localVideoActionFab.show();
        localVideoActionFab.setOnClickListener(localVideoClickListener());
        muteActionFab.show();
        muteActionFab.setOnClickListener(muteClickListener());
    }

    /*
     * The behavior applied to disconnect
     */
    private void setDisconnectBehavior() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_end_white_24dp));
        connectActionFab.show();
        connectActionFab.setOnClickListener(disconnectClickListener());
    }

    /*
     * Creates a connect UI dialog
     */
    private void showConnectDialog() {
        EditText roomEditText = new EditText(this);
        String title = "Connect to a video room";
        roomEditText.setHint("room name");
        alertDialog = createConnectDialog(title,
                roomEditText,
                connectClickListener(roomEditText),
                cancelConnectDialogClickListener(),
                this);
        alertDialog.show();
    }

    /*
     * Called when participant joins the room
     */
    private void addParticipant(Participant participant) {
        /*
         * This app only displays video for one additional participant per Room
         */
        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
            Snackbar.make(connectActionFab,
                    "Rendering multiple participants not supported in this app",
                    Snackbar.LENGTH_LONG)
                    .setAction("Info", null).show();
            return;
        }
        participantIdentity = participant.getIdentity();
        statusTextView.setText("Participant "+ participantIdentity + " joined");

        /*
         * Add participant renderer
         */
        if (participant.getMedia().getVideoTracks().size() > 0) {
            addParticipantVideo(participant.getMedia().getVideoTracks().get(0));
        }

        /*
         * Start listening for participant media events
         */
        participant.getMedia().setListener(mediaListener());
    }

    /*
     * Set primary view as renderer for participant video track
     */
    private void addParticipantVideo(VideoTrack videoTrack) {
        moveLocalVideoToThumbnailView();
        primaryVideoView.setMirror(false);
        videoTrack.addRenderer(primaryVideoView);
    }

    private void moveLocalVideoToThumbnailView() {
        if (thumbnailVideoView.getVisibility() == View.GONE) {
            thumbnailVideoView.setVisibility(View.VISIBLE);
            localVideoTrack.removeRenderer(primaryVideoView);
            localVideoTrack.addRenderer(thumbnailVideoView);
            localVideoView = thumbnailVideoView;
            thumbnailVideoView.setMirror(cameraCapturer.getCameraSource() ==
                    CameraSource.FRONT_CAMERA);
        }
    }

    /*
     * Called when participant leaves the room
     */
    private void removeParticipant(Participant participant) {
        statusTextView.setText("Participant "+participant.getIdentity()+ " left.");
        if (!participant.getIdentity().equals(participantIdentity)) {
            return;
        }

        /*
         * Remove participant renderer
         */
        if (participant.getMedia().getVideoTracks().size() > 0) {
            removeParticipantVideo(participant.getMedia().getVideoTracks().get(0));
        }
        participant.getMedia().setListener(null);
        moveLocalVideoToPrimaryView();
    }

    private void removeParticipantVideo(VideoTrack videoTrack) {
        videoTrack.removeRenderer(primaryVideoView);
    }

    private void moveLocalVideoToPrimaryView() {
        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
            localVideoTrack.removeRenderer(thumbnailVideoView);
            thumbnailVideoView.setVisibility(View.GONE);
            localVideoTrack.addRenderer(primaryVideoView);
            localVideoView = primaryVideoView;
            primaryVideoView.setMirror(cameraCapturer.getCameraSource() ==
                    CameraSource.FRONT_CAMERA);
        }
    }

    /*
     * Room events listener
     */
    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                statusTextView.setText("Connected to " + room.getName());
                setTitle(room.getName());

                for (Map.Entry<String, Participant> entry : room.getParticipants().entrySet()) {
                    addParticipant(entry.getValue());
                    break;
                }
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                statusTextView.setText("Failed to connect");
            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                statusTextView.setText("Disconnected from " + room.getName());
                VideoNotifyActivity.this.room = null;
                enableAudioFocus(false);
                enableVolumeControl(false);
                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy) {
                    intializeUI();
                    moveLocalVideoToPrimaryView();
                }
            }

            @Override
            public void onParticipantConnected(Room room, Participant participant) {
                addParticipant(participant);

            }

            @Override
            public void onParticipantDisconnected(Room room, Participant participant) {
                removeParticipant(participant);
            }

            @Override
            public void onRecordingStarted(Room room) {
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
            }

            @Override
            public void onRecordingStopped(Room room) {
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
            }
        };
    }

    private Media.Listener mediaListener() {
        return new Media.Listener() {

            @Override
            public void onAudioTrackAdded(Media media, AudioTrack audioTrack) {
                statusTextView.setText("onAudioTrackAdded");
            }

            @Override
            public void onAudioTrackRemoved(Media media, AudioTrack audioTrack) {
                statusTextView.setText("onAudioTrackRemoved");
            }

            @Override
            public void onVideoTrackAdded(Media media, VideoTrack videoTrack) {
                statusTextView.setText("onVideoTrackAdded");
                addParticipantVideo(videoTrack);
            }

            @Override
            public void onVideoTrackRemoved(Media media, VideoTrack videoTrack) {
                statusTextView.setText("onVideoTrackRemoved");
                removeParticipantVideo(videoTrack);
            }

            @Override
            public void onAudioTrackEnabled(Media media, AudioTrack audioTrack) {

            }

            @Override
            public void onAudioTrackDisabled(Media media, AudioTrack audioTrack) {

            }

            @Override
            public void onVideoTrackEnabled(Media media, VideoTrack videoTrack) {

            }

            @Override
            public void onVideoTrackDisabled(Media media, VideoTrack videoTrack) {

            }
        };
    }

    private DialogInterface.OnClickListener connectClickListener(final EditText roomEditText) {
        return new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String roomName = roomEditText.getText().toString();
                /*
                 * Connect to room
                 */
                connectToRoom(roomName);
                /*
                 * Notify other participants to join the room
                 */
                VideoNotifyActivity.this.notify(roomName);
            }
        };
    }

    private DialogInterface.OnClickListener videoNotificationConnectClickListener(final EditText roomEditText) {
        return new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                /*
                 * Connect to room
                 */
                connectToRoom(roomEditText.getText().toString());
            }
        };
    }

    private View.OnClickListener disconnectClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Disconnect from room
                 */
                if (room != null) {
                    room.disconnect();
                }
                intializeUI();
            }
        };
    }

    private View.OnClickListener connectActionClickListener() {
        return new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                showConnectDialog();
            }
        };
    }

    private DialogInterface.OnClickListener cancelConnectDialogClickListener() {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                intializeUI();
                alertDialog.dismiss();
            }
        };
    }

    private View.OnClickListener switchCameraClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraCapturer != null) {
                    CameraSource cameraSource = cameraCapturer.getCameraSource();
                    cameraCapturer.switchCamera();
                    if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                        thumbnailVideoView.setMirror(cameraSource == CameraSource.BACK_CAMERA);
                    } else {
                        primaryVideoView.setMirror(cameraSource == CameraSource.BACK_CAMERA);
                    }
                }
            }
        };
    }

    private View.OnClickListener localVideoClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Enable/disable the local video track
                 */
                if (localVideoTrack != null) {
                    boolean enable = !localVideoTrack.isEnabled();
                    localVideoTrack.enable(enable);
                    int icon;
                    if (enable) {
                        icon = R.drawable.ic_videocam_white_24dp;
                        switchCameraActionFab.show();
                    } else {
                        icon = R.drawable.ic_videocam_off_black_24dp;
                        switchCameraActionFab.hide();
                    }
                    localVideoActionFab.setImageDrawable(
                            ContextCompat.getDrawable(VideoNotifyActivity.this, icon));
                }
            }
        };
    }

    private View.OnClickListener muteClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Enable/disable the local audio track. The results of this operation are
                 * signaled to other Participants in the same Room. When an audio track is
                 * disabled, the audio is muted.
                 */
                if (localAudioTrack != null) {
                    boolean enable = !localAudioTrack.isEnabled();
                    localAudioTrack.enable(enable);
                    int icon = enable ?
                            R.drawable.ic_mic_white_24dp : R.drawable.ic_mic_off_black_24dp;
                    muteActionFab.setImageDrawable(ContextCompat.getDrawable(
                            VideoNotifyActivity.this, icon));
                }
            }
        };
    }

    private void enableAudioFocus(boolean focus) {
        if (focus) {
            previousAudioMode = audioManager.getMode();
            // Request audio focus before making any device switch.
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        } else {
            audioManager.setMode(previousAudioMode);
            audioManager.abandonAudioFocus(null);
        }
    }

    private void enableVolumeControl(boolean volumeControl) {
        if (volumeControl) {
            /*
             * Enable changing the volume using the up/down keys during a conversation
             */
            setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        } else {
            setVolumeControlStream(getVolumeControlStream());
        }
    }

    public static AlertDialog createConnectDialog(String title,
                                                  EditText roomEditText,
                                                  DialogInterface.OnClickListener callParticipantsClickListener,
                                                  DialogInterface.OnClickListener cancelClickListener,
                                                  Context context) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setIcon(R.drawable.ic_video_call_black_24dp);
        alertDialogBuilder.setTitle(title);
        alertDialogBuilder.setPositiveButton("Connect", callParticipantsClickListener);
        alertDialogBuilder.setNegativeButton("Cancel", cancelClickListener);
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setView(roomEditText);
        return alertDialogBuilder.create();
    }

}
