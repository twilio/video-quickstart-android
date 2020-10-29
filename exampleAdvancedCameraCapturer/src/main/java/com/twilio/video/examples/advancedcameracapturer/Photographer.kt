package com.twilio.video.examples.advancedcameracapturer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import com.twilio.video.VideoView
import tvi.webrtc.VideoFrame
import tvi.webrtc.VideoFrame.I420Buffer
import tvi.webrtc.VideoFrame.TextureBuffer
import tvi.webrtc.VideoProcessor
import tvi.webrtc.VideoSink
import tvi.webrtc.YuvConverter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

typealias PictureListener = (Bitmap?) -> Unit

class Photographer(private val videoView: VideoView) : VideoProcessor {
    private val pictureRequest = AtomicReference<PictureListener?>(null)

    /**
     * These methods are part of the [tvi.webrtc.VideoProcessor] API, but are not required to be
     * implemented for this example.
     */
    override fun onCapturerStopped() {}
    override fun onCapturerStarted(success: Boolean) {}
    override fun setSink(videoSink: VideoSink?) {}
    override fun onFrameCaptured(frame: VideoFrame?) {}

    /**
     * This onFrameCaptured method provides an unadapted [tvi.webrtc.VideoFrame].
     *
     * The following code demonstrates how to capture the unadapted frame to a [Bitmap] and then
     * forward the adapted frame to a [VideoView].
     */
    override fun onFrameCaptured(
        frame: VideoFrame?,
        parameters: VideoProcessor.FrameAdaptationParameters?
    ) {
        requireNotNull(frame)
        frame.retain()

        /**
         * Get the picture request and then capture the current frame to a Bitmap. The
         * picture request is cleared for subsequent calls to [takePicture].
         */
        pictureRequest.getAndSet(null)?.invoke(captureBitmap(frame))

        /**
         * Adapt the current frame and forward to the video view.
         */
        VideoProcessor.applyFrameAdaptationParameters(frame, parameters)?.let {
            videoView.onFrame(it)
            it.release()
        } ?: videoView.onFrame(frame)
        frame.release()
    }

    fun takePicture(pictureListener: PictureListener) {
        this.pictureRequest.set(pictureListener)
    }

    private fun captureBitmap(frame: VideoFrame) : Bitmap? {
        return if (frame.buffer is TextureBuffer) {
            captureBitmapFromTexture(frame)
        } else {
            captureBitmapFromYuvFrame(frame)
        }
    }

    private fun captureBitmapFromYuvFrame(videoFrame: VideoFrame): Bitmap? {
        val yuvImage = i420ToYuvImage(
            videoFrame.buffer.toI420(),
            videoFrame.buffer.width,
            videoFrame.buffer.height
        )
        val stream = ByteArrayOutputStream()
        val rect =
            Rect(0, 0, yuvImage.width, yuvImage.height)

        // Compress YuvImage to jpeg
        yuvImage.compressToJpeg(rect, 100, stream)

        // Convert jpeg to Bitmap
        val imageBytes = stream.toByteArray()
        var bitmap: Bitmap?
        bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val buffer = ByteBuffer.wrap(imageBytes)
            val src =
                ImageDecoder.createSource(buffer)
            try {
                ImageDecoder.decodeBitmap(src)
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        } else {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }
        val matrix = Matrix()

        // Apply any needed rotation
        matrix.postRotate(videoFrame.rotation.toFloat())
        bitmap = Bitmap.createBitmap(
            bitmap!!, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        return bitmap
    }

    private fun captureBitmapFromTexture(videoFrame: VideoFrame): Bitmap? {
        val width = videoFrame.rotatedWidth
        val height = videoFrame.rotatedHeight

        /**
         * YuvConverter must be instantiated on a thread that has an active EGL context.
         * [onFrameCaptured] is called from the correct render thread therefore defer
         * instantiation of the converter until frame arrives.
         */
        val yuvConverter = YuvConverter()
        val i420Buffer = yuvConverter.convert(videoFrame.buffer as TextureBuffer)
        val yuvImage = i420ToYuvImage(i420Buffer, width, height)
        val stream = ByteArrayOutputStream()
        val rect =
            Rect(0, 0, yuvImage.width, yuvImage.height)

        // Compress YuvImage to jpeg
        yuvImage.compressToJpeg(rect, 100, stream)

        // Convert jpeg to Bitmap
        val imageBytes = stream.toByteArray()

        // Release YUV Converter
        yuvConverter.release()
        val bitmap: Bitmap?
        bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val buffer = ByteBuffer.wrap(imageBytes)
            val src =
                ImageDecoder.createSource(buffer)
            try {
                ImageDecoder.decodeBitmap(src)
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        } else {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }
        return bitmap
    }

    private fun i420ToYuvImage(i420Buffer: I420Buffer, width: Int, height: Int): YuvImage {
        val yuvPlanes = arrayOf(
            i420Buffer.dataY, i420Buffer.dataU, i420Buffer.dataV
        )
        val yuvStrides = intArrayOf(
            i420Buffer.strideY, i420Buffer.strideU, i420Buffer.strideV
        )
        if (yuvStrides[0] != width) {
            return fastI420ToYuvImage(yuvPlanes, yuvStrides, width, height)
        }
        if (yuvStrides[1] != width / 2) {
            return fastI420ToYuvImage(yuvPlanes, yuvStrides, width, height)
        }
        if (yuvStrides[2] != width / 2) {
            return fastI420ToYuvImage(yuvPlanes, yuvStrides, width, height)
        }
        val bytes = ByteArray(
            yuvStrides[0] * height + yuvStrides[1] * height / 2 + yuvStrides[2] * height / 2
        )
        var tmp = ByteBuffer.wrap(bytes, 0, width * height)
        copyPlane(yuvPlanes[0], tmp)
        val tmpBytes = ByteArray(width / 2 * height / 2)
        tmp = ByteBuffer.wrap(tmpBytes, 0, width / 2 * height / 2)
        copyPlane(yuvPlanes[2], tmp)
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                bytes[width * height + row * width + col * 2] =
                    tmpBytes[row * width / 2 + col]
            }
        }
        copyPlane(yuvPlanes[1], tmp)
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                bytes[width * height + row * width + col * 2 + 1] =
                    tmpBytes[row * width / 2 + col]
            }
        }
        return YuvImage(bytes, ImageFormat.NV21, width, height, null)
    }

    private fun fastI420ToYuvImage(
        yuvPlanes: Array<ByteBuffer>, yuvStrides: IntArray, width: Int, height: Int
    ): YuvImage {
        val bytes = ByteArray(width * height * 3 / 2)
        var i = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                bytes[i++] = yuvPlanes[0].get(col + row * yuvStrides[0])
            }
        }
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                bytes[i++] = yuvPlanes[2].get(col + row * yuvStrides[2])
                bytes[i++] = yuvPlanes[1].get(col + row * yuvStrides[1])
            }
        }
        return YuvImage(bytes, ImageFormat.NV21, width, height, null)
    }

    private fun copyPlane(src: ByteBuffer, dst: ByteBuffer) {
        src.position(0).limit(src.capacity())
        dst.put(src)
        dst.position(0).limit(dst.capacity())
    }
}