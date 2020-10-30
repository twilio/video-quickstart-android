package com.twilio.video.examples.customcapturer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import com.twilio.video.Rgba8888Buffer;
import com.twilio.video.VideoCapturer;
import com.twilio.video.VideoDimensions;
import com.twilio.video.VideoFormat;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import tvi.webrtc.CapturerObserver;
import tvi.webrtc.SurfaceTextureHelper;

/**
 * ViewCapturer demonstrates how to implement a custom {@link VideoCapturer}. This class
 * captures the contents of a provided view and signals the {@link VideoCapturer.Listener} when
 * the frame is available.
 */
public class ViewCapturer implements VideoCapturer {
    private static final int VIEW_CAPTURER_FRAMERATE_MS = 100;

    private final View view;
    private Handler handler = new Handler(Looper.getMainLooper());
    private CapturerObserver capturerObserver;
    private AtomicBoolean started = new AtomicBoolean(false);
    private final Runnable viewCapturer = new Runnable() {
        @Override
        public void run() {
            boolean dropFrame = view.getWidth() == 0 || view.getHeight() == 0;

            // Only capture the view if the dimensions have been established
            if (!dropFrame) {
                // Draw view into bitmap backed canvas
                int measuredWidth = View.MeasureSpec.makeMeasureSpec(view.getWidth(),
                        View.MeasureSpec.EXACTLY);
                int measuredHeight = View.MeasureSpec.makeMeasureSpec(view.getHeight(),
                        View.MeasureSpec.EXACTLY);
                view.measure(measuredWidth, measuredHeight);
                view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

                Bitmap viewBitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                        Bitmap.Config.ARGB_8888);
                Canvas viewCanvas = new Canvas(viewBitmap);
                view.draw(viewCanvas);

                // Extract the frame from the bitmap
                int bytes = viewBitmap.getByteCount();
                ByteBuffer buffer = ByteBuffer.allocate(bytes);
                viewBitmap.copyPixelsToBuffer(buffer);

                // Create video frame
                final long captureTimeNs =
                        TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
                tvi.webrtc.VideoFrame.Buffer videoBuffer =
                        new Rgba8888Buffer(buffer, view.getWidth(), view.getHeight());
                tvi.webrtc.VideoFrame videoFrame =
                        new tvi.webrtc.VideoFrame(videoBuffer, 0, captureTimeNs);

                // Notify the listener
                if (started.get()) {
                    capturerObserver.onFrameCaptured(videoFrame);
                    videoFrame.release();
                }
            }

            // Schedule the next capture
            if (started.get()) {
                handler.postDelayed(this, VIEW_CAPTURER_FRAMERATE_MS);
            }
        }
    };

    public ViewCapturer(View view) {
        this.view = view;
    }

    @Override
    public VideoFormat getCaptureFormat() {
        VideoDimensions videoDimensions = new VideoDimensions(view.getWidth(), view.getHeight());

        return new VideoFormat(videoDimensions, 30);
    }

    /**
     * Returns true because we are capturing screen content.
     */
    @Override
    public boolean isScreencast() {
        return true;
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper,
                           Context context,
                           CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        this.started.set(true);

        // Notify capturer API that the capturer has started
        boolean capturerStarted = handler.postDelayed(viewCapturer,
                VIEW_CAPTURER_FRAMERATE_MS);
        this.capturerObserver.onCapturerStarted(capturerStarted);
    }

    /**
     * Stop capturing frames. Note that the SDK cannot receive frames once this has been invoked.
     */
    @Override
    public void stopCapture() {
        this.started.set(false);
        handler.removeCallbacks(viewCapturer);
    }
}
