package com.example.mytelegram;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import de.hdodenhof.circleimageview.CircleImageView;

public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";

    // Данные звонка
    private String callId;
    private String roomName;
    private String callerName;
    private String callerId;
    private boolean isVideo;
    private boolean isOutgoing;
    private String action;

    // UI Components
    private CircleImageView callerAvatar;
    private TextView callerNameText;
    private TextView callStatusText;
    private TextView callTimerText;
    private ImageButton btnSpeaker;
    private ImageButton btnMute;
    private ImageButton btnVideo;
    private ImageButton btnEndCall;

    // Состояния
    private boolean isSpeakerOn = false;
    private boolean isMuted = false;
    private boolean isVideoOn = true;
    private boolean isCallActive = true;

    // Таймер
    private CountDownTimer callTimer;
    private long callStartTime;
    private Handler timerHandler;
    private Runnable timerRunnable;

    // Audio
    private AudioManager audioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // Разблокируем экран и показываем поверх блокировки
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        initViews();
        getIntentData();
        setupClickListeners();
        updateUI();
        startCall();
    }

    private void initViews() {
        callerAvatar = findViewById(R.id.caller_avatar);
        callerNameText = findViewById(R.id.caller_name);
        callStatusText = findViewById(R.id.call_status);
        callTimerText = findViewById(R.id.call_timer);
        btnSpeaker = findViewById(R.id.btn_speaker);
        btnMute = findViewById(R.id.btn_mute);
        btnVideo = findViewById(R.id.btn_video);
        btnEndCall = findViewById(R.id.btn_end_call);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    private void getIntentData() {
        Intent intent = getIntent();
        callId = intent.getStringExtra("call_id");
        roomName = intent.getStringExtra("room_name");
        callerName = intent.getStringExtra("caller_name");
        callerId = intent.getStringExtra("caller_id");
        isVideo = intent.getBooleanExtra("is_video", false);
        isOutgoing = intent.getBooleanExtra("is_outgoing", true);
        action = intent.getStringExtra("action");
    }

    private void setupClickListeners() {
        // Кнопка динамика
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());

        // Кнопка микрофона
        btnMute.setOnClickListener(v -> toggleMute());

        // Кнопка видео
        btnVideo.setOnClickListener(v -> toggleVideo());

        // Кнопка завершения звонка
        btnEndCall.setOnClickListener(v -> endCall());
    }

    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        btnSpeaker.setAlpha(isSpeakerOn ? 1.0f : 0.5f);

        if (audioManager != null) {
            if (isSpeakerOn) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setSpeakerphoneOn(true);
            } else {
                audioManager.setSpeakerphoneOn(false);
            }
        }

        Toast.makeText(this,
                isSpeakerOn ? "Динамик включен" : "Динамик выключен",
                Toast.LENGTH_SHORT).show();
    }

    private void toggleMute() {
        isMuted = !isMuted;
        btnMute.setAlpha(isMuted ? 0.5f : 1.0f);

        // TODO: localAudioTrack.setEnabled(!isMuted)

        Toast.makeText(this,
                isMuted ? "Микрофон выключен" : "Микрофон включен",
                Toast.LENGTH_SHORT).show();
    }

    private void toggleVideo() {
        if (!isVideo) {
            Toast.makeText(this, "Это аудиозвонок", Toast.LENGTH_SHORT).show();
            return;
        }

        isVideoOn = !isVideoOn;
        btnVideo.setAlpha(isVideoOn ? 1.0f : 0.5f);

        // TODO: localVideoTrack.setEnabled(isVideoOn)

        Toast.makeText(this,
                isVideoOn ? "Камера включена" : "Камера выключена",
                Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        // Имя звонящего
        callerNameText.setText(callerName != null ? callerName : "Неизвестный");

        // Статус звонка
        callStatusText.setText("В разговоре");
        callStatusText.setTextColor(getColor(android.R.color.holo_green_dark));

        // Аватар
        loadCallerAvatar();

        // Показываем кнопку видео только для видеозвонков
        btnVideo.setVisibility(isVideo ? View.VISIBLE : View.GONE);

        // Для видеозвонков показываем подсказку
        if (isVideo) {
            Toast.makeText(this, "Видеозвонок начался", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCall() {
        // Убираем уведомление о входящем звонке
        clearNotification();

        // Запускаем таймер
        startTimer();

        // TODO: Подключение к WebRTC
        // WebRTCManager.connect(roomName, isVideo);

        Log.d(TAG, "Звонок начался: " + callId);
    }

    private void startTimer() {
        callStartTime = System.currentTimeMillis();
        timerHandler = new Handler();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isCallActive) return;

                long duration = System.currentTimeMillis() - callStartTime;
                long seconds = duration / 1000;
                long minutes = seconds / 60;
                seconds = seconds % 60;

                String timeString = String.format("%02d:%02d", minutes, seconds);
                if (callTimerText != null) {
                    callTimerText.setText(timeString);
                }
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void loadCallerAvatar() {
        // Загрузка аватара из Firebase Database
        if (callerId != null && !callerId.isEmpty()) {
            com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("avatars").child(callerId)
                    .get()
                    .addOnSuccessListener(dataSnapshot -> {
                        if (dataSnapshot.exists()) {
                            String avatarUrl = dataSnapshot.getValue(String.class);
                            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                Glide.with(CallActivity.this)
                                        .load(avatarUrl)
                                        .placeholder(R.drawable.ic_person)
                                        .error(R.drawable.ic_person)
                                        .into(callerAvatar);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Ошибка загрузки аватара: " + e.getMessage());
                    });
        }
    }

    private void clearNotification() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (callId != null) {
            notificationManager.cancel(callId.hashCode());
        }
    }

    private void endCall() {
        isCallActive = false;

        // Останавливаем таймер
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }

        // Отправляем уведомление о завершении звонка
        sendCallEndNotification();

        // TODO: Закрыть WebRTC соединение
        // WebRTCManager.disconnect();

        Toast.makeText(this, "Звонок завершён", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void sendCallEndNotification() {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("http://192.168.1.45:8000/send-call-to-user");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                org.json.JSONObject json = new org.json.JSONObject();
                json.put("user_id", callerId);
                json.put("caller_id", callerId);
                json.put("caller_name", callerName);
                json.put("call_type", "ended");
                json.put("call_id", callId);
                json.put("room_name", roomName);
                json.put("is_video", isVideo);

                java.io.OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Уведомление о завершении звонка отправлено. Код: " + responseCode);
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки уведомления о завершении: " + e.getMessage());
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }


}