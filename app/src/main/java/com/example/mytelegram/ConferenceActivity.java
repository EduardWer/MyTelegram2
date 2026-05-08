package com.example.mytelegram;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;
import java.util.Random;

public class ConferenceActivity extends AppCompatActivity {

    private static final String TAG = "ConferenceActivity";
    private EditText etRoomCode;
    private Button btnCreateRoom, btnJoinRoom;
    private TextView tvCurrentUser;
    private ProgressBar progressBar;
    private String currentUserId;
    private String currentUserName;
    private boolean isActivityDestroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference);

        Log.d(TAG, "onCreate: Activity created");

        initViews();
        initUser();

        btnCreateRoom.setOnClickListener(v -> createRoom());
        btnJoinRoom.setOnClickListener(v -> joinRoom());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true;
        Log.d(TAG, "onDestroy: Activity destroyed");
    }

    private void initViews() {
        etRoomCode = findViewById(R.id.et_room_code);
        btnCreateRoom = findViewById(R.id.btn_create_room);
        btnJoinRoom = findViewById(R.id.btn_join_room);
        tvCurrentUser = findViewById(R.id.tv_current_user);
        progressBar = findViewById(R.id.progress_bar);

        Log.d(TAG, "initViews: Views initialized");
    }

    private void initUser() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            currentUserName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();

            // Проверяем displayName
            if (TextUtils.isEmpty(currentUserName)) {
                // Пробуем получить email
                String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                if (!TextUtils.isEmpty(email)) {
                    // Берем часть до @ как имя
                    currentUserName = email.split("@")[0];
                } else {
                    // Берем часть userId
                    currentUserName = "User_" + currentUserId.substring(0, Math.min(5, currentUserId.length()));
                }
            }

            Log.d(TAG, "initUser: userId=" + currentUserId + ", userName=" + currentUserName);
        } else {
            currentUserId = "user_" + System.currentTimeMillis();
            currentUserName = "Guest_" + currentUserId.substring(5, 10);
            Log.d(TAG, "initUser: Guest user - " + currentUserName);
        }

        tvCurrentUser.setText("Вы: " + currentUserName);
    }

    private void showLoading(boolean show) {
        if (!isActivityDestroyed) {
            btnCreateRoom.setEnabled(!show);
            btnJoinRoom.setEnabled(!show);
            etRoomCode.setEnabled(!show);
            if (progressBar != null) {
                progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void createRoom() {
        Log.d(TAG, "createRoom: Creating new room");

        // Проверяем, что userName не пустой
        if (TextUtils.isEmpty(currentUserName)) {
            Log.e(TAG, "createRoom: userName is empty!");
            // Повторно инициализируем пользователя
            initUser();
            if (TextUtils.isEmpty(currentUserName)) {
                Toast.makeText(this, "Ошибка: не удалось определить пользователя", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        showLoading(true);

        String roomCode = generateRoomCode();
        Log.d(TAG, "createRoom: Generated room code: " + roomCode);
        Log.d(TAG, "createRoom: Current user - ID: " + currentUserId + ", Name: " + currentUserName);

        // Убедитесь, что порт правильный (3001, а не 3002)
        String serverUrl = "ws://192.168.31.163:3002";
        Log.d(TAG, "createRoom: Connecting to server: " + serverUrl);

        ConferenceWebRTCClient tempClient = new ConferenceWebRTCClient(
                this,
                serverUrl,
                currentUserId,
                currentUserName
        );

        tempClient.setListener(new ConferenceWebRTCClient.ConferenceListener() {
            @Override
            public void onConnected() {
                Log.d(TAG, "onConnected: WebSocket connected");
                // Даем небольшую задержку перед созданием комнаты
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Log.d(TAG, "onConnected: Creating room: " + roomCode);
                    tempClient.createRoom(roomCode);
                }, 500);
            }

            @Override
            public void onRegistered(String userId, String userName) {
                Log.d(TAG, "onRegistered: userId=" + userId + ", userName=" + userName);
            }

            @Override
            public void onRoomCreated(String code) {
                Log.d(TAG, "onRoomCreated: Room created successfully: " + code);
                if (!isActivityDestroyed) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(ConferenceActivity.this,
                                "Комната создана! Код: " + code, Toast.LENGTH_LONG).show();
                        tempClient.disconnect();
                        joinConference(code, true);
                    });
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "onError: " + error);
                if (!isActivityDestroyed) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(ConferenceActivity.this,
                                "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                        tempClient.disconnect();
                    });
                }
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "onDisconnected: WebSocket disconnected");
            }

            @Override
            public void onRoomExists(String code, boolean exists) {}

            @Override
            public void onRoomJoined(String roomCode, boolean isCreator, List<String> participants) {}

            @Override
            public void onUserJoined(String userId, String userName) {}

            @Override
            public void onUserLeft(String userId, String userName) {}

            @Override
            public void onOfferReceived(String fromUserId, String fromUserName, String sdp) {}

            @Override
            public void onAnswerReceived(String fromUserId, String fromUserName, String sdp) {}

            @Override
            public void onIceCandidateReceived(String fromUserId, String fromUserName, String candidate, int sdpMLineIndex, String sdpMid) {}

            @Override
            public void onChatMessage(String fromUserId, String fromUserName, String message, long timestamp) {}

            @Override
            public void onUserAudioStatusChanged(String userId, boolean enabled) {}

            @Override
            public void onUserVideoStatusChanged(String userId, boolean enabled) {}
        });

        tempClient.connect();
    }

    private void joinRoom() {
        Log.d(TAG, "joinRoom: Joining existing room");
        String roomCode = etRoomCode.getText().toString().trim().toUpperCase();

        if (TextUtils.isEmpty(roomCode)) {
            Toast.makeText(this, "Введите код комнаты", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "joinRoom: Room code: " + roomCode);
        showLoading(true);

        String serverUrl = "ws://192.168.31.163:3002";

        ConferenceWebRTCClient tempClient = new ConferenceWebRTCClient(
                this,
                serverUrl,
                currentUserId,
                currentUserName
        );

        tempClient.setListener(new ConferenceWebRTCClient.ConferenceListener() {
            @Override
            public void onConnected() {
                Log.d(TAG, "onConnected: Checking room: " + roomCode);
                tempClient.checkRoom(roomCode);
            }

            @Override
            public void onRoomExists(String code, boolean exists) {
                Log.d(TAG, "onRoomExists: " + code + " exists=" + exists);
                if (!isActivityDestroyed) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        tempClient.disconnect();
                        if (exists) {
                            joinConference(code, false);
                        } else {
                            Toast.makeText(ConferenceActivity.this,
                                    "Комната не найдена", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "onError: " + error);
                if (!isActivityDestroyed) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(ConferenceActivity.this,
                                "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                        tempClient.disconnect();
                    });
                }
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "onDisconnected: WebSocket disconnected");
            }

            @Override
            public void onRegistered(String userId, String userName) {
                Log.d(TAG, "onRegistered: " + userName);
            }

            @Override
            public void onRoomCreated(String code) {}

            @Override
            public void onRoomJoined(String roomCode, boolean isCreator, List<String> participants) {}

            @Override
            public void onUserJoined(String userId, String userName) {}

            @Override
            public void onUserLeft(String userId, String userName) {}

            @Override
            public void onOfferReceived(String fromUserId, String fromUserName, String sdp) {}

            @Override
            public void onAnswerReceived(String fromUserId, String fromUserName, String sdp) {}

            @Override
            public void onIceCandidateReceived(String fromUserId, String fromUserName, String candidate, int sdpMLineIndex, String sdpMid) {}

            @Override
            public void onChatMessage(String fromUserId, String fromUserName, String message, long timestamp) {}

            @Override
            public void onUserAudioStatusChanged(String userId, boolean enabled) {}

            @Override
            public void onUserVideoStatusChanged(String userId, boolean enabled) {}
        });

        tempClient.connect();
    }

    private void joinConference(String roomCode, boolean isCreator) {
        Log.d(TAG, "joinConference: Opening ConferenceCallActivity for room: " + roomCode);
        Intent intent = new Intent(this, ConferenceCallActivity.class);
        intent.putExtra("room_code", roomCode);
        intent.putExtra("user_id", currentUserId);
        intent.putExtra("user_name", currentUserName);
        intent.putExtra("is_creator", isCreator);
        intent.putExtra("server_url", "ws://192.168.31.163:3002");
        startActivity(intent);
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(chars.length());
            code.append(chars.charAt(index));
        }
        return code.toString();
    }
}