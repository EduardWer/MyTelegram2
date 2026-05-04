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
    public void initialize() {
        if (currentUserId == null) {
            Log.w(TAG, "Пользователь не авторизован, CallManager не инициализирован");
            return;
        }

        listenForIncomingCalls();
        listenForCallResponses();
        listenForIceCandidates();
        listenForOffer();
        listenForAnswer();

        Log.d(TAG, "CallManager инициализирован для пользователя: " + currentUserId);
    }

    /**
     * Слушаем входящие звонки
     */
    private void listenForIncomingCalls() {
        if (currentUserId == null) return;

        firebaseDatabase.getReference("calls").child(currentUserId)
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                        String callId = snapshot.getKey();
                        if (callId == null) return;

                        String status = snapshot.child("status").getValue(String.class);
                        String fromUserId = snapshot.child("callerId").getValue(String.class);
                        String fromUserName = snapshot.child("callerName").getValue(String.class);
                        Boolean isVideo = snapshot.child("isVideo").getValue(Boolean.class);

                        // Только новые входящие звонки
                        if ("calling".equals(status) && fromUserId != null && !fromUserId.equals(currentUserId)) {
                            Log.d(TAG, "📞 Входящий звонок: callId=" + callId + ", from=" + fromUserName);

                            CallInfo callInfo = new CallInfo(
                                    callId,
                                    snapshot.child("roomName").getValue(String.class),
                                    fromUserId,
                                    fromUserName != null ? fromUserName : "Неизвестный",
                                    isVideo != null && isVideo,
                                    false,
                                    "incoming"
                            );

                            activeCallId = callId;
                            activeRoomName = callInfo.roomName;
                            activeCallerId = fromUserId;
                            activeCallerName = callInfo.callerName;
                            activeIsVideo = callInfo.isVideo;
                            activeIsOutgoing = false;
                            callState = CallState.INCOMING;

                            if (stateCallback != null) {
                                mainHandler.post(() -> stateCallback.onIncomingCall(callInfo));
                            }

                            showIncomingCallNotification(callInfo);
                        }
                    }

                    @Override
                    public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                        String callId = snapshot.getKey();
                        String status = snapshot.child("status").getValue(String.class);

                        if ("answered".equals(status) && activeCallId != null && activeCallId.equals(callId)) {
                            Log.d(TAG, "✅ Звонок принят: " + callId);
                            callState = CallState.ACTIVE;
                            if (stateCallback != null) {
                                mainHandler.post(() -> stateCallback.onCallStateChanged(CallState.ACTIVE));
                            }
                        } else if ("rejected".equals(status) || "ended".equals(status)) {
                            if (stateCallback != null && activeCallId != null && activeCallId.equals(callId)) {
                                mainHandler.post(() -> stateCallback.onCallEnded(callId));
                            }
                            clearActiveCall();
                        }
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot snapshot) {
                        clearActiveCall();
                    }

                    @Override
                    public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Ошибка прослушивания звонков: " + error.getMessage());
                    }
                });
    }




    /**
     * Проверка, онлайн ли пользователь через Firebase
     */
    public void checkUserOnline(String userId, OnlineStatusCallback callback) {
        if (userId == null) {
            callback.onResult(false);
            return;
        }

        firebaseDatabase.getReference("users").child(userId).child("online")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Boolean isOnline = snapshot.getValue(Boolean.class);
                        callback.onResult(isOnline != null && isOnline);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Error checking online status: " + error.getMessage());
                        callback.onResult(false);
                    }
                });
    }






    public interface OnlineStatusCallback {
        void onResult(boolean isOnline);
    }



























    /**
     * Слушаем ответы на исходящие звонки
     */
    private void listenForCallResponses() {
        if (currentUserId == null) return;

        firebaseDatabase.getReference("calls").child(currentUserId)
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                        String callId = snapshot.getKey();
                        String status = snapshot.child("status").getValue(String.class);

                        if ("answered".equals(status) && activeCallId != null && activeCallId.equals(callId)) {
                            Log.d(TAG, "✅ Исходящий звонок принят: " + callId);
                            callState = CallState.ACTIVE;
                            if (stateCallback != null) {
                                CallInfo callInfo = new CallInfo(
                                        callId, activeRoomName, activeCallerId,
                                        activeCallerName, activeIsVideo, true, "answered"
                                );
                                mainHandler.post(() -> stateCallback.onCallStarted(callInfo));
                            }
                        }
                    }

                    @Override
                    public void onChildAdded(DataSnapshot snapshot, String previousChildName) {}
                    @Override
                    public void onChildRemoved(DataSnapshot snapshot) {}
                    @Override
                    public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    /**
     * Слушаем ICE кандидаты
     */
    private void listenForIceCandidates() {
        if (currentUserId == null || dataCallback == null) return;

        firebaseDatabase.getReference("calls").child(currentUserId)
                .child("iceCandidates")
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                        String candidate = snapshot.child("candidate").getValue(String.class);
                        Long sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Long.class);
                        String sdpMid = snapshot.child("sdpMid").getValue(String.class);

                        if (candidate != null && sdpMLineIndex != null && activeCallId != null) {
                            dataCallback.onIceCandidateReceived(
                                    activeCallId, candidate, sdpMLineIndex.intValue(),
                                    sdpMid != null ? sdpMid : ""
                            );
                            snapshot.getRef().removeValue();
                        }
                    }

                    @Override
                    public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
                    @Override
                    public void onChildRemoved(DataSnapshot snapshot) {}
                    @Override
                    public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Ошибка ICE: " + error.getMessage());
                    }
                });
    }

    /**
     * Слушаем Offer
     */
    private void listenForOffer() {
        if (currentUserId == null || dataCallback == null) return;

        firebaseDatabase.getReference("calls").child(currentUserId).child("offer")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String sdp = snapshot.getValue(String.class);
                        if (sdp != null && activeCallId != null) {
                            Log.d(TAG, "📨 Получен Offer для звонка: " + activeCallId);
                            dataCallback.onOfferReceived(activeCallId, sdp);
                            snapshot.getRef().removeValue();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Ошибка получения Offer: " + error.getMessage());
                    }
                });
    }

    /**
     * Слушаем Answer
     */
    private void listenForAnswer() {
        if (currentUserId == null || dataCallback == null) return;

        firebaseDatabase.getReference("calls").child(currentUserId).child("answer")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String sdp = snapshot.getValue(String.class);
                        if (sdp != null && activeCallId != null) {
                            Log.d(TAG, "📨 Получен Answer для звонка: " + activeCallId);
                            dataCallback.onAnswerReceived(activeCallId, sdp);
                            snapshot.getRef().removeValue();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Ошибка получения Answer: " + error.getMessage());
                    }
                });
    }

    /**
     * Начало исходящего звонка
     */
    public void startCall(String targetUserId, String targetUserName, boolean isVideo) {
        if (currentUserId == null) {
            Log.e(TAG, "Пользователь не авторизован");
            return;
        }

        String callId = "call_" + System.currentTimeMillis();
        String roomName = "room_" + currentUserId + "_" + targetUserId + "_" + System.currentTimeMillis();

        Log.d(TAG, "📞 Начало исходящего звонка: callId=" + callId + ", target=" + targetUserName);

        Map<String, Object> callData = new HashMap<>();
        callData.put("callerId", currentUserId);
        callData.put("callerName", currentUserName != null ? currentUserName : "Пользователь");
        callData.put("calleeId", targetUserId);
        callData.put("calleeName", targetUserName);
        callData.put("roomName", roomName);
        callData.put("isVideo", isVideo);
        callData.put("status", "calling");
        callData.put("timestamp", System.currentTimeMillis());

        Map<String, Object> updates = new HashMap<>();
        updates.put("/calls/" + callId, callData);

        firebaseDatabase.getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Звонок создан в Firebase: " + callId);

                    activeCallId = callId;
                    activeRoomName = roomName;
                    activeCallerId = targetUserId;
                    activeCallerName = targetUserName;
                    activeIsVideo = isVideo;
                    activeIsOutgoing = true;
                    callState = CallState.OUTGOING;

                    if (stateCallback != null) {
                        mainHandler.post(() -> stateCallback.onCallStateChanged(CallState.OUTGOING));
                    }

                    sendCallNotification(targetUserId, callId, targetUserName, isVideo);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Ошибка создания звонка: " + e.getMessage());
                    if (stateCallback != null) {
                        mainHandler.post(() -> stateCallback.onError("Ошибка создания звонка: " + e.getMessage()));
                    }
                });
    }

    /**
     * Ответ на входящий звонок
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