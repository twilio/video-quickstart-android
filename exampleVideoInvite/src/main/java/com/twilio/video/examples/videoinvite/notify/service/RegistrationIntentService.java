package com.twilio.video.examples.videoinvite.notify.service;

import static com.twilio.video.examples.videoinvite.VideoInviteActivity.TWILIO_SDK_STARTER_SERVER_URL;
import static com.twilio.video.examples.videoinvite.notify.service.BindingSharedPreferences.ADDRESS;
import static com.twilio.video.examples.videoinvite.notify.service.BindingSharedPreferences.ENDPOINT;
import static com.twilio.video.examples.videoinvite.notify.service.BindingSharedPreferences.IDENTITY;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.messaging.FirebaseMessaging;
import com.twilio.video.examples.videoinvite.R;
import com.twilio.video.examples.videoinvite.VideoInviteActivity;
import com.twilio.video.examples.videoinvite.notify.api.TwilioSDKStarterAPI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.twilio.video.examples.videoinvite.notify.api.model.Binding;
import com.twilio.video.examples.videoinvite.notify.api.model.Token;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegistrationIntentService extends IntentService {

    private static final String TAG = "RegIntentService";

    /*
     * The notify binding type to use. Use FCM since GCM has been deprecated by Google
     */
    private static final String BINDING_TYPE = "fcm";

    private SharedPreferences sharedPreferences;

    public RegistrationIntentService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        register();
    }

    private void register() {
        if (TWILIO_SDK_STARTER_SERVER_URL.equals(
                getString(R.string.twilio_sdk_starter_server_url))) {
            String message = "Error: Set a valid sdk starter server url";
            Log.e(TAG, message);
            sendRegistrationFailure(message);
        } else {
            String identity = sharedPreferences.getString(IDENTITY, null);
            try {
                Response<Token> response = TwilioSDKStarterAPI.fetchToken(identity).execute();
                if (response.isSuccessful() && response.body() != null) {
                    bind(response.body().identity, response.body().token);
                } else if (!response.isSuccessful()) {
                    String message =
                            "Fetching token failed: "
                                    + response.code()
                                    + " "
                                    + response.message();
                    Log.e(TAG, message);
                    sendRegistrationFailure(message);
                } else {
                    String message = "Fetching token failed: server returned empty body";
                    Log.e(TAG, message);
                    sendRegistrationFailure(message);
                }
            } catch (Exception e) {
                String message = "Fetching token failed: " + e.getMessage();
                Log.e(TAG, message, e);
                sendRegistrationFailure(message);
            }
        }
    }

    private void bind(final String identity, final String token) {
        // Load the old binding values from shared preferences if they exist
        final String endpoint = sharedPreferences.getString(ENDPOINT, null);
        final String address = sharedPreferences.getString(ADDRESS, null);

        final String newEndpoint;
        final String newAddress;
        try {
            String installationId =
                    Tasks.await(FirebaseInstallations.getInstance().getId(), 30, TimeUnit.SECONDS);
            newEndpoint = identity + "@" + installationId;
            newAddress = Tasks.await(FirebaseMessaging.getInstance().getToken(), 30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            Log.w(TAG, "Firebase token request timed out, will retry on next attempt.");
            sendRegistrationFailure("Firebase token request timed out.");
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Firebase token request interrupted.");
            sendRegistrationFailure("Firebase token request interrupted.");
            return;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get Firebase token: " + e.getMessage());
            sendRegistrationFailure("Failed to get Firebase token: " + e.getMessage());
            return;
        }

        if (newAddress == null) {
            Log.w(TAG, "The Firebase token is not available yet.");
            return;
        }

        /*
         * Check whether a new binding registration is required by comparing the prior values that
         * were stored in shared preferences after the last successful binding registration.
         */
        if (newEndpoint.equals(endpoint) && newAddress.equals(address)) {
            Log.i(
                    TAG,
                    "A new binding registration was not performed because "
                            + "the binding values are the same as the last registered binding.");
            sendRegistrationSuccess(identity, token);
        } else {
            /*
             * Clear the existing binding from SharedPreferences and attempt to register
             * the new binding values.
             */
            sharedPreferences.edit().remove(IDENTITY).remove(ENDPOINT).remove(ADDRESS).apply();

            final Binding binding =
                    new Binding(
                            identity,
                            newEndpoint,
                            newAddress,
                            BINDING_TYPE,
                            VideoInviteActivity.NOTIFY_TAGS);

            TwilioSDKStarterAPI.registerBinding(binding)
                    .enqueue(
                            new Callback<Void>() {
                                @Override
                                public void onResponse(Call<Void> call, Response<Void> response) {
                                    if (response.isSuccessful()) {
                                        sharedPreferences
                                                .edit()
                                                .putString(IDENTITY, identity)
                                                .putString(ENDPOINT, newEndpoint)
                                                .putString(ADDRESS, newAddress)
                                                .apply();
                                        sendRegistrationSuccess(identity, token);
                                    } else {
                                        String message =
                                                "Binding registration failed: "
                                                        + response.code()
                                                        + " "
                                                        + response.message();
                                        Log.e(TAG, message);
                                        sendRegistrationFailure(message);
                                    }
                                }

                                @Override
                                public void onFailure(Call<Void> call, Throwable t) {
                                    String message =
                                            "Binding registration failed: " + t.getMessage();
                                    Log.e(TAG, message);
                                    sendRegistrationFailure(message);
                                }
                            });
        }
    }

    private void sendRegistrationSuccess(String identity, String token) {
        Intent intent = new Intent(VideoInviteActivity.ACTION_REGISTRATION);
        intent.putExtra(VideoInviteActivity.REGISTRATION_IDENTITY, identity);
        intent.putExtra(VideoInviteActivity.REGISTRATION_TOKEN, token);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendRegistrationFailure(String message) {
        Intent intent = new Intent(VideoInviteActivity.ACTION_REGISTRATION);
        intent.putExtra(VideoInviteActivity.REGISTRATION_ERROR, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
