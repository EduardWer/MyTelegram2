package com.example.mytelegram;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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

    private String callId;
    private String roomName;
    private String callerName;
    private String callerId;
    private boolean isVideo;
    private String action;

    private CircleImageView callerAvatar;
    private TextView callerNameText;
    private TextView callStatusText;
    private ImageButton btnAnswer;
    private ImageButton btnDecline;
    private ImageButton btnSpeaker;
    private ImageButton btnMute;
    private ImageButton btnVideo;

    private boolean isSpeakerOn = false;
    private boolean isMuted = false;
    private boolean isVideoOn = false;

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
    }

    private void initViews() {
        callerAvatar = findViewById(R.id.caller_avatar);
        callerNameText = findViewById(R.id.caller_name);
        callStatusText = findViewById(R.id.call_status);
        btnAnswer = findViewById(R.id.btn_answer);
        btnDecline = findViewById(R.id.btn_decline);
        btnSpeaker = findViewById(R.id.btn_speaker);
        btnMute = findViewById(R.id.btn_mute);
        btnVideo = findViewById(R.id.btn_video);
    }

    private void getIntentData() {
        Intent intent = getIntent();
        callId = intent.getStringExtra("call_id");
        roomName = intent.getStringExtra("room_name");
        callerName = intent.getStringExtra("caller_name");
        callerId = intent.getStringExtra("caller_id");
        isVideo = intent.getBooleanExtra("is_video", false);
        action = intent.getStringExtra("action");

        // Если пришли с действием ANSWER — сразу отвечаем
        if ("ANSWER".equals(action)) {
            answerCall();
        }
    }

    private void setupClickListeners() {
        btnAnswer.setOnClickListener(v -> answerCall());
        btnDecline.setOnClickListener(v -> declineCall());

        btnSpeaker.setOnClickListener(v -> {
            isSpeakerOn = !isSpeakerOn;
            btnSpeaker.setAlpha(isSpeakerOn ? 1.0f : 0.5f);
            Toast.makeText(this,
                    isSpeakerOn ? "Динамик включен" : "Динамик выключен",
                    Toast.LENGTH_SHORT).show();
            // TODO: AudioManager.setSpeakerphoneOn(isSpeakerOn)
        });

        btnMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            btnMute.setAlpha(isMuted ? 1.0f : 0.5f);
            Toast.makeText(this,
                    isMuted ? "Микрофон выключен" : "Микрофон включен",
                    Toast.LENGTH_SHORT).show();
            // TODO: localAudioTrack.setEnabled(!isMuted)
        });

        btnVideo.setOnClickListener(v -> {
            if (!isVideo) {
                Toast.makeText(this, "Это аудиозвонок", Toast.LENGTH_SHORT).show();
                return;
            }
            isVideoOn = !isVideoOn;
            btnVideo.setAlpha(isVideoOn ? 1.0f : 0.5f);
            Toast.makeText(this,
                    isVideoOn ? "Камера включена" : "Камера выключена",
                    Toast.LENGTH_SHORT).show();
            // TODO: localVideoTrack.setEnabled(isVideoOn)
        });
    }

    private void updateUI() {
        // Имя звонящего
        callerNameText.setText(callerName != null ? callerName : "Неизвестный");

        // Статус
        if ("ANSWER".equals(action)) {
            callStatusText.setText("Соединение...");
        } else {
            callStatusText.setText(isVideo ? "Входящий видеозвонок..." : "Входящий звонок...");
        }

        // Аватар (загружаем из Firebase или по URL)
        loadCallerAvatar();

        // Показываем/скрываем кнопку видео
        btnVideo.setVisibility(isVideo ? View.VISIBLE : View.GONE);
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
                    });
        }
    }

    private void answerCall() {
        // Убираем уведомление о входящем звонке
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (callId != null) {
            notificationManager.cancel(callId.hashCode());
        }

        callStatusText.setText("Соединение...");

        // Меняем видимость кнопок
        btnAnswer.setVisibility(View.GONE);

        // TODO: Подключение к WebRTC
        // WebRTCManager.connect(roomName, isVideo);

        Toast.makeText(this, "Подключение к звонку...", Toast.LENGTH_SHORT).show();
    }

    private void declineCall() {
        // Убираем уведомление
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (callId != null) {
            notificationManager.cancel(callId.hashCode());
        }

        // TODO: Отправить на сервер rejectCall(callId)
        rejectCallOnServer();

        Toast.makeText(this, "Звонок отклонён", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void rejectCallOnServer() {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("http://192.168.1.45:8000/reject-call");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                org.json.JSONObject json = new org.json.JSONObject();
                json.put("call_id", callId);

                java.io.OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка отклонения звонка: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        // Не даём выйти назад во время звонка
        if ("ANSWER".equals(action)) {
            Toast.makeText(this, "Завершите звонок перед выходом", Toast.LENGTH_SHORT).show();
        } else {
            declineCall();
        }
    }
}