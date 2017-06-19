package com.twilio.video.examples.videoinvite.notify.api.model;

import java.util.List;

/**
 * The Notification model defined to send notifications with Twilio Notify
 *
 * https://www.twilio.com/docs/api/notify/rest/notifications
 */
public class Notification {
    public final String Title;
    public final String Body;
    public final String Data;
    public final List<String> Tag;

    public Notification(String title, String body, String data, List<String> tag) {
        this.Title = title;
        this.Body = body;
        this.Data = data;
        this.Tag = tag;
    }

}
