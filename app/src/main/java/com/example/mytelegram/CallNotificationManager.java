package com.example.mytelegram;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class CallNotificationManager {

    public static final String CHANNEL_CALLS = "calls";
    public static final String CHANNEL_MESSAGES = "chat_messages";

    private final Context context;

    public CallNotificationManager(Context context) {
        this.context = context;
    }

    public void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);

            // Канал для звонков
            NotificationChannel callChannel = new NotificationChannel(
                    CHANNEL_CALLS,
                    "Звонки",
                    NotificationManager.IMPORTANCE_HIGH
            );
            callChannel.setDescription("Уведомления о входящих звонках");

            // Рингтон
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            callChannel.setSound(ringtoneUri, null);

            // Вибрация
            callChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            callChannel.enableVibration(true);

            notificationManager.createNotificationChannel(callChannel);

            // Канал для сообщений
            NotificationChannel messageChannel = new NotificationChannel(
                    CHANNEL_MESSAGES,
                    "Сообщения",
                    NotificationManager.IMPORTANCE_HIGH
            );
            messageChannel.setDescription("Уведомления о новых сообщениях");
            notificationManager.createNotificationChannel(messageChannel);
        }
    }
}