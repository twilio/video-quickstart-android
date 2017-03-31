package com.twilio.video.examples.videonotify.notify.api.model;

/**
 * This is the Binding model defined to register with Notify using the sdk-starter projects
 * available in C#, Java, Node, PHP, Python, or Ruby.
 *
 * https://github.com/TwilioDevEd?q=sdk-starter
 */
public class Binding {
    public final String identity;
    public final String endpoint;
    public final String Address;
    public final String BindingType;

    public Binding(String identity, String endpoint, String address, String bindingType) {
        this.identity = identity;
        this.endpoint = endpoint;
        this.Address = address;
        this.BindingType = bindingType;
    }
}
