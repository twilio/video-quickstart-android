package com.twilio.exampleaudiosink;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.koushikdutta.ion.Ion;
import com.twilio.exampleaudiosink.dialog.Dialog;
import com.twilio.video.AudioSink;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.Room;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.UUID;

public class AudioSinkActivity extends AppCompatActivity {
    private static final int MAX_PARTICIPANTS = 2;
    private static final int MIC_PERMISSION_REQUEST_CODE = 5;
    private static final String TAG = "AudioSinkActivity";
    private static final String UNSUPPORTED_PARTICIPANT_COUNT_MSG = "This example only supports %d participants";

    /*
     * Audio and video tracks can be created with names. This feature is useful for categorizing
     * tracks of participants. For example, if one participant publishes a video track with
     * ScreenCapturer and CameraCapturer with the names "screen" and "camera" respectively then
     * other participants can use RemoteVideoTrack#getName to determine which video track is
     * produced from the other participant's screen or camera.
     */
    private static final String LOCAL_AUDIO_TRACK_NAME = "mic";

    /*
     * You must provide a Twilio Access Token to connect to the Video service
     */
    private static final String TWILIO_ACCESS_TOKEN = BuildConfig.TWILIO_ACCESS_TOKEN;
    private static final String ACCESS_TOKEN_SERVER = BuildConfig.TWILIO_ACCESS_TOKEN_SERVER;

    /*
     * Access token used to connect. This field will be set either from the console generated token
     * or the request to the token server.
     */
    private String accessToken;

    /*
     * A Room represents communication between a local participant and one or more participants.
     */
    private Room room;

    /*
     * Android application UI elements
     */
    private LocalAudioTrack localAudioTrack;

    private FloatingActionButton connectActionFab;

    private ImageButton toggleAudioSinkButton, togglePlayAudioButton;
    private TextView audioSinkStatusText;
    private AlertDialog connectDialog;
    private AudioManager audioManager;

    private WavFileHelper wavFileHelper;
    private MediaPlayerHelper mediaPlayerHelper;
    private AudioSink audioSink = new AudioSink() {
        @Override
        public void renderSample(@NonNull ByteBuffer audioSample, int encoding, int sampleRate, int channels) {
            try {
                wavFileHelper.writeBytesToFile(audioSample, encoding, sampleRate, channels);
            } catch (IOException e) {
                Log.e(TAG, String.format("A fatal error has occurred. Stacktrace %s", e.getLocalizedMessage()));
            }
        }
    };

    private int previousAudioMode;
    private boolean previousMicrophoneMute;
    private boolean disconnectedFromOnDestroy;
    private boolean isSpeakerPhoneEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_sink);

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(isSpeakerPhoneEnabled);

        /*
         * Check microphone permissions. Needed in Android M.
         */
        if (!checkPermissionForMicrophone()) {
            requestPermissionForMicrophone();
        } else {
            createAudioTrack();
            setAccessToken();
        }


        /*
         * Initialize audio helper classes
         */
        initializeHelpers();

        /*
         * Set the initial state of the UI
         */
        initializeUI();
    }

    @Override
    protected void onResume() {
        super.onResume();

        /*
         * Route audio through cached value.
         */
        audioManager.setSpeakerphoneOn(isSpeakerPhoneEnabled);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        if (room != null && room.getState() != Room.State.DISCONNECTED) {
            room.disconnect();
            disconnectedFromOnDestroy = true;
        }

        /*
         * Release the local audio track ensuring any memory allocated to audio
         * is freed.
         */
        if (localAudioTrack != null) {
            localAudioTrack.release();
            localAudioTrack = null;
        }

        if (wavFileHelper.isFileWriteInProgress()) {
            try {
                wavFileHelper.finish();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == MIC_PERMISSION_REQUEST_CODE) {
            boolean micPermissionGranted = true;

            for (int grantResult : grantResults) {
                micPermissionGranted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }

            if (micPermissionGranted) {
                createAudioTrack();
                setAccessToken();
            } else {
                Toast.makeText(this,
                        R.string.permissions_needed,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initializeHelpers() {
        wavFileHelper = new WavFileHelper(AudioSinkActivity.this);
        mediaPlayerHelper = new MediaPlayerHelper();
    }

    private void setAccessToken() {
        if (!BuildConfig.USE_TOKEN_SERVER) {
            /*
             * OPTION 1 - Generate an access token from the getting started portal
             * https://www.twilio.com/console/video/dev-tools/testing-tools and add
             * the variable TWILIO_ACCESS_TOKEN setting it equal to the access token
             * string in your local.properties file.
             */
            this.accessToken = TWILIO_ACCESS_TOKEN;
        } else {
            /*
             * OPTION 2 - Retrieve an access token from your own web app.
             * Add the variable ACCESS_TOKEN_SERVER assigning it to the url of your
             * token server and the variable USE_TOKEN_SERVER=true to your
             * local.properties file.
             */
            retrieveAccessTokenfromServer();
        }
    }

    private void connectToRoom(String roomName) {
        configureAudio(true);
        ConnectOptions.Builder connectOptionsBuilder = new ConnectOptions.Builder(accessToken)
                .roomName(roomName);

        /*
         * Add local audio track to connect options to share with participants.
         */
        if (localAudioTrack != null) {
            connectOptionsBuilder
                    .audioTracks(Collections.singletonList(localAudioTrack));
        }

        room = Video.connect(this, connectOptionsBuilder.build(), roomListener());
        setDisconnectAction();
    }

    private void retrieveAccessTokenfromServer() {
        Ion.with(this)
                .load(String.format("%s?identity=%s", ACCESS_TOKEN_SERVER,
                        UUID.randomUUID().toString()))
                .asString()
                .setCallback((e, token) -> {
                    if (e == null) {
                        AudioSinkActivity.this.accessToken = token;
                    } else {
                        Toast.makeText(AudioSinkActivity.this,
                                R.string.error_retrieving_access_token, Toast.LENGTH_LONG)
                                .show();
                    }
                });
    }

    private boolean checkPermissionForMicrophone() {
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG).show();

        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MIC_PERMISSION_REQUEST_CODE);
        }
    }

    private void createAudioTrack() {
        // Share your microphone
        localAudioTrack = LocalAudioTrack.create(this, true, LOCAL_AUDIO_TRACK_NAME);
    }

    private void configureAudio(boolean enable) {
        if (enable) {
            previousAudioMode = audioManager.getMode();
            // Request audio focus before making any device switch
            requestAudioFocus();
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            /*
             * Always disable microphone mute during a WebRTC call.
             */
            previousMicrophoneMute = audioManager.isMicrophoneMute();
            audioManager.setMicrophoneMute(false);
        } else {
            audioManager.setMode(previousAudioMode);
            audioManager.abandonAudioFocus(null);
            audioManager.setMicrophoneMute(previousMicrophoneMute);
        }
    }

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest focusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(
                                    i -> {
                                    })
                            .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    /*
     * Room events listener
     */
    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(@NonNull Room room) {
                audioSinkStatusText.setText(String.format("Connected to %s", room.getName()));
                setTitle(room.getName());
                if (hasNecessaryParticipants(room)) {
                    audioSinkStatusText.setText(getString(R.string.status_capture_ready));
                    enableAudioSinkButton(true);
                } else {
                    audioSinkStatusText.setText(getString(R.string.status_two_particpants_needed));
                    enableAudioSinkButton(false);
                }
            }

            @Override
            public void onReconnecting(@NonNull Room room, @NonNull TwilioException twilioException) {
                audioSinkStatusText.setText(String.format("Reconnecting to %s", room.getName()));
            }

            @Override
            public void onReconnected(@NonNull Room room) {
                audioSinkStatusText.setText(String.format("Connected to %s", room.getName()));
            }

            @Override
            public void onConnectFailure(@NonNull Room room, @NonNull TwilioException e) {
                audioSinkStatusText.setText(getString(R.string.connect_failed));
                configureAudio(false);
                initializeUI();
            }

            @Override
            public void onDisconnected(@NonNull Room room, TwilioException e) {
                audioSinkStatusText.setText(String.format("Disconnected from %s", room.getName()));
                AudioSinkActivity.this.room = null;
                enableAudioSinkButton(false);
                if (wavFileHelper.isFileWriteInProgress()) {
                    finish();
                    enablePlayFileButton(wavFileHelper.doesFileExist() && !wavFileHelper.isFileWriteInProgress());
                }
                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy) {
                    configureAudio(false);
                    initializeUI();
                }
            }

            @Override
            public void onParticipantConnected(@NonNull Room room, @NonNull RemoteParticipant remoteParticipant) {
                if (hasNecessaryParticipants(room)) {
                    audioSinkStatusText.setText(getString(R.string.status_capture_ready));
                    enableAudioSinkButton(true);
                }
            }

            @Override
            public void onParticipantDisconnected(@NonNull Room room, @NonNull RemoteParticipant remoteParticipant) {
                if (!hasNecessaryParticipants(room)) {
                    audioSinkStatusText.setText(getString(R.string.status_two_particpants_needed));
                    enableAudioSinkButton(false);
                    if (wavFileHelper.isFileWriteInProgress()) {
                        try {
                            wavFileHelper.finish();
                            enablePlayFileButton(wavFileHelper.doesFileExist() && !wavFileHelper.isFileWriteInProgress());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void onRecordingStarted(@NonNull Room room) {
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStarted");
            }

            @Override
            public void onRecordingStopped(@NonNull Room room) {
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStopped");
            }
        };
    }

    public boolean hasNecessaryParticipants(@NonNull Room room) {
        if (room.getRemoteParticipants().size() > 1)
            throw new RuntimeException(String.format(UNSUPPORTED_PARTICIPANT_COUNT_MSG, MAX_PARTICIPANTS));
        return room.getRemoteParticipants().size() > 0;
    }

    private void attachSink() {
        room.getRemoteParticipants().get(0).getRemoteAudioTracks().get(0).getRemoteAudioTrack().addSink(audioSink);
        toggleAudioSinkButton.setColorFilter(Color.GRAY);
    }

    private void detachSink() {
        room.getRemoteParticipants().get(0).getRemoteAudioTracks().get(0).getRemoteAudioTrack().removeSink(audioSink);
        toggleAudioSinkButton.setColorFilter(Color.WHITE);
    }

    private void initializeUI() {
        audioSinkStatusText = findViewById(R.id.status_text);

        toggleAudioSinkButton = findViewById(R.id.toggle_sink);
        toggleAudioSinkButton.setOnClickListener(audioSinkClickListener());
        toggleAudioSinkButton.setEnabled(room != null && room.getRemoteParticipants().size() > 0);
        toggleAudioSinkButton.setColorFilter(Color.WHITE);

        togglePlayAudioButton = findViewById(R.id.toggle_play_file);
        togglePlayAudioButton.setEnabled(wavFileHelper.doesFileExist());
        togglePlayAudioButton.setOnClickListener(playAudioClickListener());

        connectActionFab = findViewById(R.id.connect_action_fab);
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                android.R.drawable.sym_call_outgoing));

        connectActionFab.show();
        connectActionFab.setOnClickListener(connectActionClickListener());
    }

    /*
     * Creates a connect UI dialog
     */
    private void showConnectDialog() {
        EditText roomEditText = new EditText(this);
        connectDialog = Dialog.createConnectDialog(roomEditText,
                connectClickListener(roomEditText),
                cancelConnectDialogClickListener(),
                this);
        connectDialog.show();
    }

    /*
     * The actions performed during disconnect.
     */
    private void setDisconnectAction() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_end_white_24px));
        connectActionFab.show();
        connectActionFab.setOnClickListener(disconnectClickListener());
    }

    private void enableAudioSinkButton(boolean isEnabled) {
        toggleAudioSinkButton.setEnabled(isEnabled);
    }

    private void enablePlayFileButton(boolean isEnabled) {
        togglePlayAudioButton.setEnabled(isEnabled);
    }

    private View.OnClickListener audioSinkClickListener() {
        return v -> {
            try {
                if (wavFileHelper.isFileWriteInProgress()) {
                    wavFileHelper.finish();
                    detachSink();
                    enablePlayFileButton(wavFileHelper.doesFileExist() && !wavFileHelper.isFileWriteInProgress());
                    audioSinkStatusText.setText(getString(R.string.status_finished_capturing));
                } else {
                    wavFileHelper.createFile();
                    attachSink();
                    audioSinkStatusText.setText(getString(R.string.status_capturing));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
    }

    private View.OnClickListener playAudioClickListener() {
        return v -> {
            if (mediaPlayerHelper.isPlaying()) {
                mediaPlayerHelper.stopPlaying();
                togglePlayAudioButton.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_media_play));
            } else {
                if (!wavFileHelper.doesFileExist()) {
                    Snackbar.make(connectActionFab, "Couldn't find AudioSink Recording", Snackbar.LENGTH_SHORT).show();
                    togglePlayAudioButton.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_media_play));
                    return;
                }
                try {
                    mediaPlayerHelper.playFile(wavFileHelper.getFullFilePath(), mp -> {
                        if (!mediaPlayerHelper.isPlaying()) {
                            togglePlayAudioButton.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_media_play));
                        }
                    });
                    togglePlayAudioButton.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_media_pause));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private DialogInterface.OnClickListener connectClickListener(final EditText roomEditText) {
        return (dialog, which) -> {
            /*
             * Connect to room
             */
            connectToRoom(roomEditText.getText().toString());
        };
    }

    private View.OnClickListener disconnectClickListener() {
        return v -> {
            /*
             * Disconnect from room
             */
            if (room != null) {
                room.disconnect();
            }
            initializeUI();
        };
    }

    private View.OnClickListener connectActionClickListener() {
        return v -> showConnectDialog();
    }

    private DialogInterface.OnClickListener cancelConnectDialogClickListener() {
        return (dialog, which) -> {
            initializeUI();
            connectDialog.dismiss();
        };
    }
}
