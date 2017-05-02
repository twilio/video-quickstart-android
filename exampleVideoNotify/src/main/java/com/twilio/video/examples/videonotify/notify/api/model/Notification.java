package com.twilio.video.examples.videonotify.notify.api.model;

import java.util.List;

public class Notification {
    public final String title;
    public final String body;
    public final String data;
    public final List<String> tag;

    public Notification(String title, String body, String data, List<String> tag) {
        this.title = title;
        this.body = body;
        this.data = data;
        this.tag = tag;
    }

}
