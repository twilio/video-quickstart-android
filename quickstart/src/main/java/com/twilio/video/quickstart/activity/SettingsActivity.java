package com.twilio.video.quickstart.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.view.MenuItem;

import com.twilio.video.AudioCodec;
import com.twilio.video.G722Codec;
import com.twilio.video.H264Codec;
import com.twilio.video.IsacCodec;
import com.twilio.video.OpusCodec;
import com.twilio.video.PcmaCodec;
import com.twilio.video.PcmuCodec;
import com.twilio.video.VideoCodec;
import com.twilio.video.Vp8Codec;
import com.twilio.video.Vp9Codec;
import com.twilio.video.quickstart.R;

import org.webrtc.MediaCodecVideoDecoder;
import org.webrtc.MediaCodecVideoEncoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    public static final String PREF_AUDIO_CODEC = "audio_codec";
    public static final String PREF_AUDIO_CODEC_DEFAULT = OpusCodec.NAME;
    public static final String PREF_VIDEO_CODEC = "video_codec";
    public static final String PREF_VIDEO_CODEC_DEFAULT = Vp8Codec.NAME;
    public static final String PREF_SENDER_MAX_AUDIO_BITRATE = "sender_max_audio_bitrate";
    public static final String PREF_SENDER_MAX_AUDIO_BITRATE_DEFAULT = "0";
    public static final String PREF_SENDER_MAX_VIDEO_BITRATE = "sender_max_video_bitrate";
    public static final String PREF_SENDER_MAX_VIDEO_BITRATE_DEFAULT = "0";
    public static final String PREF_VP8_SIMULCAST = "vp8_simulcast";
    public static final boolean PREF_VP8_SIMULCAST_DEFAULT = false;

    private static final String[] VIDEO_CODEC_NAMES = new String[] {
            Vp8Codec.NAME, H264Codec.NAME, Vp9Codec.NAME
    };

    private static final String[] AUDIO_CODEC_NAMES = new String[] {
            IsacCodec.NAME, OpusCodec.NAME, PcmaCodec.NAME, PcmuCodec.NAME, G722Codec.NAME
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SettingsFragment settingsFragment = SettingsFragment.newInstance(sharedPreferences);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, settingsFragment)
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private SharedPreferences sharedPreferences;

        public static SettingsFragment newInstance(SharedPreferences sharedPreferences) {
            SettingsFragment settingsFragment = new SettingsFragment();
            settingsFragment.sharedPreferences = sharedPreferences;

            return settingsFragment;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.settings);
            setHasOptionsMenu(true);
            setupCodecListPreference(AudioCodec.class,
                    PREF_AUDIO_CODEC,
                    PREF_AUDIO_CODEC_DEFAULT,
                    (ListPreference) findPreference(PREF_AUDIO_CODEC));
            setupCodecListPreference(VideoCodec.class,
                    PREF_VIDEO_CODEC,
                    PREF_VIDEO_CODEC_DEFAULT,
                    (ListPreference) findPreference(PREF_VIDEO_CODEC));
            setupSenderBandwidthPreferences(PREF_SENDER_MAX_AUDIO_BITRATE,
                    PREF_SENDER_MAX_AUDIO_BITRATE_DEFAULT,
                    (EditTextPreference) findPreference(PREF_SENDER_MAX_AUDIO_BITRATE));
            setupSenderBandwidthPreferences(PREF_SENDER_MAX_VIDEO_BITRATE,
                    PREF_SENDER_MAX_VIDEO_BITRATE_DEFAULT,
                    (EditTextPreference) findPreference(PREF_SENDER_MAX_VIDEO_BITRATE));
        }



        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        private void setupCodecListPreference(Class codecClass,
                                              String key,
                                              String defaultValue,
                                              ListPreference preference) {
            List<String> codecEntries = new ArrayList<>();
            if(codecClass == AudioCodec.class){
                Collections.addAll(codecEntries, AUDIO_CODEC_NAMES);
            }else{
                Collections.addAll(codecEntries, VIDEO_CODEC_NAMES);
            }

            // Remove H264 if not supported
            if (!MediaCodecVideoDecoder.isH264HwSupported() ||
                    !MediaCodecVideoEncoder.isH264HwSupported()) {
                codecEntries.remove(H264Codec.NAME);
            }
            String[] codecStrings = codecEntries.toArray(new String[codecEntries.size()]);

            // Saved value
            final String value = sharedPreferences.getString(key, defaultValue);

            // Bind values
            preference.setEntries(codecStrings);
            preference.setEntryValues(codecStrings);
            preference.setValue(value);
            preference.setSummary(value);
            preference.setOnPreferenceChangeListener((preference1, newValue) -> {
                preference1.setSummary(newValue.toString());
                return true;
            });
        }

        private void setupSenderBandwidthPreferences(String key,
                                                     String defaultValue,
                                                     EditTextPreference editTextPreference) {
            String value = sharedPreferences.getString(key, defaultValue);

            // Set layout with input type number for edit text
            editTextPreference.setDialogLayoutResource(R.layout.preference_dialog_number_edittext);
            editTextPreference.setSummary(value);
            editTextPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                preference.setSummary(newValue.toString());
                return true;
            });
        }
    }
}
