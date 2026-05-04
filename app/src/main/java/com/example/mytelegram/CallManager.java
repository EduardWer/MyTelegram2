package com.example.mytelegram;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CallManager - управление звонками, интеграция с Firebase
 */
public class CallManager {

    private static final String TAG = "CallManager";
    private static final String PUSH_SERVER_URL = "http://192.168.31.163:8000"; // Замените на ваш IP

    private static CallManager instance;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String currentUserId;
    private String currentUserName;

    // Текущий звонок
    private String activeCallId;
    private String activeRoomName;
    private String activeCallerId;
    private String activeCallerName;
    private boolean activeIsVideo;
    private boolean activeIsOutgoing;

    // Firebase
    private final FirebaseDatabase firebaseDatabase;
    private final FirebaseAuth firebaseAuth;

    // Состояние звонка
    public enum CallState {
        IDLE,           // Нет звонка
        OUTGOING,       // Исходящий звонок
        INCOMING,       // Входящий звонок
        ACTIVE,         // Активный звонок
        ENDED           // Звонок завершен
    }



    private CallState callState = CallState.IDLE;

    // Callbacks
    private CallStateCallback stateCallback;
    private CallDataCallback dataCallback;

    public interface CallStateCallback {
        void onCallStateChanged(CallState state);
        void onIncomingCall(CallInfo callInfo);
        void onCallStarted(CallInfo callInfo);
        void onCallEnded(String callId);
        void onError(String error);
    }



    public interface CallDataCallback {
        void onOfferReceived(String callId, String sdp);
        void onAnswerReceived(String callId, String sdp);
        void onIceCandidateReceived(String callId, String candidate, int lineIndex, String mid);
        void onCallTerminated(String callId);
    }

    public static class CallInfo {
        public String callId;
        public String roomName;
        public String callerId;
        public String callerName;
        public boolean isVideo;
        public boolean isOutgoing;
        public String status;

        public CallInfo(String callId, String roomName, String callerId,
                        String callerName, boolean isVideo, boolean isOutgoing, String status) {
            this.callId = callId;
            this.roomName = roomName;
            this.callerId = callerId;
            this.callerName = callerName;
            this.isVideo = isVideo;
            this.isOutgoing = isOutgoing;
            this.status = status;
        }
    }

    private CallManager(Context context) {
        this.context = context.getApplicationContext();
        this.firebaseDatabase = FirebaseDatabase.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();

        if (firebaseAuth.getCurrentUser() != null) {
            this.currentUserId = firebaseAuth.getCurrentUser().getUid();
            loadUserName();
        }
    }

    public static synchronized CallManager getInstance(Context context) {
        if (instance == null) {
            instance = new CallManager(context);
        }
        return instance;
    }

    private void loadUserName() {
        if (currentUserId == null) return;

        firebaseDatabase.getReference("users").child(currentUserId).child("username")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            currentUserName = snapshot.getValue(String.class);
                            Log.d(TAG, "Имя пользователя загружено: " + currentUserName);
                        } else {
                            currentUserName = currentUserId.substring(0, Math.min(8, currentUserId.length()));
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Ошибка загрузки имени: " + error.getMessage());
                        currentUserName = "Пользователь";
                    }
                });
    }

    /**
     * Инициализация CallManager (слушатели Firebase)
     */


    /**
     * Слушаем входящие звонки
     */










































    public void answerCall(String callId) {
        Log.d(TAG, "✅ Ответ на звонок: " + callId);

        if (activeCallerId == null) {
            Log.e(TAG, "activeCallerId is null, cannot answer call");
            // Пытаемся получить из активного звонка

            if (this.activeCallId != null && this.activeCallId.equals(callId)) {
                Log.d(TAG, "activeCallId matches, but activeCallerId is null. This should not happen.");
            }
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("/calls/" + callId + "/status", "answered");
        updates.put("/calls/" + callId + "/answeredAt", System.currentTimeMillis());

        firebaseDatabase.getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Звонок принят: " + callId);
                    callState = CallState.ACTIVE;
                    if (stateCallback != null) {
                        mainHandler.post(() -> stateCallback.onCallStateChanged(CallState.ACTIVE));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Ошибка ответа на звонок: " + e.getMessage());
                });
    }

    /**
     * Отклонение звонка
     */
    public void declineCall(String callId) {
        Log.d(TAG, "❌ Отклонение звонка: " + callId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("/calls/" + callId + "/status", "rejected");
        updates.put("/calls/" + callId + "/rejectedAt", System.currentTimeMillis());

        firebaseDatabase.getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Звонок отклонён: " + callId);
                    clearActiveCall();
                    if (stateCallback != null) {
                        mainHandler.post(() -> stateCallback.onCallStateChanged(CallState.IDLE));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Ошибка отклонения звонка: " + e.getMessage());
                });
    }

    /**
     * Завершение звонка
     */
    public void endCall(String callId) {
        Log.d(TAG, "🔴 Завершение звонка: " + callId);

        if (activeCallerId == null) {
            Log.w(TAG, "activeCallerId is null, clearing local call only");
            clearActiveCall();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("/calls/" + callId + "/status", "ended");
        updates.put("/calls/" + callId + "/endedAt", System.currentTimeMillis());

        firebaseDatabase.getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Звонок завершён: " + callId);
                    clearActiveCall();
                    if (stateCallback != null) {
                        mainHandler.post(() -> stateCallback.onCallStateChanged(CallState.IDLE));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Ошибка завершения звонка: " + e.getMessage());
                    clearActiveCall();
                });
    }

    /**
     * Отправка ICE кандидата
     */


    /**
     * Отправка SDP Offer
     */


    /**
     * Отправка SDP Answer
     */


    /**
     * Отправка push-уведомления о звонке через сервер
     */
    private void sendCallNotification(String targetUserId, String callId, String targetUserName, boolean isVideo) {
        executor.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("user_id", targetUserId);
                json.put("caller_id", currentUserId);
                json.put("caller_name", currentUserName != null ? currentUserName : "Пользователь");
                json.put("call_type", "incoming");
                json.put("call_id", callId);
                json.put("room_name", activeRoomName);
                json.put("is_video", isVideo);

                java.net.URL url = new java.net.URL(PUSH_SERVER_URL + "/send-call-to-user");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);

                java.io.OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Push-уведомление отправлено. Код: " + responseCode);
                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки push-уведомления: " + e.getMessage());
            }
        });
    }

    /**
     * Показ уведомления о входящем звонке
     */
    private void showIncomingCallNotification(CallInfo callInfo) {
        Intent intent = new Intent(context, IncomingCallActivity.class);
        intent.putExtra("call_id", callInfo.callId);
        intent.putExtra("room_name", callInfo.roomName);
        intent.putExtra("caller_name", callInfo.callerName);
        intent.putExtra("caller_id", callInfo.callerId);
        intent.putExtra("is_video", callInfo.isVideo);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        CallNotificationManager.createIncomingCallNotification(context, callInfo, intent);
    }

    /**
     * Очистка данных активного звонка
     */
    public void clearActiveCall() {
        activeCallId = null;
        activeRoomName = null;
        activeCallerId = null;
        activeCallerName = null;
        activeIsVideo = false;
        activeIsOutgoing = false;
        callState = CallState.IDLE;
        Log.d(TAG, "Активный звонок очищен");
    }

    /**
     * Установка активного звонка (для IncomingCallActivity)
     */
    public void setActiveCall(String callId, String callerId, String callerName, boolean isVideo, boolean isOutgoing) {
        this.activeCallId = callId;
        this.activeCallerId = callerId;
        this.activeCallerName = callerName;
        this.activeIsVideo = isVideo;
        this.activeIsOutgoing = isOutgoing;
        this.callState = isOutgoing ? CallState.OUTGOING : CallState.INCOMING;
        Log.d(TAG, "Активный звонок установлен: callId=" + callId + ", callerId=" + callerId);
    }


}