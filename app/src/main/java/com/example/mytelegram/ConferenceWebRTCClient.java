package com.example.mytelegram;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public class ConferenceWebRTCClient {

    private static final String TAG = "ConferenceWebRTCClient";

    private WebSocket webSocket;
    private OkHttpClient client;
    private String serverUrl;
    private String userId;
    private String userName;
    private ConferenceListener listener;
    private boolean isConnected = false;
    private boolean isRegistered = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Очередь сообщений для отправки после регистрации
    private Queue<Runnable> pendingMessages = new LinkedList<>();

    public interface ConferenceListener {
        void onConnected();
        void onDisconnected();
        void onRegistered(String userId, String userName);
        void onRoomCreated(String code);
        void onRoomExists(String code, boolean exists);
        void onRoomJoined(String roomCode, boolean isCreator, List<String> participants);
        void onUserJoined(String userId, String userName);
        void onUserLeft(String userId, String userName);
        void onOfferReceived(String fromUserId, String fromUserName, String sdp);
        void onAnswerReceived(String fromUserId, String fromUserName, String sdp);
        void onIceCandidateReceived(String fromUserId, String fromUserName, String candidate, int sdpMLineIndex, String sdpMid);
        void onChatMessage(String fromUserId, String fromUserName, String message, long timestamp);
        void onUserAudioStatusChanged(String userId, boolean enabled);
        void onUserVideoStatusChanged(String userId, boolean enabled);
        void onError(String error);
    }

    public ConferenceWebRTCClient(Context context, String serverUrl, String userId, String userName) {
        this.serverUrl = serverUrl;
        this.userId = userId;
        this.userName = userName;

        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public void setListener(ConferenceListener listener) {
        this.listener = listener;
    }

    public void connect() {
        Log.d(TAG, "connect: Connecting to " + serverUrl);

        Request request = new Request.Builder()
                .url(serverUrl)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "onOpen: WebSocket connected successfully");
                isConnected = true;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onConnected();
                    }
                    // Сразу регистрируемся
                    register();
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "onMessage: Received: " + text);
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "onClosing: code=" + code + " reason=" + reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "onClosed: code=" + code + " reason=" + reason);
                isConnected = false;
                isRegistered = false;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onDisconnected();
                    }
                });
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "onFailure: " + t.getMessage(), t);
                isConnected = false;
                isRegistered = false;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onError("Connection failed: " + t.getMessage());
                    }
                });
            }
        });
    }

    private void register() {
        try {
            JSONObject payload = new JSONObject();
            payload.put("userId", userId);
            payload.put("userName", userName);
            Log.d(TAG, "register: Sending registration - userId=" + userId + ", userName=" + userName);
            sendMessage("register", payload);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating register payload", e);
            handleError("Error creating register payload: " + e.getMessage());
        }
    }

    // Методы с отложенной отправкой до регистрации
    public void createRoom(String roomCode) {
        Log.d(TAG, "createRoom: Creating room " + roomCode);
        if (!isRegistered) {
            Log.d(TAG, "createRoom: Not registered yet, queuing");
            pendingMessages.add(() -> createRoomDirect(roomCode));
            return;
        }
        createRoomDirect(roomCode);
    }

    private void createRoomDirect(String roomCode) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("roomCode", roomCode);
            sendMessage("create-room", payload);
            Log.d(TAG, "createRoomDirect: Room creation requested for " + roomCode);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating room payload", e);
            handleError("Error creating room payload: " + e.getMessage());
        }
    }

    public void checkRoom(String roomCode) {
        Log.d(TAG, "checkRoom: Checking room " + roomCode);
        if (!isRegistered) {
            Log.d(TAG, "checkRoom: Not registered yet, queuing");
            pendingMessages.add(() -> checkRoomDirect(roomCode));
            return;
        }
        checkRoomDirect(roomCode);
    }

    private void checkRoomDirect(String roomCode) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("roomCode", roomCode);
            sendMessage("check-room", payload);
            Log.d(TAG, "checkRoomDirect: Room check requested for " + roomCode);
        } catch (JSONException e) {
            Log.e(TAG, "Error checking room payload", e);
            handleError("Error checking room payload: " + e.getMessage());
        }
    }

    public void joinRoom(String roomCode) {
        Log.d(TAG, "joinRoom: Joining room " + roomCode);
        if (!isRegistered) {
            Log.d(TAG, "joinRoom: Not registered yet, queuing");
            pendingMessages.add(() -> joinRoomDirect(roomCode));
            return;
        }
        joinRoomDirect(roomCode);
    }

    private void joinRoomDirect(String roomCode) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("roomCode", roomCode);
            sendMessage("join-room", payload);
            Log.d(TAG, "joinRoomDirect: Room join requested for " + roomCode);
        } catch (JSONException e) {
            Log.e(TAG, "Error joining room payload", e);
            handleError("Error joining room payload: " + e.getMessage());
        }
    }

    public void leaveRoom() {
        try {
            JSONObject payload = new JSONObject();
            sendMessage("leave-room", payload);
        } catch (Exception e) {
            handleError("Error leaving room: " + e.getMessage());
        }
    }

    public void sendOffer(String targetUserId, String sdp) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("targetUserId", targetUserId);
            payload.put("sdp", sdp);
            sendMessage("offer", payload);
        } catch (JSONException e) {
            handleError("Error sending offer: " + e.getMessage());
        }
    }

    public void sendAnswer(String targetUserId, String sdp) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("targetUserId", targetUserId);
            payload.put("sdp", sdp);
            sendMessage("answer", payload);
        } catch (JSONException e) {
            handleError("Error sending answer: " + e.getMessage());
        }
    }

    public void sendIceCandidate(String targetUserId, String candidate, int sdpMLineIndex, String sdpMid) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("targetUserId", targetUserId);
            payload.put("candidate", candidate);
            payload.put("sdpMLineIndex", sdpMLineIndex);
            payload.put("sdpMid", sdpMid);
            sendMessage("ice-candidate", payload);
        } catch (JSONException e) {
            handleError("Error sending ICE candidate: " + e.getMessage());
        }
    }

    public void sendChatMessage(String message) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("message", message);
            sendMessage("chat-message", payload);
        } catch (JSONException e) {
            handleError("Error sending chat message: " + e.getMessage());
        }
    }

    public void toggleAudio(boolean enabled) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("enabled", enabled);
            sendMessage("toggle-audio", payload);
        } catch (JSONException e) {
            handleError("Error toggling audio: " + e.getMessage());
        }
    }

    public void toggleVideo(boolean enabled) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("enabled", enabled);
            sendMessage("toggle-video", payload);
        } catch (JSONException e) {
            handleError("Error toggling video: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "User disconnect");
            webSocket = null;
        }
        isRegistered = false;
        pendingMessages.clear();
    }

    private void sendMessage(String type, JSONObject payload) {
        if (webSocket != null && isConnected) {
            try {
                JSONObject message = new JSONObject();
                message.put("type", type);
                message.put("payload", payload);
                String jsonString = message.toString();
                Log.d(TAG, "sendMessage: Sending: " + jsonString);
                webSocket.send(jsonString);
            } catch (JSONException e) {
                Log.e(TAG, "sendMessage: Error creating message", e);
                handleError("Error sending message: " + e.getMessage());
            }
        } else {
            Log.e(TAG, "sendMessage: WebSocket is not connected");
            handleError("WebSocket is not connected");
        }
    }

    private void handleError(String errorMessage) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onError(errorMessage);
            }
        });
    }

    private void handleMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            JSONObject payload = new JSONObject();
            if (json.has("payload")) {
                Object payloadObj = json.get("payload");
                if (payloadObj instanceof JSONObject) {
                    payload = (JSONObject) payloadObj;
                }
            }

            final JSONObject finalPayload = payload;

            mainHandler.post(() -> {
                if (listener == null) return;

                try {
                    switch (type) {
                        case "registered":
                            isRegistered = true;
                            Log.d(TAG, "Registered successfully as " + userName);
                            listener.onRegistered(
                                    finalPayload.optString("userId", userId),
                                    finalPayload.optString("userName", userName));

                            // Выполняем отложенные сообщения
                            processPendingMessages();
                            break;

                        case "room-created":
                            listener.onRoomCreated(finalPayload.optString("roomCode", ""));
                            break;

                        case "room-exists":
                            listener.onRoomExists(
                                    finalPayload.optString("roomCode", ""),
                                    finalPayload.optBoolean("exists", false));
                            break;

                        case "room-joined":
                            List<String> participants = new ArrayList<>();
                            if (finalPayload.has("participants")) {
                                JSONArray arr = finalPayload.getJSONArray("participants");
                                for (int i = 0; i < arr.length(); i++) {
                                    participants.add(arr.getString(i));
                                }
                            }
                            listener.onRoomJoined(
                                    finalPayload.optString("roomCode", ""),
                                    finalPayload.optBoolean("isCreator", false),
                                    participants);
                            break;

                        case "user-joined":
                            listener.onUserJoined(
                                    finalPayload.optString("userId", ""),
                                    finalPayload.optString("userName", ""));
                            break;

                        case "user-left":
                            listener.onUserLeft(
                                    finalPayload.optString("userId", ""),
                                    finalPayload.optString("userName", ""));
                            break;

                        case "offer":
                            listener.onOfferReceived(
                                    finalPayload.optString("fromUserId", ""),
                                    finalPayload.optString("fromUserName", ""),
                                    finalPayload.optString("sdp", ""));
                            break;

                        case "answer":
                            listener.onAnswerReceived(
                                    finalPayload.optString("fromUserId", ""),
                                    finalPayload.optString("fromUserName", ""),
                                    finalPayload.optString("sdp", ""));
                            break;

                        case "ice-candidate":
                            listener.onIceCandidateReceived(
                                    finalPayload.optString("fromUserId", ""),
                                    finalPayload.optString("fromUserName", ""),
                                    finalPayload.optString("candidate", ""),
                                    finalPayload.optInt("sdpMLineIndex", 0),
                                    finalPayload.optString("sdpMid", ""));
                            break;

                        case "chat-message":
                            listener.onChatMessage(
                                    finalPayload.optString("fromUserId", ""),
                                    finalPayload.optString("fromUserName", ""),
                                    finalPayload.optString("message", ""),
                                    finalPayload.optLong("timestamp", System.currentTimeMillis()));
                            break;

                        case "toggle-audio":
                            listener.onUserAudioStatusChanged(
                                    finalPayload.optString("userId", ""),
                                    finalPayload.optBoolean("enabled", true));
                            break;

                        case "toggle-video":
                            listener.onUserVideoStatusChanged(
                                    finalPayload.optString("userId", ""),
                                    finalPayload.optBoolean("enabled", true));
                            break;

                        case "error":
                            String errorMsg = finalPayload.optString("message", "Unknown error");
                            Log.e(TAG, "Server error: " + errorMsg);
                            // Не передаем ошибку "Not registered", так как мы это обрабатываем
                            if (!errorMsg.equals("Not registered")) {
                                listener.onError(errorMsg);
                            }
                            break;

                        default:
                            Log.w(TAG, "Unknown message type: " + type);
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling message type: " + type, e);
                    handleError("Error handling message: " + e.getMessage());
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing message: " + message, e);
            handleError("Error parsing message: " + e.getMessage());
        }
    }

    private void processPendingMessages() {
        Log.d(TAG, "processPendingMessages: Processing " + pendingMessages.size() + " pending messages");
        while (!pendingMessages.isEmpty()) {
            Runnable message = pendingMessages.poll();
            if (message != null) {
                message.run();
            }
        }
    }
}