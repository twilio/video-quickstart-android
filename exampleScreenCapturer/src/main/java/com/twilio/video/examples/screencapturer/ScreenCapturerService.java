package com.twilio.video.examples.screencapturer;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;


class ScreenCapturerManager {
    private ScreenCapturerService mService;
    private Context mContext;
    private State currentState = State.UNBIND_SERVICE;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to ScreenCapturerService, cast the IBinder and get ScreenCapturerService instance
            ScreenCapturerService.LocalBinder binder = (ScreenCapturerService.LocalBinder) service;
            mService = binder.getService();
            currentState = State.BIND_SERVICE;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };

    /**
     * An enum describing the possible states of a ScreenCapturerManager.
     */
    public enum State {
        BIND_SERVICE,
        START_FOREGROUND,
        END_FOREGROUND,
        UNBIND_SERVICE
    }

    ScreenCapturerManager(Context context) {
        mContext = context;
        bindService();
    }

    public void nextState() {
        switch (currentState) {
            case BIND_SERVICE:
            case END_FOREGROUND:
                startForeground();
                break;
            case START_FOREGROUND:
                endForeground();
                break;
            case UNBIND_SERVICE:
            default:
                break;
        }
    }

    private void bindService() {
        Intent intent = new Intent(mContext, ScreenCapturerService.class);
        mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void startForeground() {
        mService.startForeground();
        currentState = State.START_FOREGROUND;
    }

    private void endForeground() {
        mService.endForeground();
        currentState = State.END_FOREGROUND;
    }

    void unbindService() {
        mContext.unbindService(connection);
        currentState = State.UNBIND_SERVICE;
    }
}

@TargetApi(29)
public class ScreenCapturerService extends Service {
    private static final String CHANNEL_ID = "screen_capture";
    private static final String CHANNEL_NAME = "Screen_Capture";

    // Binder given to clients
    private final IBinder binder = new LocalBinder();

    /**
     * Class used for the client Binder.  We know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public ScreenCapturerService getService() {
            // Return this instance of ScreenCapturerService so clients can call public methods
            return ScreenCapturerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    public void startForeground() {
        NotificationChannel chan = new NotificationChannel(CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_NONE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        final int notificationId = (int) System.currentTimeMillis();
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_screen_share_white_24dp)
                .setContentTitle("ScreenCapturerService is running in the foreground")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(notificationId, notification);
    }

    public void endForeground() {
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
