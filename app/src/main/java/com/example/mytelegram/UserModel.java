package com.example.mytelegram;

public class UserModel {
    private String uid;
    private String username;
    private String status;
    private String phone;
    private String avatarUrl;

    public UserModel() {}

    public UserModel(String uid, String username, String status, String phone) {
        this.uid = uid;
        this.username = username;
        this.status = status;
        this.phone = phone;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}