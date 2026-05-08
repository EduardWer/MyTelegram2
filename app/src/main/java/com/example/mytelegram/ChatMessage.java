package com.example.mytelegram;

public class ChatMessage {
    private String userId;
    private String userName;
    private String message;
    private long timestamp;

    public ChatMessage(String userId, String userName, String message, long timestamp) {
        this.userId = userId;
        this.userName = userName;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
}