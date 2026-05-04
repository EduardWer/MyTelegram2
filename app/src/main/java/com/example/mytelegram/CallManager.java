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
 * CallManager - управление звонками, интеграция с Firebase и сервером
 */
public class CallManager {

    private static final String TAG = "CallManager";

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
        }

        // Загружаем имя пользователя
        loadUserName();
    }

    public static synchronized CallManager getInstance(Context context) {
        if (instance == null) {
            instance = new CallManager(context);
        }
        return instance;
    }

    private void loadUserName() {
        if (currentUserId == null) return;

        firebaseDatabase.getReference("users").child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            currentUserName = snapshot.child("username").getValue(String.class);
                            Log.d(TAG, "Имя пользователя загружено: " + currentUserName);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Ошибка загрузки имени: " + error.getMessage());
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

        // Слушаем входящие предложения звонков
        listenForIncomingCalls();

        // Слушаем ответы на звонки
        listenForCallAnswers();

        // Слушаем ICE кандидаты
        listenForIceCandidates();

        // Слушаем команды завершения звонка
        listenForCallTermination();

        // НОВЫЕ СЛУШАТЕЛИ ДЛЯ WEBRTC
        // Слушаем SDP Offer
        listenForOffer();

        // Слушаем SDP Answer
        listenForAnswer();

        Log.d(TAG, "CallManager инициализирован для пользователя: " + currentUserId);
    }

    /**
     * Слушаем входящие звонки
     */
    /**
     * Слушаем входящие звонки
     */
    private void listenForIncomingCalls() {
        if (currentUserId == null) return;

        firebaseDatabase.getReference("calls")
                .child(currentUserId)
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                        String callId = snapshot.getKey();
                        if (callId == null) return;

                        String type = snapshot.child("type").getValue(String.class);
                        String status = snapshot.child("status").getValue(String.class);

                        // Пропускаем, если это не новый входящий звонок
                        if (!"incoming".equals(type) || !"initiated".equals(status)) {
                            return;
                        }

                        String fromUserId = snapshot.child("fromUserId").getValue(String.class);
                        String fromUserName = snapshot.child("fromUserName").getValue(String.class);
                        boolean isVideo = snapshot.child("isVideo").getValue(Boolean.class) == Boolean.TRUE;

                        Log.d(TAG, "Входящий звонок: callId=" + callId +
                                ", from=" + fromUserName + ", video=" + isVideo);

                        CallInfo callInfo = new CallInfo(
                                callId,
                                "call_" + callId,
                                fromUserId,
                                fromUserName != null ? fromUserName : "Неизвестный",
                                isVideo,
                                false,
                                "incoming"
                        );

                        // Сохраняем информацию о звонке
                        activeCallId = callId;
                        activeRoomName = callInfo.roomName;
                        activeCallerId = fromUserId;
                        activeCallerName = fromUserName;
                        activeIsVideo = isVideo;
                        activeIsOutgoing = false;
                        callState = CallState.INCOMING;

                        if (stateCallback != null) {
                            mainHandler.post(() -> stateCallback.onIncomingCall(callInfo));
                        }

                        // Показываем уведомление о входящем звонке
                        showIncomingCallNotification(callInfo);
                    }

                    @Override
                    public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                        String callId = snapshot.getKey();
                        String status = snapshot.child("status").getValue(String.class);

                        if ("rejected".equals(status) || "ended".equals(status)) {
                            if (stateCallback != null) {
                                mainHandler.post(() -> stateCallback.onCallEnded(callId));
                            }
                            clearActiveCall();
                        }
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot snapshot) {
                        String callId = snapshot.getKey();
                        Log.d(TAG, "Звонок удалён: " + callId);
                        clearActiveCall();
                    }

                    @Override
                    public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                        // Не используется для звонков
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Ошибка прослушивания звонков: " + error.getMessage());
                    }
                });
    }

    /**
     * Слушаем ответы на исходящие звонки
     */
    /**
     * Слушаем ответы на исходящие звонки
     */
    private void listenForCallAnswers() {
        if (currentUserId == null) return;

        firebaseDatabase.getReference("calls")
                .child(currentUserId)
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                        String callId = snapshot.getKey();
                        if (callId == null) return;

                        String type = snapshot.child("type").getValue(String.class);
                        String status = snapshot.child("status").getValue(String.class);

                        if ("outgoing".equals(type) && "answered".equals(status)) {
                            Log.d(TAG, "Исходящий звонок принят: " + callId);

                            String toUserId = snapshot.child("toUserId").getValue(String.class);
                            String toUserName = snapshot.child("toUserName").getValue(String.class);
                            boolean isVideo = snapshot.child("isVideo").getValue(Boolean.class) == Boolean.TRUE;

                            CallInfo callInfo = new CallInfo(
                                    callId,
                                    "call_" + callId,
                                    toUserId,
                                    toUserName != null ? toUserName : "Неизвестный",
                                    isVideo,
                                    true,
                                    "answered"
                            );

                            activeCallId = callId;
                            activeRoomName = callInfo.roomName;
                            activeCallerId = toUserId;
                            activeCallerName = toUserName;
                            activeIsVideo = isVideo;
                            activeIsOutgoing = true;
                            callState = CallState.ACTIVE;

                            if (stateCallback != null) {
                                mainHandler.post(() -> stateCallback.onCallStarted(callInfo));
                            }
                        }
                    }

                    @Override
                    public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                        String callId = snapshot.getKey();
                        String status = snapshot.child("status").getValue(String.class);

                        if ("ended".equals(status)) {
                            if (stateCallback != null) {
                                mainHandler.post(() -> stateCallback.onCallEnded(callId));
                            }
                            clearActiveCall();
                        }
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot snapshot) {
                        String callId = snapshot.getKey();
                        Log.d(TAG, "Исходящий звонок удалён: " + callId);
                        clearActiveCall();
                    }

                    @Override
                    public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                        // Не используется для ответов на звонки
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Ошибка прослушивания ответов: " + error.getMessage());
                    }
                });
    }

    /**
     * Слушаем ICE кандидаты
     */
    /**
     * Слушаем ICE кандидаты
     */
    private void listenForIceCandidates() {
        if (currentUserId == null) return;

        // Слушаем ICE кандидаты от собеседника
        firebaseDatabase.getReference("calls")
                .child(currentUserId)
                .child("iceCandidates")
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                        if (dataCallback == null) return;

                        String candidate = snapshot.child("candidate").getValue(String.class);
                        Long sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Long.class);
                        String sdpMid = snapshot.child("sdpMid").getValue(String.class);

                        if (candidate != null && sdpMLineIndex != null) {
                            dataCallback.onIceCandidateReceived(
                                    snapshot.getKey(),
                                    candidate,
                                    sdpMLineIndex.intValue(),
                                    sdpMid != null ? sdpMid : ""
                            );
                        }
                    }

                    @Override
                    public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                        // Не используется
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot snapshot) {
                        // TODO: Обработка удаления ICE кандидатов
                    }

                    @Override
                    public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                        // Не используется для ICE кандидатов
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Ошибка прослушивания ICE: " + error.getMessage());
                    }
                });
    }

    /**
     * Слушаем команды завершения звонка
     */
    private void listenForCallTermination() {
        if (currentUserId == null) return;

        firebaseDatabase.getReference("calls")
                .child(currentUserId)
                .child("terminate")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String callId = snapshot.child("callId").getValue(String.class);

                        if (callId != null && dataCallback != null) {
                            dataCallback.onCallTerminated(callId);
                            // Удаляем флаг завершения после обработки
                            snapshot.getRef().removeValue();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Ошибка прослушивания termination: " + error.getMessage());
                    }
                });
    }

    /**
     * НОВЫЙ МЕТОД: Слушаем SDP Offer от собеседника
     */
    private void listenForOffer() {
        if (currentUserId == null) return;

        firebaseDatabase.getReference("calls")
                .child(currentUserId)
                .child("offer")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String sdp = snapshot.getValue(String.class);
                        if (sdp != null && dataCallback != null && activeCallId != null) {
                            Log.d(TAG, "Получен Offer для звонка: " + activeCallId);
                            dataCallback.onOfferReceived(activeCallId, sdp);
                            // Удаляем offer после обработки
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
     * НОВЫЙ МЕТОД: Слушаем SDP Answer от собеседника
     */
    private void listenForAnswer() {
        if (currentUserId == null) return;

        firebaseDatabase.getReference("calls")
                .child(currentUserId)
                .child("answer")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String sdp = snapshot.getValue(String.class);
                        if (sdp != null && dataCallback != null && activeCallId != null) {
                            Log.d(TAG, "Получен Answer для звонка: " + activeCallId);
                            dataCallback.onAnswerReceived(activeCallId, sdp);
                            // Удаляем answer после обработки
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

        String callId = UUID.randomUUID().toString();
        String roomName = "call_" + callId;

        Log.d(TAG, "Начало исходящего звонка: callId=" + callId + ", target=" + targetUserName);

        // Создаем запись звонка в Firebase
        Map<String, Object> callData = new HashMap<>();
        callData.put("type", "outgoing");
        callData.put("status", "initiated");
        callData.put("fromUserId", currentUserId);
        callData.put("fromUserName", currentUserName != null ? currentUserName : "Unknown");
        callData.put("toUserId", targetUserId);
        callData.put("toUserName", targetUserName);
        callData.put("isVideo", isVideo);
        callData.put("timestamp", System.currentTimeMillis());

        // Записываем звонки для обеих сторон
        Map<String, Object> updates = new HashMap<>();
        updates.put("/calls/" + currentUserId + "/" + callId, callData);
        updates.put("/calls/" + targetUserId + "/" + callId, callData);

        firebaseDatabase.getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Звонок создан в Firebase: " + callId);

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

                    // Отправляем push-уведомление через сервер
                    sendCallNotification(targetUserId, callId, targetUserName, isVideo, "outgoing");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ошибка создания звонка: " + e.getMessage());
                    if (stateCallback != null) {
                        mainHandler.post(() -> stateCallback.onError("Ошибка создания звонка: " + e.getMessage()));
                    }
                });
    }

    /**
     * Ответ на входящий звонок (ИСПРАВЛЕНО - обновляем обе стороны)
     */
    public void answerCall(String callId) {
        Log.d(TAG, "Ответ на звонок: " + callId);

        if (activeCallerId == null) {
            Log.e(TAG, "activeCallerId is null, cannot answer call");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        // Обновляем статус для ОБОИХ пользователей
        updates.put("/calls/" + currentUserId + "/" + callId + "/status", "answered");
        updates.put("/calls/" + currentUserId + "/" + callId + "/answeredAt", System.currentTimeMillis());
        updates.put("/calls/" + activeCallerId + "/" + callId + "/status", "answered");

        firebaseDatabase.getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Звонок принят: " + callId);

                    callState = CallState.ACTIVE;
                    if (stateCallback != null) {
                        mainHandler.post(() -> stateCallback.onCallStateChanged(CallState.ACTIVE));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ошибка ответа на звонок: " + e.getMessage());
                    if (stateCallback != null) {
                        mainHandler.post(() -> stateCallback.onError("Ошибка ответа на звонок: " + e.getMessage()));
                    }
                });
    }

    /**
     * Отклонение звонка
     */
    public void declineCall(String callId) {
        Log.d(TAG, "Отклонение звонка: " + callId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("/calls/" + currentUserId + "/" + callId + "/status", "rejected");
        updates.put("/calls/" + currentUserId + "/" + callId + "/rejectedAt", System.currentTimeMillis());

        // Отправляем уведомление об отклонении
        updates.put("/calls/" + activeCallerId + "/" + callId + "/status", "rejected");

        firebaseDatabase.getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Звонок отклонён: " + callId);
                    clearActiveCall();

                    if (stateCallback != null) {
                        mainHandler.post(() -> stateCallback.onCallStateChanged(CallState.IDLE));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ошибка отклонения звонка: " + e.getMessage());
                });
    }

    /**
     * Завершение звонка
     */
    public void endCall(String callId) {
        Log.d(TAG, "Завершение звонка: " + callId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("/calls/" + currentUserId + "/" + callId + "/status", "ended");
        updates.put("/calls/" + currentUserId + "/" + callId + "/endedAt", System.currentTimeMillis());
        updates.put("/calls/" + activeCallerId + "/" + callId + "/status", "ended");

        firebaseDatabase.getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Звонок завершён: " + callId);
                    clearActiveCall();

                    if (stateCallback != null) {
                        mainHandler.post(() -> stateCallback.onCallStateChanged(CallState.IDLE));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ошибка завершения звонка: " + e.getMessage());
                });
    }

    /**
     * Отправка ICE кандидата
     */
    public void sendIceCandidate(String callId, String candidate, int lineIndex, String mid) {
        if (activeCallerId == null) return;

        Map<String, Object> iceData = new HashMap<>();
        iceData.put("candidate", candidate);
        iceData.put("sdpMLineIndex", lineIndex);
        iceData.put("sdpMid", mid);
        iceData.put("fromUser", currentUserId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("/calls/" + activeCallerId + "/" + callId + "/iceCandidates/" + System.currentTimeMillis(), iceData);
        updates.put("/calls/" + currentUserId + "/" + callId + "/iceCandidates/" + System.currentTimeMillis(), iceData);

        firebaseDatabase.getReference().updateChildren(updates)
                .addOnFailureListener(e -> Log.e(TAG, "Ошибка отправки ICE: " + e.getMessage()));
    }

    /**
     * Отправка SDP Offer
     */
    public void sendOffer(String callId, String sdp) {
        if (activeCallerId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("/calls/" + activeCallerId + "/" + callId + "/offer", sdp);

        firebaseDatabase.getReference().updateChildren(updates)
                .addOnFailureListener(e -> Log.e(TAG, "Ошибка отправки Offer: " + e.getMessage()));
    }

    /**
     * Отправка SDP Answer (ИСПРАВЛЕНО - отправляем звонящему)
     */
    public void sendAnswer(String callId, String sdp) {
        if (activeCallerId == null) {
            Log.e(TAG, "activeCallerId is null, cannot send answer");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        // Answer нужно отправить звонящему, а не себе
        updates.put("/calls/" + activeCallerId + "/" + callId + "/answer", sdp);

        firebaseDatabase.getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Answer отправлен успешно"))
                .addOnFailureListener(e -> Log.e(TAG, "Ошибка отправки Answer: " + e.getMessage()));
    }

    /**
     * Отправка push-уведомления о звонке через сервер
     */
    private void sendCallNotification(String targetUserId, String callId,
                                      String targetUserName, boolean isVideo, String callType) {
        executor.execute(() -> {
            try {
                String serverUrl = "http://192.168.1.45:8000/send-call-to-user";
                java.net.URL url = new java.net.URL(serverUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                JSONObject json = new JSONObject();
                json.put("user_id", targetUserId);
                json.put("caller_id", currentUserId);
                json.put("caller_name", currentUserName);
                json.put("call_type", callType);
                json.put("call_id", callId);
                json.put("is_video", isVideo);

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
        CallNotificationManager notificationManager = new CallNotificationManager(context);
        notificationManager.createNotificationChannels();

        Intent intent = new Intent(context, IncomingCallActivity.class);
        intent.putExtra("call_id", callInfo.callId);
        intent.putExtra("room_name", callInfo.roomName);
        intent.putExtra("caller_name", callInfo.callerName);
        intent.putExtra("caller_id", callInfo.callerId);
        intent.putExtra("is_video", callInfo.isVideo);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        CallNotificationManager.createIncomingCallNotification(
                context, callInfo, intent);
    }

    /**
     * Очистка данных активного звонка
     */
    private void clearActiveCall() {
        activeCallId = null;
        activeRoomName = null;
        activeCallerId = null;
        activeCallerName = null;
        activeIsVideo = false;
        activeIsOutgoing = false;
    }

    /**
     * Удаление прослушивателей (вызывать при выходе из приложения)
     */
    public void cleanup() {
        // Прослушиватели автоматически отключаются при уничтожении Firebase Database ссылкок
        Log.d(TAG, "CallManager очищен");
    }

    // Getters и Setters
    public void setStateCallback(CallStateCallback callback) {
        this.stateCallback = callback;
    }

    public void setDataCallback(CallDataCallback callback) {
        this.dataCallback = callback;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public String getCurrentUserName() {
        return currentUserName;
    }

    public String getActiveCallId() {
        return activeCallId;
    }

    public String getActiveRoomName() {
        return activeRoomName;
    }

    public String getActiveCallerId() {
        return activeCallerId;
    }

    public String getActiveCallerName() {
        return activeCallerName;
    }

    public boolean isActiveIsVideo() {
        return activeIsVideo;
    }

    public boolean isActiveIsOutgoing() {
        return activeIsOutgoing;
    }

    public CallState getCallState() {
        return callState;
    }

    public boolean isCallActive() {
        return callState == CallState.ACTIVE || callState == CallState.OUTGOING || callState == CallState.INCOMING;
    }
}