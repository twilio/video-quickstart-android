package com.twilio.video.examples.videoinvite.notify.fcm;

import android.content.Intent;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.twilio.video.examples.videoinvite.notify.service.RegistrationIntentService;

public class NotifyFirebaseInstanceIDService extends FirebaseMessagingService {

    private static final String TAG = "NotifyFbIIDService";

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed token: " + token);
        sendRegistrationToServer();
    }

    private void sendRegistrationToServer() {
        Intent intent = new Intent(this, RegistrationIntentService.class);
        startService(intent);
    }
}
