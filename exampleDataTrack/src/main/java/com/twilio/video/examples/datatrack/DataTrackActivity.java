package com.twilio.video.examples.datatrack;

import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalDataTrack;
import com.twilio.video.RemoteAudioTrack;
import com.twilio.video.RemoteAudioTrackPublication;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DataTrackActivity extends AppCompatActivity {
    private static final String TAG = "DataTrackActivity";
    private static final String DATA_TRACK_MESSAGE_THREAD_NAME = "DataTrackMessages";

    // You must provide a Twilio Access Token to connect to the Video service
    private static final String TWILIO_ACCESS_TOKEN = BuildConfig.TWILIO_ACCESS_TOKEN;
    private static final String ACCESS_TOKEN_SERVER = BuildConfig.TWILIO_ACCESS_TOKEN_SERVER;

    // Video SDK fields
    private String accessToken = null;
    private Room room;
    private LocalDataTrack localDataTrack;
    private boolean disconnectedFromOnDestroy;

    // Dedicated thread and handler for messages received from a RemoteDataTrack
    private final HandlerThread dataTrackMessageThread =
            new HandlerThread(DATA_TRACK_MESSAGE_THREAD_NAME);
    private Handler dataTrackMessageThreadHandler;

    // Map used to map remote data tracks to remote participants
    private final Map<RemoteDataTrack, RemoteParticipant> dataTrackRemoteParticipantMap =
            new HashMap<>();

    // Drawing view listener that sends the events on the local data track
    private final CollaborativeDrawingView.Listener drawingViewListener =
            new CollaborativeDrawingView.Listener() {
                @Override
                public void onTouchEvent(int actionEvent, float x, float y) {
                    Log.d(TAG, String.format("onTouchEvent: actionEvent=%d, x=%f, y=%f",
                            actionEvent, x, y));
                    boolean actionDown = (actionEvent == MotionEvent.ACTION_DOWN);
                    float normalizedX = x / (float) collaborativeDrawingView.getWidth();
                    float normalizedY = y / (float) collaborativeDrawingView.getHeight();
                    MotionMessage motionMessage = new MotionMessage(actionDown,
                            normalizedX,
                            normalizedY);

                    if (localDataTrack != null) {
                        localDataTrack.send(motionMessage.toJsonString());
                    } else {
                        Log.e(TAG, "Ignoring touch event because data track is release");
                    }
                }
            };

    // UI Fields
    private CoordinatorLayout containerLayout;
    private Toolbar toolbar;
    private Snackbar snackbar;
    private EditText roomEditText;
    private Button connectButton;
    private CollaborativeDrawingView collaborativeDrawingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_track);

        // Create the local data track
        localDataTrack = LocalDataTrack.create(this);

        // Start the thread where data messages are received
        dataTrackMessageThread.start();
        dataTrackMessageThreadHandler = new Handler(dataTrackMessageThread.getLooper());

        // Setup UI
        toolbar = findViewById(R.id.toolbar);
        containerLayout = findViewById(R.id.container);
        collaborativeDrawingView = findViewById(R.id.drawing_view);
        snackbar = Snackbar.make(containerLayout,
                R.string.connect_to_share,
                Snackbar.LENGTH_INDEFINITE);
        setSupportActionBar(toolbar);
        snackbar.show();
        initializeUi();

        // Set access token
        setAccessToken();
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

        // Quit the data track message thread
        dataTrackMessageThread.quit();

        /*
         * Release the local audio and video tracks ensuring any memory allocated to audio
         * or video is freed.
         */
        if (localDataTrack != null) {
            localDataTrack.release();
            localDataTrack = null;
        }

        super.onDestroy();
    }

    private void initializeUi() {
        snackbar.setText(R.string.connect_to_share);
        roomEditText = findViewById(R.id.room_edit_text);
        roomEditText.addTextChangedListener(roomEditTextWatcher());
        roomEditText.setEnabled(true);
        connectButton = findViewById(R.id.connect_button);
        connectButton.setOnClickListener(connectButtonClickListener());
        connectButton.setText(R.string.connect);
        connectButton.setEnabled(true);
        collaborativeDrawingView.setEnabled(false);
        collaborativeDrawingView.setListener(drawingViewListener);
    }

    private TextWatcher roomEditTextWatcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (roomEditText.length() != 0) {
                    connectButton.setTextColor(Color.WHITE);
                    connectButton.setEnabled(true);
                } else {
                    connectButton.setTextColor(ResourcesCompat.getColor(getResources(),
                            R.color.colorButtonText,
                            null));
                    connectButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        };
    }

    private View.OnClickListener connectButtonClickListener() {
        return view -> {
            if (connectButton.getText().equals(getString(R.string.connect))) {
                String roomName = roomEditText.getText().toString();
                roomEditText.setEnabled(false);
                connectButton.setEnabled(false);
                snackbar.setText("Connecting to " + roomName);
                connectToRoom(roomName);
            } else {
                disconnectFromRoom();
            }
        };
    }

    private void connectToRoom(String roomName) {
        ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
                .roomName(roomName)
                .dataTracks(Collections.singletonList(localDataTrack))
                .build();

        room = Video.connect(this, connectOptions, roomListener());
    }

    private void setAccessToken() {
        if (!BuildConfig.USE_TOKEN_SERVER) {
            /*
             * OPTION 1 - Generate an access token from the getting started portal
             * https://www.twilio.com/console/video/dev-tools/testing-tools
             */
            this.accessToken = TWILIO_ACCESS_TOKEN;
        } else {
            // OPTION 2 - Retrieve an access token from your own web app
            retrieveAccessTokenfromServer();
        }
    }

    private void retrieveAccessTokenfromServer() {
        // Disable UI while token is being retrieved
        roomEditText.setEnabled(false);
        connectButton.setEnabled(false);

        // Get token
        Ion.with(this)
                .load(String.format("%s?identity=%s", ACCESS_TOKEN_SERVER,
                        UUID.randomUUID().toString()))
                .asString()
                .setCallback((e, token) -> {
                    boolean requestSucceeded = e == null;

                    if (requestSucceeded) {
                        DataTrackActivity.this.accessToken = token;
                    } else {
                        Toast.makeText(DataTrackActivity.this,
                                R.string.error_retrieving_access_token, Toast.LENGTH_LONG)
                                .show();
                    }

                    // Setup UI based on request result
                    roomEditText.setEnabled(requestSucceeded);
                    connectButton.setEnabled(requestSucceeded);
                });
    }

    private void disconnectFromRoom() {
        if (room != null) {
            room.disconnect();
        }
    }

    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                DataTrackActivity.this.room = room;
                snackbar.setText("Connected to " + room.getName());
                connectButton.setText(R.string.disconnect);
                connectButton.setEnabled(true);
                collaborativeDrawingView.setEnabled(true);
                Toast.makeText(DataTrackActivity.this,
                        R.string.start_drawing,
                        Toast.LENGTH_SHORT)
                        .show();

                for (RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
                    addRemoteParticipant(remoteParticipant);
                }
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                Toast.makeText(DataTrackActivity.this,
                        "Failed to connect: " + e.getMessage(),
                        Toast.LENGTH_LONG)
                        .show();
                initializeUi();
            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                if (!disconnectedFromOnDestroy) {
                    // Clear local drawing
                    collaborativeDrawingView.clear();

                    // Clear remote participants drawing
                    for (RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
                        collaborativeDrawingView.clear(remoteParticipant);
                    }

                    initializeUi();
                }
            }

            @Override
            public void onParticipantConnected(Room room, RemoteParticipant remoteParticipant) {
                Toast.makeText(DataTrackActivity.this,
                        String.format("%s connected", remoteParticipant.getIdentity()),
                        Toast.LENGTH_SHORT)
                        .show();
                addRemoteParticipant(remoteParticipant);
            }

            @Override
            public void onParticipantDisconnected(Room room, RemoteParticipant remoteParticipant) {
                Toast.makeText(DataTrackActivity.this,
                        String.format("%s disconnected", remoteParticipant.getIdentity()),
                        Toast.LENGTH_SHORT)
                        .show();
                removeRemoteParticipant(remoteParticipant);
            }

            @Override
            public void onRecordingStarted(Room room) {

            }

            @Override
            public void onRecordingStopped(Room room) {

            }
        };
    }

    private void addRemoteParticipant(final RemoteParticipant remoteParticipant) {
        // Observe remote participant events
        remoteParticipant.setListener(remoteParticipantListener());

        for (final RemoteDataTrackPublication remoteDataTrackPublication :
                remoteParticipant.getRemoteDataTracks()) {
            /*
             * Data track messages are received on the thread that calls setListener. Post the
             * invocation of setting the listener onto our dedicated data track message thread.
             */
            if (remoteDataTrackPublication.isTrackSubscribed()) {
                dataTrackMessageThreadHandler.post(() -> addRemoteDataTrack(remoteParticipant,
                        remoteDataTrackPublication.getRemoteDataTrack()));
            }
        }
    }

    private void removeRemoteParticipant(RemoteParticipant remoteParticipant) {
        // Clear the drawing of the remote participant
        collaborativeDrawingView.clear(remoteParticipant);
    }

    private void addRemoteDataTrack(RemoteParticipant remoteParticipant,
                                    RemoteDataTrack remoteDataTrack) {
        dataTrackRemoteParticipantMap.put(remoteDataTrack, remoteParticipant);
        remoteDataTrack.setListener(remoteDataTrackListener());
    }

    private RemoteParticipant.Listener remoteParticipantListener() {
        return new RemoteParticipant.Listener() {
            @Override
            public void onAudioTrackPublished(RemoteParticipant remoteParticipant,
                                              RemoteAudioTrackPublication remoteAudioTrackPublication) {
            }

            @Override
            public void onAudioTrackUnpublished(RemoteParticipant remoteParticipant,
                                                RemoteAudioTrackPublication remoteAudioTrackPublication) {
            }

            @Override
            public void onDataTrackPublished(RemoteParticipant remoteParticipant,
                                             RemoteDataTrackPublication remoteDataTrackPublication) {
            }

            @Override
            public void onDataTrackUnpublished(RemoteParticipant remoteParticipant,
                                               RemoteDataTrackPublication remoteDataTrackPublication) {
            }

            @Override
            public void onVideoTrackPublished(RemoteParticipant remoteParticipant,
                                              RemoteVideoTrackPublication remoteVideoTrackPublication) {
            }

            @Override
            public void onVideoTrackUnpublished(RemoteParticipant remoteParticipant,
                                                RemoteVideoTrackPublication remoteVideoTrackPublication) {
            }

            @Override
            public void onAudioTrackSubscribed(RemoteParticipant remoteParticipant,
                                               RemoteAudioTrackPublication remoteAudioTrackPublication,
                                               RemoteAudioTrack remoteAudioTrack) {
            }

            @Override
            public void onAudioTrackUnsubscribed(RemoteParticipant remoteParticipant,
                                                 RemoteAudioTrackPublication remoteAudioTrackPublication,
                                                 RemoteAudioTrack remoteAudioTrack) {
            }

            @Override
            public void onAudioTrackSubscriptionFailed(RemoteParticipant remoteParticipant,
                                                       RemoteAudioTrackPublication remoteAudioTrackPublication,
                                                       TwilioException twilioException) {
            }

            @Override
            public void onDataTrackSubscribed(final RemoteParticipant remoteParticipant,
                                              RemoteDataTrackPublication remoteDataTrackPublication,
                                              final RemoteDataTrack remoteDataTrack) {
                /*
                 * Data track messages are received on the thread that calls setListener. Post the
                 * invocation of setting the listener onto our dedicated data track message thread.
                 */
                dataTrackMessageThreadHandler.post(() -> addRemoteDataTrack(remoteParticipant, remoteDataTrack));
            }

            @Override
            public void onDataTrackUnsubscribed(RemoteParticipant remoteParticipant,
                                                RemoteDataTrackPublication remoteDataTrackPublication,
                                                RemoteDataTrack remoteDataTrack) {
            }

            @Override
            public void onDataTrackSubscriptionFailed(RemoteParticipant remoteParticipant,
                                                      RemoteDataTrackPublication remoteDataTrackPublication,
                                                      TwilioException twilioException) {
            }

            @Override
            public void onVideoTrackSubscribed(RemoteParticipant remoteParticipant,
                                               RemoteVideoTrackPublication remoteVideoTrackPublication,
                                               RemoteVideoTrack remoteVideoTrack) {
            }

            @Override
            public void onVideoTrackUnsubscribed(RemoteParticipant remoteParticipant,
                                                 RemoteVideoTrackPublication remoteVideoTrackPublication,
                                                 RemoteVideoTrack remoteVideoTrack) {
            }

            @Override
            public void onVideoTrackSubscriptionFailed(RemoteParticipant remoteParticipant,
                                                       RemoteVideoTrackPublication remoteVideoTrackPublication,
                                                       TwilioException twilioException) {
            }

            @Override
            public void onAudioTrackEnabled(RemoteParticipant remoteParticipant,
                                            RemoteAudioTrackPublication remoteAudioTrackPublication) {
            }

            @Override
            public void onAudioTrackDisabled(RemoteParticipant remoteParticipant,
                                             RemoteAudioTrackPublication remoteAudioTrackPublication) {

            }

            @Override
            public void onVideoTrackEnabled(RemoteParticipant remoteParticipant,
                                            RemoteVideoTrackPublication remoteVideoTrackPublication) {

            }

            @Override
            public void onVideoTrackDisabled(RemoteParticipant remoteParticipant,
                                             RemoteVideoTrackPublication remoteVideoTrackPublication) {

            }
        };
    }

    private RemoteDataTrack.Listener remoteDataTrackListener() {
        return new RemoteDataTrack.Listener() {
            @Override
            public void onMessage(RemoteDataTrack remoteDataTrack, ByteBuffer byteBuffer) {

            }

            @Override
            public void onMessage(RemoteDataTrack remoteDataTrack, String message) {
                Log.d(TAG, "onMessage: " + message);
                MotionMessage motionMessage = MotionMessage.fromJson(message);

                if (motionMessage != null) {
                    RemoteParticipant remoteParticipant = dataTrackRemoteParticipantMap
                            .get(remoteDataTrack);
                    int actionEvent = (motionMessage.actionDown) ?
                            (MotionEvent.ACTION_DOWN) :
                            MotionEvent.ACTION_UP;

                    // Process remote drawing event
                    float projectedX = motionMessage.coordinates.first *
                            (float) collaborativeDrawingView.getWidth();
                    float projectedY = motionMessage.coordinates.second *
                            (float) collaborativeDrawingView.getHeight();
                    collaborativeDrawingView.onRemoteTouchEvent(remoteParticipant,
                            actionEvent,
                            projectedX,
                            projectedY);
                } else {
                    Log.e(TAG, "Failed to deserialize message: " + message);
                }
            }
        };
    }
}
