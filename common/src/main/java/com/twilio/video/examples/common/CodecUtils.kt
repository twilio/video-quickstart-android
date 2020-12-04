@file:JvmName("CodecUtils")
package com.twilio.video.examples.common

import tvi.webrtc.HardwareVideoDecoderFactory
import tvi.webrtc.HardwareVideoEncoderFactory

fun isH264Supported(): Boolean {
    val hardwareVideoEncoderFactory =
        HardwareVideoEncoderFactory(null, true, true)
    val hardwareVideoDecoderFactory =
        HardwareVideoDecoderFactory(null)

    val h264EncoderSupported = hardwareVideoEncoderFactory.supportedCodecs.any {
        it.name.equals("h264", ignoreCase = true)
    }
    val h264DecoderSupported = hardwareVideoDecoderFactory.supportedCodecs.any {
        it.name.equals("h264", ignoreCase = true)
    }

    return h264EncoderSupported && h264DecoderSupported
}