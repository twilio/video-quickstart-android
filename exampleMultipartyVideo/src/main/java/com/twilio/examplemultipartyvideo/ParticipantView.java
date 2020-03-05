package com.twilio.examplemultipartyvideo;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.twilio.video.VideoTextureView;

public class ParticipantView extends RelativeLayout {
    private VideoTextureView videoView;
    private ImageView dominantSpeakerImg;
    private ImageView networkQualityLevelImageView;

    public ParticipantView(Context context) {
        super(context);
    }

    public ParticipantView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ParticipantView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private void init() {
        videoView = findViewById(R.id.video_view);
        dominantSpeakerImg = findViewById(R.id.dominant_speaker_img);
        networkQualityLevelImageView = findViewById(R.id.network_quality_level);
    }

    public VideoTextureView getVideoView() {
        return videoView;
    }

    public ImageView getDominantSpeakerImg() {
        return dominantSpeakerImg;
    }

    public ImageView getNetworkQualityLevelImageView() { return networkQualityLevelImageView; }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        init();
    }
}
