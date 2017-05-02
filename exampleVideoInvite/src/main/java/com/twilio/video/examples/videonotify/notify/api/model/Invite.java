package com.twilio.video.examples.videonotify.notify.api.model;

public class Invite {
    public final String roomName;
    public final String from;

    public Invite(final String from, final String roomName) {
        this.from = from;
        this.roomName = roomName;
    }
}
