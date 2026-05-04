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