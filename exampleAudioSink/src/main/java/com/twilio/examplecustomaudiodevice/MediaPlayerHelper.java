package com.twilio.examplecustomaudiodevice;

import android.media.AudioManager;
import android.media.MediaPlayer;

import java.io.IOException;

class MediaPlayerHelper {

    private MediaPlayer player;
    private boolean isReleased = false;

    boolean isPlaying() {
        if (player == null || isReleased) {
            return false;
        }
        return player.isPlaying();
    }

    void playFile(String path, final MediaPlayer.OnCompletionListener listener) throws IOException {
        if (!isReleased && player != null) {
            player.release();
            player = null;
            isReleased = true;
        }
        player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setDataSource(path);
        player.setOnCompletionListener(listener);
        player.prepare();
        player.start();

        isReleased = false;
    }

    boolean stopPlaying() {
        if (player == null) return false;
        if (!player.isPlaying()) return false;
        player.stop();
        player.release();
        isReleased = true;
        return true;
    }
}
