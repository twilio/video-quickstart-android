package com.twilio.video.examples.videoinvite.notify.api.model;

import java.util.List;

/**
 * This is the Binding model defined to register with Twilio Notify in the sdk-starter projects
 * available in C#, Java, Node, PHP, Python, or Ruby.
 *
 * https://github.com/TwilioDevEd?q=sdk-starter
 */
public class Binding {
    public final String identity;
    public final String endpoint;
    public final String Address;
    public final String BindingType;
    public final List<String> Tag;

    public Binding(String identity, String endpoint, String address, String bindingType, List<String> tag) {
        this.identity = identity;
        this.endpoint = endpoint;
        this.Address = address;
        this.BindingType = bindingType;
        this.Tag = tag;
    }
}
