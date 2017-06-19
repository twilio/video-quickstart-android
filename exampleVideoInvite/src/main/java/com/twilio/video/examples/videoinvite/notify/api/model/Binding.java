package com.twilio.video.examples.videoinvite.notify.api.model;

import java.util.List;

/**
 * The Binding model defined to register a binding with Twilio Notify
 *
 * https://www.twilio.com/docs/api/notify/rest/bindings
 */
public class Binding {
    public final String Identity;
    public final String Endpoint;
    public final String Address;
    public final String BindingType;
    public final List<String> Tag;

    public Binding(String identity, String endpoint, String address, String bindingType, List<String> tag) {
        this.Identity = identity;
        this.Endpoint = endpoint;
        this.Address = address;
        this.BindingType = bindingType;
        this.Tag = tag;
    }
}
