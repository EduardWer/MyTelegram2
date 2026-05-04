package com.example.mytelegram;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import de.hdodenhof.circleimageview.CircleImageView;

public class IncomingCallActivity extends AppCompatActivity {

    private static final String TAG = "IncomingCall";

    private String callId;
    private String roomName;
    private String callerName;
    private String callerId;
    private boolean isVideo;

    private CircleImageView callerAvatar;
    private ImageView backgroundAvatar;
    private TextView callerNameText;
    private TextView callStatusText;
    private TextView callerUsernameText;

    private Ringtone ringtone;
    private Vibrator vibrator;
    private Handler vibrationHandler;
    private boolean isVibrating = false;

    private CallManager callManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Разблокировка экрана
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_incoming_call);

        initViews();
        getIntentData();
        setupUI();
        initCallManager();
        loadCallerDataFromFirebase();
        startRinging();

        // Добавляем слушатель для отслеживания отмены звонка
        listenForCallCancellation();
    }

    private void initViews() {
        callerAvatar = findViewById(R.id.caller_avatar);
        backgroundAvatar = findViewById(R.id.background_avatar);
        callerNameText = findViewById(R.id.caller_name);
        callStatusText = findViewById(R.id.call_status);
        callerUsernameText = findViewById(R.id.caller_username);
    }

    private void getIntentData() {
        Intent intent = getIntent();
        callId = intent.getStringExtra("call_id");
        roomName = intent.getStringExtra("room_name");
        callerName = intent.getStringExtra("caller_name");
        callerId = intent.getStringExtra("caller_id");
        isVideo = intent.getBooleanExtra("is_video", false);

        Log.d(TAG, "callId=" + callId + ", callerId=" + callerId + ", callerName=" + callerName);
    }

    private void initCallManager() {
        callManager = CallManager.getInstance(this);
    }

    private void setupUI() {
        if (callerName != null && !callerName.isEmpty()) {
            callerNameText.setText(callerName);
        } else {
            callerNameText.setText("Неизвестный");
        }

        callStatusText.setText(isVideo ? "Входящий видеозвонок" : "Входящий звонок");

        // Кнопки
        findViewById(R.id.btn_answer).setOnClickListener(v -> answerCall());
        findViewById(R.id.btn_decline).setOnClickListener(v -> declineCall());
    }

    private void listenForCallCancellation() {
        if (callId == null) return;

        // Слушаем изменение статуса звонка
        FirebaseDatabase.getInstance().getReference("calls")
                .child(callerId)
                .child(callId)
                .child("status")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String status = snapshot.getValue(String.class);
                        if ("rejected".equals(status) || "ended".equals(status)) {
                            Log.d(TAG, "Звонок был отменён или завершён");
                            runOnUiThread(() -> {
                                stopRinging();
                                Toast.makeText(IncomingCallActivity.this,
                                        "Звонок отменён", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Ошибка отслеживания статуса: " + error.getMessage());
                    }
                });
    }

    private void loadCallerDataFromFirebase() {
        if (callerId == null || callerId.isEmpty()) {
            Log.w(TAG, "callerId пустой, данные не загружены");
            return;
        }

        Log.d(TAG, "Загружаем данные для callerId: " + callerId);

        // 1. Загружаем имя из /users/{callerId}
        FirebaseDatabase.getInstance().getReference("users").child(callerId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String username = snapshot.child("username").getValue(String.class);
                            if (username != null && !username.isEmpty()) {
                                callerNameText.setText(username);
                            }

                            if (callerUsernameText != null && username != null) {
                                callerUsernameText.setText("@" + username);
                                callerUsernameText.setVisibility(View.VISIBLE);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Ошибка загрузки users: " + error.getMessage());
                    }
                });

        // 2. Загружаем аватар из /users/{callerId}/avatarUrl
        FirebaseDatabase.getInstance().getReference("users").child(callerId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String avatarUrl = snapshot.child("avatarUrl").getValue(String.class);
                            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                Glide.with(IncomingCallActivity.this)
                                        .load(avatarUrl)
                                        .placeholder(R.drawable.ic_person)
                                        .error(R.drawable.ic_person)
                                        .into(callerAvatar);
                                Glide.with(IncomingCallActivity.this)
                                        .load(avatarUrl)
                                        .placeholder(R.drawable.ic_person)
                                        .error(R.drawable.ic_person)
                                        .into(backgroundAvatar);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Ошибка загрузки аватара: " + error.getMessage());
                    }
                });
    }

    private void startRinging() {
        // Рингтон
        try {
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(this, ringtoneUri);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка воспроизведения рингтона: " + e.getMessage());
        }

        // Вибрация
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        vibrationHandler = new Handler();
        startVibration();
    }

    private void startVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            isVibrating = true;
            vibrationHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isVibrating) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(
                                    1000, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vibrator.vibrate(1000);
                        }
                        vibrationHandler.postDelayed(this, 1500);
                    }
                }
            }, 0);
        }
    }

    private void stopRinging() {
        isVibrating = false;
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        if (vibrationHandler != null) {
            vibrationHandler.removeCallbacksAndMessages(null);
        }
    }

    private void answerCall() {
            // ✅ СНАЧАЛА установить активный звонок
            callManager.setActiveCall(callId, callerId, callerName, isVideo, false);
            // ✅ ПОТОМ ответить
            callManager.answerCall(callId);
            // ✅ ЗАТЕМ открыть CallActivity

        Log.d(TAG, "Answering call: callId=" + callId + ", callerId=" + callerId);

        stopRinging();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (callId != null) {
            manager.cancel(callId.hashCode());
        }

        // ✅ КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ
        if (callManager != null) {
            callManager.setActiveCall(callId, callerId, callerName, isVideo, false);
            callManager.answerCall(callId);
        }

        Intent intent = new Intent(this, CallActivity.class);
        intent.putExtra("call_id", callId);
        intent.putExtra("room_name", roomName);
        intent.putExtra("caller_name", callerNameText.getText().toString());
        intent.putExtra("caller_id", callerId);
        intent.putExtra("is_video", isVideo);
        intent.putExtra("is_outgoing", false);
        intent.putExtra("action", "ANSWER");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void declineCall() {
        stopRinging();

        // Убираем уведомление
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (callId != null) {
            manager.cancel(callId.hashCode());
        }

        // Уведомляем CallManager об отклонении
        if (callManager != null) {
            callManager.declineCall(callId);
        }

        Toast.makeText(this, "Звонок отклонён", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRinging();
    }
}