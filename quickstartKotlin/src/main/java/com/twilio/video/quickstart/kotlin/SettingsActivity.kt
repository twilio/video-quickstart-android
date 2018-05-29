package com.twilio.video.quickstart.kotlin

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.ListPreference
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.view.MenuItem
import com.twilio.video.*
import org.webrtc.MediaCodecVideoDecoder
import org.webrtc.MediaCodecVideoEncoder
import java.util.*

class SettingsActivity : AppCompatActivity() {
    companion object {
        const val PREF_AUDIO_CODEC = "audio_codec"
        const val PREF_AUDIO_CODEC_DEFAULT = OpusCodec.NAME
        const val PREF_VIDEO_CODEC = "video_codec"
        const val PREF_VIDEO_CODEC_DEFAULT = Vp8Codec.NAME
        const val PREF_SENDER_MAX_AUDIO_BITRATE = "sender_max_audio_bitrate"
        const val PREF_SENDER_MAX_AUDIO_BITRATE_DEFAULT = "0"
        const val PREF_SENDER_MAX_VIDEO_BITRATE = "sender_max_video_bitrate"
        const val PREF_SENDER_MAX_VIDEO_BITRATE_DEFAULT = "0"
        const val PREF_VP8_SIMULCAST = "vp8_simulcast"
        const val PREF_VP8_SIMULCAST_DEFAULT = false

        val VIDEO_CODEC_NAMES = arrayOf(Vp8Codec.NAME, H264Codec.NAME, Vp9Codec.NAME)

        val AUDIO_CODEC_NAMES = arrayOf(IsacCodec.NAME, OpusCodec.NAME, PcmaCodec.NAME,
                PcmuCodec.NAME, G722Codec.NAME)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val settingsFragment = SettingsFragment.newInstance()
        supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, settingsFragment)
                .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private val sharedPreferences by lazy {
            PreferenceManager.getDefaultSharedPreferences(activity)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.settings)
            setHasOptionsMenu(true)
            setupCodecListPreference(AudioCodec::class.java,
                    PREF_AUDIO_CODEC,
                    PREF_AUDIO_CODEC_DEFAULT,
                    findPreference(PREF_AUDIO_CODEC) as ListPreference)
            setupCodecListPreference(VideoCodec::class.java,
                    PREF_VIDEO_CODEC,
                    PREF_VIDEO_CODEC_DEFAULT,
                    findPreference(PREF_VIDEO_CODEC) as ListPreference)
            setupSenderBandwidthPreferences(PREF_SENDER_MAX_AUDIO_BITRATE,
                    PREF_SENDER_MAX_AUDIO_BITRATE_DEFAULT,
                    findPreference(PREF_SENDER_MAX_AUDIO_BITRATE) as EditTextPreference)
            setupSenderBandwidthPreferences(PREF_SENDER_MAX_VIDEO_BITRATE,
                    PREF_SENDER_MAX_VIDEO_BITRATE_DEFAULT,
                    findPreference(PREF_SENDER_MAX_VIDEO_BITRATE) as EditTextPreference)
        }


        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            return when (item.itemId) {
                android.R.id.home -> {
                    startActivity(Intent(activity, SettingsActivity::class.java))
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
        }

        private fun setupCodecListPreference(codecClass: Class<*>,
                                             key: String,
                                             defaultValue: String,
                                             listPreference: ListPreference) {
            // Set codec entries
            val codecEntries = if (codecClass == AudioCodec::class.java)
                AUDIO_CODEC_NAMES.toMutableList()
            else
                VIDEO_CODEC_NAMES.toMutableList()

            // Remove H264 if not supported
            if (!MediaCodecVideoDecoder.isH264HwSupported() ||
                    !MediaCodecVideoEncoder.isH264HwSupported()) {
                codecEntries.remove(H264Codec.NAME)
            }

            // Bind value
            val prefValue = sharedPreferences.getString(key, defaultValue)
            val codecStrings = codecEntries.toTypedArray()

            listPreference.apply {
                entries = codecStrings
                entryValues = codecStrings
                value = prefValue
                summary = prefValue
                onPreferenceChangeListener =
                        Preference.OnPreferenceChangeListener { preference, newValue ->
                            preference.summary = newValue.toString()
                            true
                        }
            }

        }

        private fun setupSenderBandwidthPreferences(key: String,
                                                    defaultValue: String,
                                                    editTextPreference: EditTextPreference) {
            val value = sharedPreferences.getString(key, defaultValue)

            // Set layout with input type number for edit text
            editTextPreference.apply {
                dialogLayoutResource = R.layout.preference_dialog_number_edittext
                summary = value
                onPreferenceChangeListener =
                        Preference.OnPreferenceChangeListener { preference, newValue ->
                            preference.summary = newValue.toString()
                            true
                        }
            }

        }

        companion object {
            fun newInstance(): SettingsFragment {
                return SettingsFragment()
            }
        }
    }
}