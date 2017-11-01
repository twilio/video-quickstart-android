package com.twilio.video.examples.datatrack;

import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Message that represents a drawing event on a view.
 *
 * A motion message is represented as the following JSON object.
 *
 * {
 *   mouseDown: true,
 *   mouseCoordinates: {
 *     x: 1,
 *     y: 2
 *   }
 * }
 *
 * The web app sends messages prefixed with mouse so the message is serialized and
 * deserialized using this convention.
 *
 */
public class MotionMessage {
    private static final String TAG = "MotionMessage";

    /**
     * Indicates if the drawing motion is down on the view.
     */
    public final boolean actionDown;

    /**
     * X and Y coordinates of the motion event within the view.
     */
    public final Pair<Float, Float> coordinates;

    public MotionMessage(boolean actionDown, float x, float y) {
        this.actionDown = actionDown;
        this.coordinates = new Pair<>(x, y);
    }

    /**
     * Deserializes a raw json message into a MotionMessage instance.
     *
     * @param json raw json motion message.
     * @return motion message instance.
     */
    public static @Nullable MotionMessage fromJson(String json) {
        MotionMessage motionMessage = null;

        try {
            /*
             * The web app sends messages prefixed with mouse so the message is serialized and
             * deserialized using this convention.
             */
            JSONObject motionMessageJsonObject = new JSONObject(json);
            boolean actionDown = motionMessageJsonObject.getBoolean("mouseDown");
            JSONObject coordinates = motionMessageJsonObject.getJSONObject("mouseCoordinates");
            float x = Double.valueOf(coordinates.getDouble("x")).floatValue();
            float y = Double.valueOf(coordinates.getDouble("y")).floatValue();

            motionMessage = new MotionMessage(actionDown, x, y);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }

        return motionMessage;
    }

    /**
     * Serializes the motion message instance into a JSON string.
     *
     * @return raw json string of object.
     */
    public String toJsonString() {
        JSONObject motionMessageJson = new JSONObject();
        JSONObject coordinatesJson = new JSONObject();

        try {
            /*
             * The web app sends messages prefixed with mouse so the message is serialized and
             * deserialized using this convention.
             */
            motionMessageJson.put("mouseDown", actionDown);
            coordinatesJson.put("x", coordinates.first.doubleValue());
            coordinatesJson.put("y", coordinates.second.doubleValue());
            motionMessageJson.put("mouseCoordinates", coordinatesJson);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }

        return motionMessageJson.toString();
    }
}
