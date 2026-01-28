package com.example.mytelegram;

import android.os.Parcel;
import android.os.Parcelable;

public class User implements Parcelable {
    private String uid;
    private String email;
    private String username;
    private String bio;
    private String getAvatarUrl; // URL аватара/фото пользователя
    private long registrationDate;
    private boolean online;
    private long lastSeen;
    private String position;    // Должность
    private String role;        // Роль
    private String department;  // Отдел
    private String lastMessage;

    private boolean emailVerified = false;

    // Пустой конструктор для Firebase
    public User() {
    }

    // Основной конструктор
    public User(String username, String email, long registrationDate) {
        this.username = username;
        this.email = email;
        this.registrationDate = registrationDate;
        this.online = false;
        this.lastSeen = System.currentTimeMillis();
        this.emailVerified = false;

    }

    // Расширенный конструктор с новыми полями
    public User(String username, String email, long registrationDate, String position, String role, String department,boolean emailVerified ) {
        this.username = username;
        this.email = email;
        this.registrationDate = registrationDate;
        this.position = position;
        this.role = role;
        this.department = department;
        this.online = false;
        this.lastSeen = System.currentTimeMillis();
        this.emailVerified = emailVerified;
    }

    // Конструктор для Parcelable
    protected User(Parcel in) {
        uid = in.readString();
        email = in.readString();
        username = in.readString();
        bio = in.readString();
        getAvatarUrl = in.readString();
        registrationDate = in.readLong();
        online = in.readByte() != 0;
        lastSeen = in.readLong();
        position = in.readString();
        role = in.readString();
        lastMessage = in.readString();
        department = in.readString();
        this.emailVerified = false;
    }

    // Геттеры и сеттеры
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getBio() {
        return bio;
    }



    public void setAvatarUrls(String avatarUrl) {
        this.getAvatarUrl = avatarUrl;
    }

    public String getId() {
        return uid; // добавьте это поле если его нет
    }

    public String getAvatarUrls() {
        return getAvatarUrl;
    }

    public void setAvatarUrl(String getAvatarUrl) {
        this.getAvatarUrl = getAvatarUrl;
    }

    public long getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(long registrationDate) {
        this.registrationDate = registrationDate;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    // Геттеры и сеттеры для новых полей
    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    // Parcelable реализация
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(uid);
        dest.writeString(email);
        dest.writeString(username);
        dest.writeString(bio);
        dest.writeString(getAvatarUrl);
        dest.writeLong(registrationDate);
        dest.writeByte((byte) (online ? 1 : 0));
        dest.writeLong(lastSeen);
        dest.writeString(position);
        dest.writeString(role);
        dest.writeString(department);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<User> CREATOR = new Creator<User>() {
        @Override
        public User createFromParcel(Parcel in) {
            return new User(in);
        }

        @Override
        public User[] newArray(int size) {
            return new User[size];
        }
    };

    // Дополнительные методы
    public String getLastSeenString() {
        if (online) {
            return "online";
        } else {
            long diff = System.currentTimeMillis() - lastSeen;
            long minutes = diff / (60 * 1000);

            if (minutes < 60) {
                return minutes + " мин. назад";
            } else if (minutes < 24 * 60) {
                return (minutes / 60) + " ч. назад";
            } else {
                return (minutes / (60 * 24)) + " дн. назад";
            }
        }
    }

    // Новые методы для работы с аватаром
    public boolean hasPhoto() {
        return getAvatarUrl != null && !getAvatarUrl.isEmpty();
    }

    public String getPhotoUrlOrDefault(String defaultUrl) {
        return hasPhoto() ? getAvatarUrl : defaultUrl;
    }

    // Дополнительные методы для новых полей
    public boolean hasPosition() {
        return position != null && !position.isEmpty();
    }

    public boolean hasRole() {
        return role != null && !role.isEmpty();
    }

    public boolean hasDepartment() {
        return department != null && !department.isEmpty();
    }

    public String getPositionOrDefault(String defaultPosition) {
        return hasPosition() ? position : defaultPosition;
    }

    public String getRoleOrDefault(String defaultRole) {
        return hasRole() ? role : defaultRole;
    }

    public String getDepartmentOrDefault(String defaultDepartment) {
        return hasDepartment() ? department : defaultDepartment;
    }

    public void setId(String uid) {
        this.uid = uid;
    }


    public void setBio(String bio) {
        this.bio = bio;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getLastMessage() {
        return lastMessage;
    }
}
