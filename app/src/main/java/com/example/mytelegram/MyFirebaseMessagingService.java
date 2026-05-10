package com.example.mytelegram;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.media.RingtoneManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_CALLS = "calls";
    private static final String CHANNEL_MESSAGES = "chat_messages";
    private static final String CHANNEL_CONFERENCE = "conference_invites";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        Log.d(TAG, "Получено сообщение. Data: " + data.toString());

        String type = data.get("type");

        if ("call".equals(type)) {
            handleCallNotification(data);
        } else if ("conference_invite".equals(type)) {
            handleConferenceInvite(data);
        } else {
            handleMessageNotification(data);
        }
    }

    // ==================== ПРИГЛАШЕНИЕ В КОНФЕРЕНЦИЮ ====================

    private void handleConferenceInvite(Map<String, String> data) {
        String roomCode = data.get("room_code");
        String inviterName = data.get("inviterName");
        String inviterId = data.get("inviterId");
        String title = data.get("title");
        String body = data.get("body");

        Log.d(TAG, "📞 Приглашение в конференцию: room=" + roomCode + ", from=" + inviterName);

        if (roomCode == null || roomCode.isEmpty()) return;

        createNotificationChannels();
        showConferenceInviteNotification(title, body, roomCode, inviterName, inviterId);
    }

    private void showConferenceInviteNotification(String title, String body,
                                                  String roomCode, String inviterName, String inviterId) {
        Intent intent = new Intent(this, ConferenceActivity.class);
        intent.putExtra("auto_join_room", roomCode);
        intent.putExtra("inviter_name", inviterName);
        intent.putExtra("inviter_id", inviterId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                roomCode.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_CONFERENCE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title != null ? title : "Приглашение в конференцию")
                .setContentText(body != null ? body : inviterName + " приглашает вас присоединиться")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(roomCode.hashCode(), builder.build());

        Log.d(TAG, "✅ Уведомление о приглашении показано");
    }

    // ==================== ЗВОНКИ ====================

    private void handleCallNotification(Map<String, String> data) {
        String callType = data.get("call_type");
        String callerName = data.get("caller_name");
        String callId = data.get("call_id");
        String roomName = data.get("room_name");
        String callerId = data.get("caller_id");
        boolean isVideo = "true".equals(data.get("is_video"));

        Log.d(TAG, "Звонок: type=" + callType + ", caller=" + callerName);

        if (callId == null) return;

        createNotificationChannels();
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if ("incoming".equals(callType)) {
            launchIncomingCallScreen(callerName, callId, roomName, isVideo, callerId);
        } else if ("cancelled".equals(callType) || "ended".equals(callType)) {
            closeCallScreen(callId);
            notificationManager.cancel(callId.hashCode());
        } else if ("missed".equals(callType)) {
            showMissedCall(notificationManager, callerName, callId, isVideo);
        }
    }

    private void launchIncomingCallScreen(String callerName, String callId,
                                          String roomName, boolean isVideo, String callerId) {
        Intent intent = new Intent(this, IncomingCallActivity.class);
        intent.putExtra("call_id", callId);
        intent.putExtra("room_name", roomName);
        intent.putExtra("caller_name", callerName);
        intent.putExtra("caller_id", callerId);
        intent.putExtra("is_video", isVideo);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        );

        startActivity(intent);
        Log.d(TAG, "Запущен экран входящего звонка");
    }

    private void closeCallScreen(String callId) {
        Intent intent = new Intent("CLOSE_CALL_SCREEN");
        intent.putExtra("call_id", callId);
        sendBroadcast(intent);
    }

    private void showMissedCall(NotificationManager manager,
                                String callerName, String callId, boolean isVideo) {
        int notificationId = callId.hashCode() + 1;
        String callText = isVideo ? "Пропущенный видеозвонок" : "Пропущенный звонок";

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(callerName)
                .setContentText(callText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        manager.notify(notificationId, builder.build());
    }

    // ==================== СООБЩЕНИЯ ====================

    private void handleMessageNotification(Map<String, String> data) {
        String title = data.containsKey("title") ? data.get("title") : null;
        String body = data.containsKey("body") ? data.get("body") : "";
        String chatId = data.get("chat_id");
        String senderId = data.get("sender_id");

        Log.d(TAG, "Сообщение: title=" + title + ", body=" + body + ", chatId=" + chatId);

        if (chatId == null || chatId.isEmpty()) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null && senderId != null) {
                String uid = currentUser.getUid();
                chatId = (uid.compareTo(senderId) < 0) ? (uid + "_" + senderId) : (senderId + "_" + uid);
            }
        }

        final String finalChatId = chatId;

        if (isUserInThisChat(finalChatId)) {
            Log.d(TAG, "Пользователь в чате " + finalChatId + ", уведомление НЕ показываем");
            notifyChatActivityNewMessage(title, body, finalChatId, senderId);
            return;
        }

        Log.d(TAG, "Пользователь НЕ в чате, показываем уведомление");
        createNotificationChannels();
        loadAvatarAndShowNotification(title, body, finalChatId, senderId);
    }

    private boolean isUserInThisChat(String chatId) {
        return ChatActivity.isVisible() &&
                ChatActivity.getCurrentChatId() != null &&
                ChatActivity.getCurrentChatId().equals(chatId);
    }

    private void notifyChatActivityNewMessage(String title, String body, String chatId, String senderId) {
        Intent intent = new Intent("NEW_MESSAGE_RECEIVED");
        intent.putExtra("chat_id", chatId);
        intent.putExtra("title", title);
        intent.putExtra("body", body);
        intent.putExtra("sender_id", senderId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Отправлен локальный broadcast о новом сообщении");
    }

    private void loadAvatarAndShowNotification(String title, String body,
                                               String chatId, String senderId) {
        if (senderId != null && !senderId.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("avatars").child(senderId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String avatarUrl = null;
                            if (snapshot.exists()) {
                                avatarUrl = snapshot.getValue(String.class);
                            }
                            showMessageNotification(title, body, chatId, senderId, avatarUrl);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            showMessageNotification(title, body, chatId, senderId, null);
                        }
                    });
        } else {
            showMessageNotification(title, body, chatId, null, null);
        }
    }

    private void showMessageNotification(String title, String body, String chatId,
                                         String senderId, String avatarUrl) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent chatIntent = new Intent(this, ChatActivity.class);
        chatIntent.putExtra("chatId", chatId);
        chatIntent.putExtra("recipientId", senderId);
        chatIntent.putExtra("recipientName", title);
        chatIntent.putExtra("recipientAvatar", avatarUrl);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntentWithParentStack(mainIntent);
        stackBuilder.addNextIntent(chatIntent);

        PendingIntent pendingIntent = stackBuilder.getPendingIntent(
                (int) System.currentTimeMillis(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title != null ? title : "Новое сообщение")
                .setContentText(body != null ? body : "")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            loadBitmapFromUrl(avatarUrl, bitmap -> {
                if (bitmap != null) {
                    Bitmap circleBitmap = getCircleBitmap(bitmap);
                    builder.setLargeIcon(circleBitmap);
                }
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                manager.notify(chatId.hashCode(), builder.build());
            });
        } else {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(chatId.hashCode(), builder.build());
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel callChannel = new NotificationChannel(
                    CHANNEL_CALLS,
                    "Звонки",
                    NotificationManager.IMPORTANCE_HIGH
            );
            callChannel.setDescription("Уведомления о входящих звонках");
            callChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);

            NotificationChannel messageChannel = new NotificationChannel(
                    CHANNEL_MESSAGES,
                    "Сообщения",
                    NotificationManager.IMPORTANCE_HIGH
            );
            messageChannel.setDescription("Уведомления о новых сообщениях");

            NotificationChannel conferenceChannel = new NotificationChannel(
                    CHANNEL_CONFERENCE,
                    "Приглашения в конференцию",
                    NotificationManager.IMPORTANCE_HIGH
            );
            conferenceChannel.setDescription("Приглашения присоединиться к конференции");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(callChannel);
            manager.createNotificationChannel(messageChannel);
            manager.createNotificationChannel(conferenceChannel);
        }
    }

    private Bitmap getCircleBitmap(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return output;
    }

    private void loadBitmapFromUrl(String urlString, BitmapCallback callback) {
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                InputStream input = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(input);
                input.close();
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки аватара: " + e.getMessage());
            }

            Bitmap finalBitmap = bitmap;
            new android.os.Handler(getMainLooper()).post(() -> callback.onBitmapLoaded(finalBitmap));
        }).start();
    }

    interface BitmapCallback {
        void onBitmapLoaded(Bitmap bitmap);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Новый токен: " + token);
    }
}