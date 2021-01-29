package com.twilio.video.quickstart.kotlin

import android.media.MediaCodecInfo
import android.util.Log
import tvi.webrtc.MediaCodecVideoEncoder.BitrateAdjustmentType
import java.lang.reflect.Field


val TAG = "CodecTest"

object CodecTest {


    // Replace Qualcomm with HiSilicon H264 Codec Properties Field
    fun setHiSiliconCodec() {
        try {
            setHiSiliconH264Encoder()
            setHiSiliconH264Decoder()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add HiSilicon H264 support", e);
        }
    }

    private fun setHiSiliconH264Encoder() {
        // Get Qualcomm H264 Codec Properties Field
        val mediaCodecVideoEncoderClass = Class.forName("tvi.webrtc.MediaCodecVideoEncoder")
        val qcomH264HwPropertiesField: Field = mediaCodecVideoEncoderClass.getDeclaredField("qcomH264HwProperties")
        qcomH264HwPropertiesField.setAccessible(true)
        val qcomH264HwProperties = Any()
        qcomH264HwPropertiesField.get(qcomH264HwProperties)

        // Create new entry with HiSilicon details
        val codecPrefix = "OMX.hisi."
        val colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        val bitrateAdjustmentType = BitrateAdjustmentType.DYNAMIC_ADJUSTMENT
        val mediaCodecPropertiesClass = Class.forName("tvi.webrtc.MediaCodecVideoEncoder\$MediaCodecProperties")
        val mediaCodecPropertiesCtor = mediaCodecPropertiesClass
                .getDeclaredConstructor(String::class.java,
                        Int::class.javaPrimitiveType,
                        BitrateAdjustmentType::class.java)
        mediaCodecPropertiesCtor.isAccessible = true
        val intelH264HwProperties: Any = mediaCodecPropertiesCtor.newInstance(codecPrefix,
                colorFormat,
                bitrateAdjustmentType)

        // Replace Qualcomm properties field with HiSilicon properties
        qcomH264HwPropertiesField.set(qcomH264HwProperties, intelH264HwProperties)
    }

    private fun setHiSiliconH264Decoder() {
        // Get Qualcomm H264 Codec Properties Field
        val mediaCodecVideoEncoderClass = Class.forName("tvi.webrtc.MediaCodecVideoDecoder")
        val supportedH264HwCodecPrefixesField: Field = mediaCodecVideoEncoderClass.getDeclaredField("supportedH264HwCodecPrefixes")
        supportedH264HwCodecPrefixesField.isAccessible = true
        val newSupportedList = arrayOf("OMX.hisi.")
        val oldList = supportedH264HwCodecPrefixesField.get(mediaCodecVideoEncoderClass)
        supportedH264HwCodecPrefixesField.set(oldList, newSupportedList)
    }

}