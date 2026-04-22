package com.example.mytelegram;

import java.util.HashMap;
import java.util.Map;

public class Message implements Comparable<Message> {
    private String id;
    private String text;
    private String senderId;
    private String recipientId;
    private long timestamp;
    private String chatId;
    private String messageType = "text"; // text, image, video, document

    // Поля для файлов
    private String fileUrl;
    private String fileName;
    private long fileSize;

    // Поля для статуса прочтения
    private boolean isRead = false;
    private Map<String, Boolean> readBy = new HashMap<>();


    private boolean edited;
    private long editedAt;



    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    public long getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(long editedAt) {
        this.editedAt = editedAt;
    }

    // Конструкторы
    public Message() {}

    public Message(String id, String text, String senderId, String recipientId,
                   long timestamp, String chatId) {
        this.id = id;
        this.text = text;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.timestamp = timestamp;
        this.chatId = chatId;
    }

    public Message(String id, String text, String senderId, String recipientId,
                   long timestamp, String chatId, String messageType) {
        this(id, text, senderId, recipientId, timestamp, chatId);
        this.messageType = messageType;
    }

    // Геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public Map<String, Boolean> getReadBy() { return readBy; }
    public void setReadBy(Map<String, Boolean> readBy) { this.readBy = readBy; }

    // Вспомогательные методы
    public boolean isTextMessage() {
        return "text".equals(messageType);
    }

    public boolean isImageMessage() {
        return "image".equals(messageType);
    }

    public boolean isVideoMessage() {
        return "video".equals(messageType);
    }

    public boolean isDocumentMessage() {
        return "document".equals(messageType);
    }

    public boolean isReadByUser(String userId) {
        return readBy != null && readBy.containsKey(userId) && Boolean.TRUE.equals(readBy.get(userId));
    }

    public void markAsRead(String userId) {
        if (readBy == null) {
            readBy = new HashMap<>();
        }
        readBy.put(userId, true);

        // Проверяем, прочитано ли всеми участниками
        updateReadStatus();
    }

    private void updateReadStatus() {
        if (readBy != null && !readBy.isEmpty()) {
            // Логика для определения, прочитано ли сообщение всеми участниками
            // В простом случае считаем, что если прочитал хотя бы один кроме отправителя - isRead = true
            for (Map.Entry<String, Boolean> entry : readBy.entrySet()) {
                if (!entry.getKey().equals(senderId) && entry.getValue()) {
                    isRead = true;
                    return;
                }
            }
        }
        isRead = false;
    }

    @Override
    public int compareTo(Message other) {
        return Long.compare(this.timestamp, other.timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return id != null && id.equals(message.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}