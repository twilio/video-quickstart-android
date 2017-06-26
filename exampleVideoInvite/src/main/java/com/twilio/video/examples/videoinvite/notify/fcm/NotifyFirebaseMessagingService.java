package com.twilio.video.examples.videoinvite.notify.fcm;

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
import com.twilio.video.examples.videoinvite.VideoInviteActivity;
import com.twilio.video.examples.videoinvite.R;
import com.twilio.video.examples.videoinvite.notify.api.model.Invite;

import java.util.Map;

public class NotifyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "NotifyFCMService";

    /*
     * The Twilio Notify message data keys are as follows:
     *  "twi_title"  // The title of the message
     *  "twi_body"   // The body of the message
     *
     * You can find a more detailed description of all supported fields here:
     * https://www.twilio.com/docs/api/notifications/rest/notifications#generic-payload-parameters
     */
    private static final String NOTIFY_TITLE_KEY = "twi_title";
    private static final String NOTIFY_BODY_KEY = "twi_body";

    /*
     * The keys sent by the notify.api.model.Invite model class
     */
    private static final String NOTIFY_INVITE_FROM_IDENTITY_KEY = "fromIdentity";
    private static final String NOTIFY_INVITE_ROOM_NAME_KEY = "roomName";

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
        Map<String,String> messageData = message.getData();
        String title = messageData.get(NOTIFY_TITLE_KEY);
        String body = messageData.get(NOTIFY_BODY_KEY);
        Invite invite =
                new Invite(messageData.get(NOTIFY_INVITE_FROM_IDENTITY_KEY),
                        messageData.get(NOTIFY_INVITE_ROOM_NAME_KEY));

        Log.d(TAG, "From: " + invite.fromIdentity);
        Log.d(TAG, "Room Name: " + invite.roomName);
        Log.d(TAG, "Title: " + title);
        Log.d(TAG, "Body: " + body);

        showNotification(title, body, invite.roomName);
        broadcastVideoNotification(title, invite.roomName);
    }

    /**
     * Create and show a simple notification containing the FCM message.
     */
    private void showNotification(String title, String body, String roomName) {
        Intent intent = new Intent(this, VideoInviteActivity.class);
        intent.setAction(VideoInviteActivity.ACTION_VIDEO_NOTIFICATION);
        intent.putExtra(VideoInviteActivity.VIDEO_NOTIFICATION_TITLE, title);
        intent.putExtra(VideoInviteActivity.VIDEO_NOTIFICATION_ROOM_NAME, roomName);
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
     * Broadcast the Video Notification to the Activity
     */
    private void broadcastVideoNotification(String title, String roomName) {
        Intent intent = new Intent(VideoInviteActivity.ACTION_VIDEO_NOTIFICATION);
        intent.putExtra(VideoInviteActivity.VIDEO_NOTIFICATION_TITLE, title);
        intent.putExtra(VideoInviteActivity.VIDEO_NOTIFICATION_ROOM_NAME, roomName);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
