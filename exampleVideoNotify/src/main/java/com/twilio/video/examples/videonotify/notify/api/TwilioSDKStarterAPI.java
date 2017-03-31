package com.twilio.video.examples.videonotify.notify.api;

import com.twilio.video.examples.videonotify.notify.api.model.Binding;
import com.twilio.video.examples.videonotify.notify.api.model.Token;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

import static com.twilio.video.examples.videonotify.VideoNotifyActivity.TWILIO_SDK_STARTER_SERVER_URL;

public class TwilioSDKStarterAPI {

    /**
     * A resource defined to register Notify bindings using the sdk-starter projects available in
     * C#, Java, Node, PHP, Python, or Ruby.
     *
     * https://github.com/TwilioDevEd?q=sdk-starter
     */
    interface SDKStarterService {
        @POST("/register")
        Call<Void> register(@Body Binding binding);
        @GET("/token")
        Call<Token> fetchToken();
    }

    private static HttpLoggingInterceptor logging = new HttpLoggingInterceptor();

    private static OkHttpClient.Builder httpClient = new OkHttpClient.Builder().addInterceptor(logging.setLevel(HttpLoggingInterceptor.Level.BODY));

    private static SDKStarterService sdkStarterService = new Retrofit.Builder()
            .baseUrl(TWILIO_SDK_STARTER_SERVER_URL)
            .addConverterFactory(JacksonConverterFactory.create())
            .client(httpClient.build())
            .build()
            .create(SDKStarterService.class);

    public static Call<Void> registerBinding(final Binding binding) {
        return sdkStarterService.register(binding);
    }

    public static Call<Token> fetchToken() {
        return sdkStarterService.fetchToken();
    }
}
