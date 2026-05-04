package com.example.mytelegram;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

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

    /**
     * Создание и показ уведомления о входящем звонке
     */
    public static void createIncomingCallNotification(Context context, 
                                                      CallManager.CallInfo callInfo,
                                                      Intent intent) {
        NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // PendingIntent для открытия экрана входящего звонка
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                callInfo.callId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // PendingIntent для отклонения звонка
        Intent declineIntent = new Intent(context, CallActionReceiver.class);
        declineIntent.putExtra("action", "DECLINE");
        declineIntent.putExtra("call_id", callInfo.callId);
        PendingIntent declinePendingIntent = PendingIntent.getBroadcast(
                context,
                callInfo.callId.hashCode() + 1,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Строка с типом звонка
        String callTypeString = callInfo.isVideo ? "Видеозвонок" : "Аудиозвонок";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle(callInfo.callerName)
                .setContentText(callTypeString + " от " + callInfo.callerName)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .addAction(
                        R.drawable.ic_call,
                        "Ответить",
                        pendingIntent
                )
                .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Отклонить",
                        declinePendingIntent
                );

        // Показываем уведомление
        notificationManager.notify(callInfo.callId.hashCode(), builder.build());
    }
}