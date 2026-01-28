package com.example.mytelegram;

public class Chat {
    private String chatId;
    private String participantId;
    private String participantName; // Добавьте это поле
    private String participantAvatar; // Добавьте это поле
    private String lastMessage;
    private long timestamp;
    private int unreadCount;
    private String lastMessageSenderId;
    private long lastReadTime;
    private String lastMessageTime;
    private boolean lastMessageMine;

    public boolean isLastMessageMine() { return lastMessageMine; }

    public Chat() {
        // Пустой конструктор для Firebase
    }

    // Геттеры и сеттеры
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getParticipantId() { return participantId; }
    public void setParticipantId(String participantId) { this.participantId = participantId; }

    public String getParticipantName() { return participantName; }
    public void setParticipantName(String participantName) { this.participantName = participantName; }

    public String getParticipantAvatar() { return participantAvatar; }
    public void setParticipantAvatar(String participantAvatar) { this.participantAvatar = participantAvatar; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    public String getLastMessageSenderId() { return lastMessageSenderId; }
    public void setLastMessageSenderId(String lastMessageSenderId) { this.lastMessageSenderId = lastMessageSenderId; }



    public void setLastMessageMine(boolean lastMessageMine) { this.lastMessageMine = lastMessageMine; }


    public void setLastMessageTime(String lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }


    public String getLastMessageTime() {
        return lastMessageTime;
    }
}