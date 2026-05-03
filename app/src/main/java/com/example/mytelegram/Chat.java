package com.example.mytelegram;

import android.text.TextUtils;

public class Chat {

    // Основные поля
    private String chatId;
    private String participantId;
    private String participantName;
    private String participantAvatar;
    private String lastMessage;
    private long timestamp;
    private int unreadCount;
    private String lastMessageSenderId;
    private long lastReadTime;

    private boolean isOnline;
    private String lastMessageTime;
    private boolean lastMessageMine;

    // Поля для групповых чатов
    private String chatType;           // "private" или "group"
    private String groupId;            // ID группы
    private String groupName;          // Название группы
    private String messageType;        // Тип последнего сообщения (text, image, video, etc.)
    private boolean isGroup;           // Флаг группы
    private boolean online;

    // Конструкторы
    public Chat() {

    }

    public Chat(String chatId, String participantId, String participantName,
                String lastMessage, long timestamp, int unreadCount) {
        this.chatId = chatId;
        this.participantId = participantId;
        this.participantName = participantName;
        this.lastMessage = lastMessage;
        this.timestamp = timestamp;
        this.unreadCount = unreadCount;
        this.chatType = "private";
    }

    // ========== Основные геттеры и сеттеры ==========

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }


    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public String getParticipantName() {
        return participantName;
    }

    public void setParticipantName(String participantName) {
        this.participantName = participantName;
    }

    public String getParticipantAvatar() {
        return participantAvatar;
    }

    public void setParticipantAvatar(String participantAvatar) {
        this.participantAvatar = participantAvatar;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public String getLastMessageSenderId() {
        return lastMessageSenderId;
    }

    public void setLastMessageSenderId(String lastMessageSenderId) {
        this.lastMessageSenderId = lastMessageSenderId;
    }

    public long getLastReadTime() {
        return lastReadTime;
    }

    public void setLastReadTime(long lastReadTime) {
        this.lastReadTime = lastReadTime;
    }

    public String getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(String lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public boolean isLastMessageMine() {
        return lastMessageMine;
    }

    public void setLastMessageMine(boolean lastMessageMine) {
        this.lastMessageMine = lastMessageMine;
    }

    // ========== Геттеры и сеттеры для групповых чатов ==========

    public String getChatType() {
        return chatType;
    }

    public void setChatType(String chatType) {
        this.chatType = chatType;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public void setGroup(boolean group) {
        isGroup = group;
    }

    // ========== Вспомогательные методы ==========

    /**
     * Проверяет, является ли чат групповым
     */
    public boolean isGroupChat() {
        return "group".equals(chatType);
    }

    /**
     * Возвращает отображаемое имя (для группы - название группы, для личного чата - имя собеседника)
     */
    public String getDisplayName() {
        if (isGroupChat()) {
            return groupName != null ? groupName : "Группа";
        } else {
            return participantName != null ? participantName : "Пользователь";
        }
    }

    /**
     * Возвращает URL аватара
     */
    public String getDisplayAvatar() {
        if (participantAvatar != null && !participantAvatar.isEmpty()) {
            return participantAvatar;
        }
        return "";
    }

    /**
     * Проверяет, было ли последнее сообщение отправлено текущим пользователем
     */
    public boolean isLastMessageFromMe(String currentUserId) {
        return currentUserId != null && currentUserId.equals(lastMessageSenderId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Chat chat = (Chat) obj;
        return chatId != null && chatId.equals(chat.chatId);
    }

    @Override
    public int hashCode() {
        return chatId != null ? chatId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Chat{" +
                "chatId='" + chatId + '\'' +
                ", displayName='" + getDisplayName() + '\'' +
                ", lastMessage='" + lastMessage + '\'' +
                ", timestamp=" + timestamp +
                ", unreadCount=" + unreadCount +
                ", chatType='" + chatType + '\'' +
                '}';
    }








    // В классе Chat.java добавьте:
    public Chat copy() {
        Chat copy = new Chat();
        copy.setChatId(this.chatId);
        copy.setChatType(this.chatType);
        copy.setParticipantId(this.participantId);
        copy.setParticipantName(this.participantName);
        copy.setParticipantAvatar(this.participantAvatar);
        copy.setGroupId(this.groupId);
        copy.setGroupName(this.groupName);
        copy.setLastMessage(this.lastMessage);
        copy.setTimestamp(this.timestamp);
        copy.setUnreadCount(this.unreadCount);
        copy.setLastMessageSenderId(this.lastMessageSenderId);
        copy.setLastMessageMine(this.lastMessageMine);
        copy.setLastMessageTime(this.lastMessageTime);
        copy.setMessageType(this.messageType);
        copy.setOnline(this.online);
        return copy;
    }

    public boolean hasChanges(Chat other) {
        if (other == null) return true;

        return !TextUtils.equals(this.lastMessage, other.lastMessage)
                || this.timestamp != other.timestamp
                || this.unreadCount != other.unreadCount
                || !TextUtils.equals(this.participantName, other.participantName)
                || !TextUtils.equals(this.participantAvatar, other.participantAvatar)
                || this.online != other.online;
    }

    /**
     * Сбрасывает флаг изменений (обновляет кэшированные значения)
     */
   
}