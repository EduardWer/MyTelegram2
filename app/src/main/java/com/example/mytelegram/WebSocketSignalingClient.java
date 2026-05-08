package com.example.mytelegram;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class WebSocketSignalingClient {
    private static final String TAG = "WebSocketSignaling";

    private WebSocketClient webSocketClient;
    private String serverUrl;
    private String userId;
    private String userName;
    private boolean isConnected = false;
    private SignalingListener listener;
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final int RECONNECT_DELAY_MS = 3000;
    private boolean isDisconnecting = false;

    // Keep-alive
    private Handler keepAliveHandler;
    private Runnable keepAliveRunnable;
    private static final int KEEP_ALIVE_INTERVAL = 25000;

    // WebRTC Client reference
    private WebRTCClient webRtcClient;

    // UserListListener для временных запросов
    private UserListListener tempUserListListener;

    // Добавьте этот интерфейс
    public interface UserListListener {
        void onUserList(List<String> userIds);
    }

    public interface SignalingListener {



        void onConnected();
        void onDisconnected();
        void onMessage(String message);
        void onError(String error);
        void onIncomingCall(String callId, String fromUserId, String fromUserName, boolean isVideo);
        void onCallAccepted(String callId);
        void onCallRejected();
        void onUserList(JSONArray users);
        void onUserStatusChanged(String userId, boolean isOnline);
        void onOfferReceived(String fromUserId, String sdp);
        void onAnswerReceived(String fromUserId, String sdp);
        void onIceCandidateReceived(String fromUserId, String candidate, int sdpMLineIndex, String sdpMid);
    }

    public WebSocketSignalingClient(String serverUrl, String userId, String userName) {
        this.serverUrl = serverUrl;
        this.userId = userId;
        this.userName = userName;
    }



    public void setListener(SignalingListener listener) {
        this.listener = listener;
    }

    // Добавьте этот метод для установки временного listener
    public void setTempUserListListener(UserListListener listener) {
        this.tempUserListListener = listener;
    }

    public void connect() {
        if (isDisconnecting) {
            Log.d(TAG, "Skipping connect - disconnecting in progress");
            return;
        }

        try {
            URI uri = new URI(serverUrl);
            Log.d(TAG, "Creating new WebSocket connection to: " + serverUrl);

            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.d(TAG, "✅ WebSocket connected");
                    isConnected = true;
                    reconnectAttempts = 0;
                    startKeepAlive();

                    try {
                        JSONObject registerMsg = new JSONObject();
                        registerMsg.put("type", "register");
                        registerMsg.put("userId", userId);
                        registerMsg.put("userName", userName);
                        sendMessage(registerMsg.toString());
                        Log.d(TAG, "📤 Registration sent for user: " + userName);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error creating register message: " + e.getMessage());
                    }

                    if (listener != null) {
                        listener.onConnected();
                    }
                }

                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "📨 Received: " + message);
                    processIncomingMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "❌ WebSocket closed: code=" + code + ", reason=" + reason + ", remote=" + remote);
                    isConnected = false;
                    stopKeepAlive();

                    if (listener != null && !isDisconnecting) {
                        listener.onDisconnected();
                    }

                    if (!isDisconnecting) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error: " + ex.getMessage());
                    if (listener != null && !isDisconnecting) {
                        listener.onError(ex.getMessage());
                    }
                }
            };

            webSocketClient.connect();

        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid URI: " + e.getMessage());
            if (listener != null) {
                listener.onError("Invalid server URL");
            }
        }
    }





    private void processIncomingMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            switch (type) {
                case "registered":
                    Log.d(TAG, "✅ Registration confirmed");
                    if (json.has("users") && listener != null) {
                        listener.onUserList(json.getJSONArray("users"));
                    }
                    break;
                // Вставьте эти case в существующий switch в processIncomingMessage()

                case "group-offer":
                    String groupOfferSdp = json.getString("sdp");
                    String groupOfferFromUserId = json.getString("fromUserId");
                    String groupOfferCallId = json.optString("group_call_id", "");
                    Log.d(TAG, "📞 Received group offer from " + groupOfferFromUserId);

                    if (listener != null) {
                        // Можно добавить новый метод в SignalingListener или использовать существующий
                        listener.onOfferReceived(groupOfferFromUserId, groupOfferSdp);
                    } else if (webRtcClient != null) {
                        webRtcClient.onRemoteOffer(groupOfferFromUserId, groupOfferSdp);
                    }
                    break;

                case "group-answer":
                    String groupAnswerSdp = json.getString("sdp");
                    String groupAnswerFromUserId = json.getString("fromUserId");
                    String groupAnswerCallId = json.optString("group_call_id", "");
                    Log.d(TAG, "📞 Received group answer from " + groupAnswerFromUserId);

                    if (listener != null) {
                        listener.onAnswerReceived(groupAnswerFromUserId, groupAnswerSdp);
                    } else if (webRtcClient != null) {
                        webRtcClient.onRemoteAnswer(groupAnswerFromUserId, groupAnswerSdp);
                    }
                    break;

                case "group-ice-candidate":
                    String groupCandidate = json.getString("candidate");
                    int groupSdpMLineIndex = json.getInt("sdpMLineIndex");
                    String groupSdpMid = json.getString("sdpMid");
                    String groupIceFromUserId = json.getString("fromUserId");
                    String groupIceCallId = json.optString("group_call_id", "");
                    Log.d(TAG, "❄️ Received group ICE candidate from " + groupIceFromUserId);

                    if (listener != null) {
                        listener.onIceCandidateReceived(groupIceFromUserId, groupCandidate,
                                groupSdpMLineIndex, groupSdpMid);
                    } else if (webRtcClient != null) {
                        webRtcClient.addRemoteIceCandidate(groupIceFromUserId, groupCandidate,
                                groupSdpMLineIndex, groupSdpMid);
                    }
                    break;

                case "group-call-invitation":
                    String invitationGroupCallId = json.getString("groupCallId");
                    String invitationFromUserId = json.getString("fromUserId");
                    String invitationFromUserName = json.getString("fromUserName");
                    String invitationRoomId = json.optString("roomId", "");
                    boolean invitationIsVideo = json.optBoolean("isVideo", true);

                    Log.d(TAG, "📞 Group call invitation from " + invitationFromUserName);

                    if (listener != null) {
                        listener.onIncomingCall(invitationGroupCallId, invitationFromUserId,
                                invitationFromUserName, invitationIsVideo);
                    }
                    break;

                case "user-joined-group-call":
                    String joinedGroupCallId = json.getString("groupCallId");
                    String joinedUserId = json.getString("userId");
                    String joinedUserName = json.optString("userName", "Unknown");
                    Log.d(TAG, "👤 User joined group call: " + joinedUserName);

                    if (listener != null) {
                        listener.onMessage(message);
                    }
                    break;

                case "user-left-group-call":
                    String leftGroupCallId = json.getString("groupCallId");
                    String leftUserId = json.getString("userId");
                    Log.d(TAG, "👤 User left group call: " + leftUserId);

                    if (listener != null) {
                        listener.onMessage(message);
                    }
                    break;

                case "group-call-ended":
                    String endedGroupCallId = json.getString("groupCallId");
                    Log.d(TAG, "🔴 Group call ended: " + endedGroupCallId);

                    if (listener != null) {
                        listener.onMessage(message);
                    }
                    break;

                case "group-media-status":
                    String mediaGroupCallId = json.getString("groupCallId");
                    String mediaUserId = json.getString("userId");
                    boolean mediaVideoEnabled = json.optBoolean("videoEnabled", true);
                    boolean mediaAudioEnabled = json.optBoolean("audioEnabled", true);
                    Log.d(TAG, "📹 Media status from " + mediaUserId + ": video=" + mediaVideoEnabled + ", audio=" + mediaAudioEnabled);

                    if (listener != null) {
                        listener.onMessage(message);
                    }
                    break;




                case "users-list":
                    Log.d(TAG, "📋 Received users list");
                    JSONArray usersArray = json.getJSONArray("users");

                    // Обрабатываем через временный listener если есть
                    if (tempUserListListener != null) {
                        List<String> userIds = new ArrayList<>();
                        for (int i = 0; i < usersArray.length(); i++) {
                            JSONObject user = usersArray.getJSONObject(i);
                            userIds.add(user.getString("userId"));
                        }
                        tempUserListListener.onUserList(userIds);
                        tempUserListListener = null; // Очищаем после использования
                    }

                    // Также передаем в основной listener
                    if (listener != null) {
                        listener.onUserList(usersArray);
                    }
                    break;

                case "incoming-call":
                    String callId = json.getString("callId");
                    String fromUserId = json.getString("fromUserId");
                    String fromUserName = json.getString("fromUserName");
                    boolean isVideo = json.getBoolean("isVideo");

                    Log.d(TAG, "📞 Incoming call from " + fromUserName);
                    if (listener != null) {
                        listener.onIncomingCall(callId, fromUserId, fromUserName, isVideo);
                    }
                    break;

                case "call-response":
                    String respCallId = json.getString("callId");
                    boolean accept = json.getBoolean("accept");

                    if (accept && listener != null) {
                        listener.onCallAccepted(respCallId);
                    } else if (!accept && listener != null) {
                        listener.onCallRejected();
                    }
                    break;

                case "offer":
                    String sdp = json.getString("sdp");
                    String offerFromUserId = json.getString("fromUserId");
                    Log.d(TAG, "📞 Received offer from " + offerFromUserId);

                    if (listener != null) {
                        listener.onOfferReceived(offerFromUserId, sdp);
                    } else if (webRtcClient != null) {
                        webRtcClient.onRemoteOffer(offerFromUserId, sdp);
                    }
                    break;

                case "answer":
                    String answerSdp = json.getString("sdp");
                    String answerFromUserId = json.getString("fromUserId");
                    Log.d(TAG, "📞 Received answer from " + answerFromUserId);

                    if (listener != null) {
                        listener.onAnswerReceived(answerFromUserId, answerSdp);
                    } else if (webRtcClient != null) {
                        webRtcClient.onRemoteAnswer(answerFromUserId, answerSdp);
                    }
                    break;

                case "ice-candidate":
                    String candidate = json.getString("candidate");
                    int sdpMLineIndex = json.getInt("sdpMLineIndex");
                    String sdpMid = json.getString("sdpMid");
                    String iceFromUserId = json.getString("fromUserId");
                    Log.d(TAG, "❄️ Received ICE candidate from " + iceFromUserId);

                    if (listener != null) {
                        listener.onIceCandidateReceived(iceFromUserId, candidate, sdpMLineIndex, sdpMid);
                    } else if (webRtcClient != null) {
                        webRtcClient.addRemoteIceCandidate(iceFromUserId, candidate, sdpMLineIndex, sdpMid);
                    }
                    break;

                case "pong":
                    Log.d(TAG, "🏓 Pong received");
                    break;

                default:
                    Log.d(TAG, "Unknown message type: " + type);
                    if (listener != null) {
                        listener.onMessage(message);
                    }
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON: " + e.getMessage());
            if (listener != null) {
                listener.onMessage(message);
            }
        }
    }


    // Для конференций
    public void sendOfferToRoom(String roomCode, String targetUserId, String sdp) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "conference-offer");
            message.put("roomCode", roomCode);
            message.put("targetUserId", targetUserId);
            message.put("sdp", sdp);
            message.put("fromUserId", userId);
            sendMessage(message.toString());
            Log.d(TAG, "📤 Offer sent to " + targetUserId + " in room " + roomCode);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending offer: " + e.getMessage());
        }
    }

    public void sendAnswerToRoom(String roomCode, String targetUserId, String sdp) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "conference-answer");
            message.put("roomCode", roomCode);
            message.put("targetUserId", targetUserId);
            message.put("sdp", sdp);
            message.put("fromUserId", userId);
            sendMessage(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending answer: " + e.getMessage());
        }
    }

    public void sendIceCandidateToRoom(String roomCode, String targetUserId, String candidate, int sdpMLineIndex, String sdpMid) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "conference-ice");
            message.put("roomCode", roomCode);
            message.put("targetUserId", targetUserId);
            message.put("candidate", candidate);
            message.put("sdpMLineIndex", sdpMLineIndex);
            message.put("sdpMid", sdpMid);
            message.put("fromUserId", userId);
            sendMessage(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending ICE candidate: " + e.getMessage());
        }
    }



    // Для конференций
    public void joinConferenceRoom(String roomCode) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "join-conference");
            message.put("roomCode", roomCode);
            message.put("userId", userId);
            message.put("userName", userName);
            sendMessage(message.toString());
            Log.d(TAG, "📤 Joined conference room: " + roomCode);
        } catch (JSONException e) {
            Log.e(TAG, "Error joining conference: " + e.getMessage());
        }
    }

    public void leaveConferenceRoom(String roomCode) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "leave-conference");
            message.put("roomCode", roomCode);
            message.put("userId", userId);
            sendMessage(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error leaving conference: " + e.getMessage());
        }
    }

    private void startKeepAlive() {
        keepAliveHandler = new Handler(Looper.getMainLooper());
        keepAliveRunnable = new Runnable() {
            @Override
            public void run() {
                if (isConnected && webSocketClient != null && webSocketClient.isOpen()) {
                    try {
                        JSONObject ping = new JSONObject();
                        ping.put("type", "ping");
                        webSocketClient.send(ping.toString());
                        Log.d(TAG, "📡 Keep-alive ping sent");
                    } catch (JSONException e) {
                        Log.e(TAG, "Error sending ping: " + e.getMessage());
                    }
                    keepAliveHandler.postDelayed(this, KEEP_ALIVE_INTERVAL);
                }
            }
        };
        keepAliveHandler.post(keepAliveRunnable);
    }

    private void stopKeepAlive() {
        if (keepAliveHandler != null && keepAliveRunnable != null) {
            keepAliveHandler.removeCallbacks(keepAliveRunnable);
            keepAliveHandler = null;
            keepAliveRunnable = null;
        }
    }

    private void scheduleReconnect() {
        if (isDisconnecting) return;

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached");
            if (listener != null) {
                listener.onError("Max reconnect attempts reached");
            }
            return;
        }

        reconnectAttempts++;
        Log.d(TAG, "Reconnecting in " + RECONNECT_DELAY_MS + "ms... attempt " + reconnectAttempts);

        reconnectHandler.postDelayed(() -> {
            if (!isConnected && !isDisconnecting) {
                Log.d(TAG, "Attempting reconnect #" + reconnectAttempts);
                connect();
            }
        }, RECONNECT_DELAY_MS);
    }



    public void sendGroupOffer(String targetUserId, String sdp, String groupCallId) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "group-offer");
            message.put("target_user_id", targetUserId);
            message.put("sdp", sdp);
            message.put("group_call_id", groupCallId);
            sendMessage(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending group offer: " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        if (webSocketClient != null && isConnected && webSocketClient.isOpen()) {
            webSocketClient.send(message);
            Log.d(TAG, "📤 Sent: " + message);
        } else {
            Log.w(TAG, "Cannot send message - not connected. Message: " + message);
        }
    }

    public void sendCallRequest(String targetUserId, boolean isVideo) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "call-request");
            message.put("targetUserId", targetUserId);
            message.put("isVideo", isVideo);
            message.put("fromUserId", userId);
            message.put("fromUserName", userName);
            sendMessage(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending call request: " + e.getMessage());
        }
    }


    public void sendOffer(String targetUserId, String sdp) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "offer");
            message.put("targetUserId", targetUserId);
            message.put("sdp", sdp);
            message.put("fromUserId", userId);
            sendMessage(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending offer: " + e.getMessage());
        }
    }

    public void sendAnswer(String targetUserId, String sdp) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "answer");
            message.put("targetUserId", targetUserId);
            message.put("sdp", sdp);
            message.put("fromUserId", userId);
            sendMessage(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending answer: " + e.getMessage());
        }
    }

    public void sendIceCandidate(String targetUserId, String candidate, int sdpMLineIndex, String sdpMid) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "ice-candidate");
            message.put("targetUserId", targetUserId);
            message.put("candidate", candidate);
            message.put("sdpMLineIndex", sdpMLineIndex);
            message.put("sdpMid", sdpMid);
            message.put("fromUserId", userId);
            sendMessage(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending ICE candidate: " + e.getMessage());
        }
    }


    public void getOnlineUsers() {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "get-users");
            sendMessage(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error getting users: " + e.getMessage());
        }
    }


    // ==================== ДОПОЛНЕНИЯ ДЛЯ ГРУППОВЫХ ЗВОНКОВ ====================

    /**
     * Отправить групповой answer
     */
    public void sendGroupAnswer(String targetUserId, String sdp, String groupCallId) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "group-answer");
            message.put("targetUserId", targetUserId);
            message.put("sdp", sdp);
            message.put("group_call_id", groupCallId);
            message.put("fromUserId", userId);
            sendMessage(message.toString());
            Log.d(TAG, "📤 Sent group answer to: " + targetUserId + ", groupCallId: " + groupCallId);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending group answer: " + e.getMessage());
        }
    }

    /**
     * Отправить групповой ICE кандидат
     */
    public void sendGroupIceCandidate(String targetUserId, String candidate,
                                      int sdpMLineIndex, String sdpMid, String groupCallId) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "group-ice-candidate");
            message.put("targetUserId", targetUserId);
            message.put("candidate", candidate);
            message.put("sdpMLineIndex", sdpMLineIndex);
            message.put("sdpMid", sdpMid);
            message.put("group_call_id", groupCallId);
            message.put("fromUserId", userId);
            sendMessage(message.toString());
            Log.d(TAG, "📤 Sent group ICE candidate to: " + targetUserId);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending group ICE candidate: " + e.getMessage());
        }
    }

    /**
     * Пригласить пользователя в групповой звонок
     */
    public void sendGroupCallInvitation(String groupCallId, String targetUserId,
                                        String roomId, boolean isVideo) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "group-call-invitation");
            message.put("groupCallId", groupCallId);
            message.put("targetUserId", targetUserId);
            message.put("roomId", roomId);
            message.put("isVideo", isVideo);
            message.put("fromUserId", userId);
            message.put("fromUserName", userName);
            sendMessage(message.toString());
            Log.d(TAG, "📤 Sent group call invitation to: " + targetUserId);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending group call invitation: " + e.getMessage());
        }
    }

    /**
     * Присоединиться к групповому звонку
     */
    public void joinGroupCall(String groupCallId) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "join-group-call");
            message.put("groupCallId", groupCallId);
            message.put("userId", userId);
            message.put("userName", userName);
            sendMessage(message.toString());
            Log.d(TAG, "📤 Joining group call: " + groupCallId);
        } catch (JSONException e) {
            Log.e(TAG, "Error joining group call: " + e.getMessage());
        }
    }

    /**
     * Покинуть групповой звонок
     */
    public void leaveGroupCall(String groupCallId) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "leave-group-call");
            message.put("groupCallId", groupCallId);
            message.put("userId", userId);
            sendMessage(message.toString());
            Log.d(TAG, "📤 Leaving group call: " + groupCallId);
        } catch (JSONException e) {
            Log.e(TAG, "Error leaving group call: " + e.getMessage());
        }
    }

    /**
     * Завершить групповой звонок (для создателя)
     */
    public void endGroupCall(String groupCallId) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "end-group-call");
            message.put("groupCallId", groupCallId);
            message.put("userId", userId);
            sendMessage(message.toString());
            Log.d(TAG, "📤 Ending group call: " + groupCallId);
        } catch (JSONException e) {
            Log.e(TAG, "Error ending group call: " + e.getMessage());
        }
    }

    /**
     * Отправить статус медиа в групповом звонке (видео/аудио вкл/выкл)
     */
    public void sendGroupMediaStatus(String groupCallId, boolean videoEnabled, boolean audioEnabled) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "group-media-status");
            message.put("groupCallId", groupCallId);
            message.put("userId", userId);
            message.put("videoEnabled", videoEnabled);
            message.put("audioEnabled", audioEnabled);
            sendMessage(message.toString());
            Log.d(TAG, "📤 Media status update: video=" + videoEnabled + ", audio=" + audioEnabled);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending media status: " + e.getMessage());
        }
    }

    /**
     * Получить информацию о групповом звонке
     */
    public void getGroupCallInfo(String groupCallId) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "get-group-call-info");
            message.put("groupCallId", groupCallId);
            message.put("userId", userId);
            sendMessage(message.toString());
            Log.d(TAG, "📤 Requesting group call info: " + groupCallId);
        } catch (JSONException e) {
            Log.e(TAG, "Error getting group call info: " + e.getMessage());
        }
    }

    /**
     * Переподключиться к групповому звонку после разрыва соединения
     */
    public void rejoinGroupCall(String groupCallId) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "rejoin-group-call");
            message.put("groupCallId", groupCallId);
            message.put("userId", userId);
            message.put("userName", userName);
            sendMessage(message.toString());
            Log.d(TAG, "📤 Rejoining group call: " + groupCallId);
        } catch (JSONException e) {
            Log.e(TAG, "Error rejoining group call: " + e.getMessage());
        }
    }



    public void disconnect() {
        stopKeepAlive();
        isDisconnecting = true;
        reconnectHandler.removeCallbacksAndMessages(null);

        if (webSocketClient != null) {
            if (webSocketClient.isOpen()) {
                webSocketClient.close();
            }
            webSocketClient = null;
        }

        isConnected = false;
    }


}