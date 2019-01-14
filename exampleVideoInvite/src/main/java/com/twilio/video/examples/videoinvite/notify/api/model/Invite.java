package com.twilio.video.examples.videoinvite.notify.api.model;

import java.util.HashMap;
import java.util.Map;

public class Invite {
    public final String roomName;
    // "from" is a reserved word in Twilio Notify so we use a more verbose name instead
    public final String fromIdentity;

    public Invite(final String fromIdentity, final String roomName) {
        this.fromIdentity = fromIdentity;
        this.roomName = roomName;
    }

    public Map<String, String> getMap() {
        HashMap<String, String> map = new HashMap<>();

        map.put("fromIdentity", fromIdentity);
        map.put("roomName", roomName);

        return map;
    }
}
