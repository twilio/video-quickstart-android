package com.twilio.video.examples.videoinvite.notify.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * The Binding model defined to register a binding with Twilio Notify
 *
 * https://www.twilio.com/docs/api/notify/rest/bindings
 */
public class Binding {
    @SerializedName("Identity")
    public final String identity;
    @SerializedName("Endpoint")
    public final String endpoint;
    @SerializedName("Address")
    public final String address;
    @SerializedName("BindingType")
    public final String bindingType;
    @SerializedName("Tag")
    public final List<String> tag;

    public Binding(String identity, String endpoint, String address, String bindingType, List<String> tag) {
        this.identity = identity;
        this.endpoint = endpoint;
        this.address = address;
        this.bindingType = bindingType;
        this.tag = tag;
    }
}
