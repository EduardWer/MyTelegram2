package com.example.mytelegram;

public class Participant {
    private String userId;
    private String userName;
    private boolean audioEnabled;
    private boolean videoEnabled;

    public Participant(String userId, String userName, boolean audioEnabled, boolean videoEnabled) {
        this.userId = userId;
        this.userName = userName;
        this.audioEnabled = audioEnabled;
        this.videoEnabled = videoEnabled;
    }

    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public boolean isAudioEnabled() { return audioEnabled; }
    public void setAudioEnabled(boolean audioEnabled) { this.audioEnabled = audioEnabled; }
    public boolean isVideoEnabled() { return videoEnabled; }
    public void setVideoEnabled(boolean videoEnabled) { this.videoEnabled = videoEnabled; }
}