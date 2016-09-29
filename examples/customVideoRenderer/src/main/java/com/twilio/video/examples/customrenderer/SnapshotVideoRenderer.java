package com.twilio.video.examples.customrenderer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.widget.ImageView;

import com.twilio.video.I420Frame;
import com.twilio.video.VideoRenderer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static android.graphics.ImageFormat.NV21;

/**
 * SnapshotVideoRenderer demonstrates how to implement a custom {@link VideoRenderer}. Caches the
 * last frame rendered and will update the provided image view any time {@link #takeSnapshot()} is
 * invoked.
 */
public class SnapshotVideoRenderer implements VideoRenderer {
    private final ImageView imageView;
    private I420Frame i420Frame;

    public SnapshotVideoRenderer(ImageView imageView) {
        this.imageView = imageView;
    }

    @Override
    public void renderFrame(I420Frame i420Frame) {
        this.i420Frame = i420Frame;
    }

    /**
     * Updates the current image view with the last frame renderered.
     */
    public void takeSnapshot() {
        imageView.setImageBitmap(captureBitmap());
    }

    private Bitmap captureBitmap() {
        YuvImage yuvImage = i420ToYuvImage();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Rect rect = new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight());

        // Compress YuvImage to jpeg
        yuvImage.compressToJpeg(rect, 100, stream);

        // Convert jpeg to Bitmap
        byte[] imageBytes = stream.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        Matrix matrix = new Matrix();

        // Apply any needed rotation
        matrix.postRotate(i420Frame.rotationDegree);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix,
                true);

        return bitmap;
    }

    private YuvImage i420ToYuvImage() {
        if (i420Frame.yuvStrides[0] != i420Frame.width) {
            return fastI420ToYuvImage();
        }
        if (i420Frame.yuvStrides[1] != i420Frame.width / 2) {
            return fastI420ToYuvImage();
        }
        if (i420Frame.yuvStrides[2] != i420Frame.width / 2) {
            return fastI420ToYuvImage();
        }

        byte[] bytes = new byte[i420Frame.yuvStrides[0] * i420Frame.height +
                i420Frame.yuvStrides[1] * i420Frame.height / 2 +
                i420Frame.yuvStrides[2] * i420Frame.height / 2];
        ByteBuffer tmp = ByteBuffer.wrap(bytes, 0, i420Frame.width * i420Frame.height);
        copyPlane(i420Frame.yuvPlanes[0], tmp);

        byte[] tmpBytes = new byte[i420Frame.width / 2 * i420Frame.height / 2];
        tmp = ByteBuffer.wrap(tmpBytes, 0, i420Frame.width / 2 * i420Frame.height / 2);

        copyPlane(i420Frame.yuvPlanes[2], tmp);
        for (int row = 0 ; row < i420Frame.height / 2 ; row++) {
            for (int col = 0 ; col < i420Frame.width / 2 ; col++) {
                bytes[i420Frame.width * i420Frame.height + row * i420Frame.width + col * 2]
                        = tmpBytes[row * i420Frame.width / 2 + col];
            }
        }
        copyPlane(i420Frame.yuvPlanes[1], tmp);
        for (int row = 0 ; row < i420Frame.height / 2 ; row++) {
            for (int col = 0 ; col < i420Frame.width / 2 ; col++) {
                bytes[i420Frame.width * i420Frame.height + row * i420Frame.width + col * 2 + 1] =
                        tmpBytes[row * i420Frame.width / 2 + col];
            }
        }
        return new YuvImage(bytes, NV21, i420Frame.width, i420Frame.height, null);
    }

    private YuvImage fastI420ToYuvImage() {
        byte[] bytes = new byte[i420Frame.width * i420Frame.height * 3 / 2];
        int i = 0;
        for (int row = 0 ; row < i420Frame.height ; row++) {
            for (int col = 0 ; col < i420Frame.width ; col++) {
                bytes[i++] = i420Frame.yuvPlanes[0].get(col + row * i420Frame.yuvStrides[0]);
            }
        }
        for (int row = 0 ; row < i420Frame.height / 2 ; row++) {
            for (int col = 0 ; col < i420Frame.width / 2; col++) {
                bytes[i++] = i420Frame.yuvPlanes[2].get(col + row * i420Frame.yuvStrides[2]);
                bytes[i++] = i420Frame.yuvPlanes[1].get(col + row * i420Frame.yuvStrides[1]);
            }
        }
        return new YuvImage(bytes, NV21, i420Frame.width, i420Frame.height, null);
    }

    private void copyPlane(ByteBuffer src, ByteBuffer dst) {
        src.position(0).limit(src.capacity());
        dst.put(src);
        dst.position(0).limit(dst.capacity());
    }
}
