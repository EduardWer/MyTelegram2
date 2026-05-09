package com.example.mytelegram;

import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

public class Participant {
    private String userId;
    private String userName;
    private boolean audioEnabled;
    private boolean videoEnabled;
    private boolean isSpeaking = false;
    private boolean isLocal = false;
    private VideoTrack videoTrack;
    private SurfaceViewRenderer videoRenderer;

    public Participant(String userId, String userName, boolean audioEnabled, boolean videoEnabled) {
        this.userId = userId;
        this.userName = userName;
        this.audioEnabled = audioEnabled;
        this.videoEnabled = videoEnabled;
    }

    // Getters
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public boolean isAudioEnabled() { return audioEnabled; }
    public boolean isVideoEnabled() { return videoEnabled; }
    public boolean isSpeaking() { return isSpeaking; }
    public boolean isLocal() { return isLocal; }
    public VideoTrack getVideoTrack() { return videoTrack; }
    public SurfaceViewRenderer getVideoRenderer() { return videoRenderer; }

    // Setters
    public void setAudioEnabled(boolean enabled) { this.audioEnabled = enabled; }
    public void setVideoEnabled(boolean enabled) { this.videoEnabled = enabled; }
    public void setSpeaking(boolean speaking) { isSpeaking = speaking; }
    public void setIsLocal(boolean local) { isLocal = local; }
    public void setVideoTrack(VideoTrack track) { this.videoTrack = track; }
    public void setVideoRenderer(SurfaceViewRenderer renderer) { this.videoRenderer = renderer; }
}