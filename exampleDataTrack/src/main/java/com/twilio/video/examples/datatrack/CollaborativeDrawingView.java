package com.twilio.video.examples.datatrack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;

import com.twilio.video.RemoteParticipant;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Collaborative drawing view inspired by the smooth signatures blog from Square.
 *
 * https://medium.com/square-corner-blog/smoother-signatures-be64515adb33
 *
 * This view will draw the path of the local participant as well as all other participants.
 */
public class CollaborativeDrawingView extends View {
    private static final float STROKE_WIDTH = 5f;
    private static final String TAG = "DrawingView";
    private static final float HALF_STROKE_WIDTH = STROKE_WIDTH / 2;

    /*
     * Attributes used to draw the local participant's path.
     */
    private Paint paint = new Paint();
    private Path path = new Path();
    private float lastTouchX;
    private float lastTouchY;
    private final RectF dirtyRect = new RectF();


    /*
     * Listener of local drawing events.
     */
    private Listener listener;

    /*
     * Maintains the path and paint object for each remote participant.
     */
    private final Map<RemoteParticipant, Pair<Path, Paint>> remoteParticipantPalettes =
            new HashMap<>();

    public CollaborativeDrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(STROKE_WIDTH);
    }

    /**
     * Listen for local drawing events.
     */
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * Update the drawn path of a remote participant. This method can be called from any thread.
     *
     * @param remoteParticipant participant who sent motion event.
     * @param actionEvent the {@link MotionEvent} issued by the participant.
     * @param eventX the x coordinate of the motion event.
     * @param eventY the y coordinate of the motion event.
     */
    public void onRemoteTouchEvent(RemoteParticipant remoteParticipant,
                                   int actionEvent,
                                   float eventX,
                                   float eventY) {
        Pair<Path, Paint> remoteParticipantPalette =
                (remoteParticipantPalettes.containsKey(remoteParticipant)) ?
                        (remoteParticipantPalettes.get(remoteParticipant)) :
                        (insertAndGetRemoteParticipantPalette(remoteParticipant));

        // Process action event
        switch (actionEvent) {
            case MotionEvent.ACTION_DOWN:
                remoteParticipantPalette.first.moveTo(eventX, eventY);
                break;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                remoteParticipantPalette.first.lineTo(eventX, eventY);
                break;
            default:
                Log.d(TAG, "Ignored remote touch event: " + actionEvent);
        }

        // Invalidate the view to draw the new remote track event
        post(this::invalidate);
    }

    /**
     * Clear the local drawing.
     */
    public void clear() {
        // Clear the local path
        path.reset();

        // Invalidate the view
        post(this::invalidate);
    }

    /**
     * Remove drawing of the remote participant. This method can be called from any thread.
     *
     * @param remoteParticipant participant to clear.
     */
    public void clear(RemoteParticipant remoteParticipant) {
        // Remove the remote participant palette
        remoteParticipantPalettes.remove(remoteParticipant);

        // Invalidate the view
        post(this::invalidate);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Only draw when enabled
        if (!isEnabled()) {
            return;
        }

        // Draw local path
        canvas.drawPath(path, paint);

        // Draw remote paths
        for (Pair<Path, Paint> remoteParticipantPalette : remoteParticipantPalettes.values()) {
            canvas.drawPath(remoteParticipantPalette.first, remoteParticipantPalette.second);

        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Only handle touch events when enabled
        if (!isEnabled()) {
            return false;
        }

        float eventX = event.getX();
        float eventY = event.getY();
        int actionEvent = event.getAction();

        // Notify listener of action event
        if (listener != null) {
            listener.onTouchEvent(actionEvent, eventX, eventY);
        }

        // Process action event
        switch (actionEvent) {
            case MotionEvent.ACTION_DOWN:
                path.moveTo(eventX, eventY);
                lastTouchX = eventX;
                lastTouchY = eventY;
                // There is no end point yet, so don't waste cycles invalidating.
                return true;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                // Start tracking the dirty region.
                resetDirtyRect(eventX, eventY);

                /*
                 * When the hardware tracks events faster than they are delivered, the event will
                 * contain a history of those skipped points.
                 */
                int historySize = event.getHistorySize();
                for (int i = 0; i < historySize; i++) {
                    float historicalX = event.getHistoricalX(i);
                    float historicalY = event.getHistoricalY(i);
                    expandDirtyRect(historicalX, historicalY);
                    path.lineTo(historicalX, historicalY);
                }
                // After replaying history, connect the line to the touch point.
                path.lineTo(eventX, eventY);
                break;
            default:
                Log.d(TAG, "Ignored touch event: " + event.toString());
                return false;
        }

        // Include half the stroke width to avoid clipping.
        invalidate(
                (int) (dirtyRect.left - HALF_STROKE_WIDTH),
                (int) (dirtyRect.top - HALF_STROKE_WIDTH),
                (int) (dirtyRect.right + HALF_STROKE_WIDTH),
                (int) (dirtyRect.bottom + HALF_STROKE_WIDTH));
        lastTouchX = eventX;
        lastTouchY = eventY;
        return true;
    }

    /*
     * Called when replaying history to ensure the dirty region includes all
     * points.
     */
    private void expandDirtyRect(float historicalX, float historicalY) {
        if (historicalX < dirtyRect.left) {
            dirtyRect.left = historicalX;
        } else if (historicalX > dirtyRect.right) {
            dirtyRect.right = historicalX;
        }
        if (historicalY < dirtyRect.top) {
            dirtyRect.top = historicalY;
        } else if (historicalY > dirtyRect.bottom) {
            dirtyRect.bottom = historicalY;
        }
    }

    /*
     * Resets the dirty region when the motion event occurs.
     */
    private void resetDirtyRect(float eventX, float eventY) {
        // The lastTouchX and lastTouchY were set when the ACTION_DOWN
        // motion event occurred.
        dirtyRect.left = Math.min(lastTouchX, eventX);
        dirtyRect.right = Math.max(lastTouchX, eventX);
        dirtyRect.top = Math.min(lastTouchY, eventY);
        dirtyRect.bottom = Math.max(lastTouchY, eventY);
    }

    /*
     * Assigns a new path and palette to a remote participant.
     */
    private Pair<Path, Paint> insertAndGetRemoteParticipantPalette(RemoteParticipant remoteParticipant) {
        Path path = new Path();
        Paint paint = getRemoteParticipantPaint(remoteParticipant);
        Pair<Path, Paint> remoteParticipantPalette = new Pair<>(path, paint);

        remoteParticipantPalettes.put(remoteParticipant, remoteParticipantPalette);

        return remoteParticipantPalette;
    }

    /*
     * Assigns a paint instance to a remote participant.
     */
    private Paint getRemoteParticipantPaint(RemoteParticipant remoteParticipant) {
        Paint paint = new Paint();

        paint.setAntiAlias(true);
        paint.setColor(getRandomColor());
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(STROKE_WIDTH);

        return paint;
    }

    /*
     * Returns a random color.
     */
    private int getRandomColor() {
        Random rnd = new Random();

        return Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
    }

    /**
     * Listener for drawing events.
     */
    public interface Listener {
        /**
         * Notifies of a local touch event from the view on the UI thread.
         *
         * @param actionEvent the {@link MotionEvent} action.
         * @param x the event x coordinate.
         * @param y the event y coordinate.
         */
        void onTouchEvent(int actionEvent, float x, float y);
    }
}
