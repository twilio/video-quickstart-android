package com.twilio.video.examples.videonotify.notify.fcm;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.twilio.video.examples.videonotify.VideoNotifyActivity;
import com.twilio.video.examples.videonotify.R;

import java.util.Map;

public class NotifyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "NotifyFCMService";

    /*
     * The Twilio Notify message data keys are as follows:
     *  "twi_title"  // The title of the message
     *  "twi_body"   // The body of the message
     *  "twi_data"   // The data associated with the message
     *
     * You can find a more detailed description of all supported fields here:
     * https://www.twilio.com/docs/api/notifications/rest/notifications#generic-payload-parameters
     */
    private static final String NOTIFY_TITLE_KEY = "twi_title";
    private static final String NOTIFY_BODY_KEY = "twi_body";
    private static final String NOTIFY_DATA_KEY = "twi_data";

    /**
     * Called when a message is received.
     *
     * @param message The remote message, containing from, and message data as key/value pairs.
     */
    @Override
    public void onMessageReceived(RemoteMessage message) {
        /*
         * The Notify service adds the message body to the remote message data so that we can
         * show a simple notification.
         */
        String from = message.getFrom();
        Map<String,String> messageData = message.getData();
        String title = messageData.get(NOTIFY_TITLE_KEY);
        String body = messageData.get(NOTIFY_BODY_KEY);
        // TODO: Fix this once data is working in the sdk-starter app
        String roomName = body;

        Log.d(TAG, "From: " + from);
        Log.d(TAG, "Title: " + body);
        Log.d(TAG, "Body: " + body);
        Log.d(TAG, "Room Name: " + roomName);

        showNotification(title, body, roomName);
        broadcastVideoNotification(body, roomName);
    }

    /**
     * Create and show a simple notification containing the FCM message.
     */
    private void showNotification(String title, String body, String roomName) {
        Intent intent = new Intent(this, VideoNotifyActivity.class);
        intent.setAction(VideoNotifyActivity.ACTION_VIDEO_NOTIFICATION);
        intent.putExtra(VideoNotifyActivity.VIDEO_NOTIFICATION_BODY, body);
        intent.putExtra(VideoNotifyActivity.VIDEO_NOTIFICATION_ROOM_NAME, roomName);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_video_call_white_24dp)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    /*
     * Broadcast the Video Notification to the VideoNotifyActivity
     */
    private void broadcastVideoNotification(String body, String roomName) {
        Intent intent = new Intent(VideoNotifyActivity.ACTION_VIDEO_NOTIFICATION);
        intent.putExtra(VideoNotifyActivity.VIDEO_NOTIFICATION_BODY, body);
        intent.putExtra(VideoNotifyActivity.VIDEO_NOTIFICATION_ROOM_NAME, roomName);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
