package com.example.mytelegram;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.database.FirebaseDatabase;

import org.webrtc.PeerConnection;
import org.webrtc.SurfaceViewRenderer;

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

    // UI Components
    private CircleImageView callerAvatar;
    private TextView callerNameText;
    private TextView callStatusText;
    private TextView callTimerText;
    private ImageButton btnSpeaker;
    private ImageButton btnMute;
    private ImageButton btnVideo;
    private ImageButton btnEndCall;

    // Видео компоненты
    private FrameLayout videoPreviewContainer;
    private SurfaceView remoteVideoView;
    private FrameLayout localVideoContainer;
    private SurfaceView localVideoView;
    private ImageButton btnVideoSpeaker;
    private ImageButton btnVideoMute;
    private ImageButton btnSwitchCamera;
    private ImageButton btnVideoEndCall;

    // Состояния
    private boolean isSpeakerOn = false;
    private boolean isMuted = false;
    private boolean isVideoOn = true;
    private boolean isCallActive = true;

    // Таймер
    private Handler timerHandler;
    private Runnable timerRunnable;
    private long callStartTime;

    // Audio
    private AudioManager audioManager;

    // WebRTC Client
    private WebRTCClient webRtcClient;
    private CallManager callManager;

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
        initCallManager();
        initWebRTC();
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

        videoPreviewContainer = findViewById(R.id.video_preview_container);
        remoteVideoView = findViewById(R.id.remote_video_view);
        localVideoContainer = findViewById(R.id.local_video_container);
        localVideoView = findViewById(R.id.local_video_view);
        btnVideoSpeaker = findViewById(R.id.btn_video_speaker);
        btnVideoMute = findViewById(R.id.btn_video_mute);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        btnVideoEndCall = findViewById(R.id.btn_video_end_call);

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
    }

    private void setupClickListeners() {
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnMute.setOnClickListener(v -> toggleMute());
        btnVideo.setOnClickListener(v -> toggleVideo());
        btnEndCall.setOnClickListener(v -> endCall());

        if (btnVideoSpeaker != null) {
            btnVideoSpeaker.setOnClickListener(v -> toggleSpeaker());
        }
        if (btnVideoMute != null) {
            btnVideoMute.setOnClickListener(v -> toggleMute());
        }
        if (btnSwitchCamera != null) {
            btnSwitchCamera.setOnClickListener(v -> switchCamera());
        }
        if (btnVideoEndCall != null) {
            btnVideoEndCall.setOnClickListener(v -> endCall());
        }
    }

    private void initCallManager() {
        callManager = CallManager.getInstance(this);
    }

    private void initWebRTC() {
        webRtcClient = new WebRTCClient(this, callManager);

        // Исправленный callback с правильными методами
        webRtcClient.setCallback(new WebRTCClient.WebRTCCallback() {
            @Override
            public void onLocalStreamReady(org.webrtc.VideoTrack videoTrack, org.webrtc.AudioTrack audioTrack) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Локальный стрим готов");
                    if (isVideo && videoTrack != null && localVideoView instanceof SurfaceViewRenderer) {
                        ((SurfaceViewRenderer) localVideoView).init(webRtcClient.getRootEglBase().getEglBaseContext(), null);
                        videoTrack.addSink((SurfaceViewRenderer) localVideoView);
                        localVideoContainer.setVisibility(View.VISIBLE);
                        videoPreviewContainer.setVisibility(View.VISIBLE);
                    }
                    callStatusText.setText("Соединение...");
                });
            }

            @Override
            public void onRemoteVideoTrack(org.webrtc.VideoTrack videoTrack) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Удалённый видео стрим получен");
                    if (isVideo && videoTrack != null && remoteVideoView instanceof SurfaceViewRenderer) {
                        ((SurfaceViewRenderer) remoteVideoView).init(webRtcClient.getRootEglBase().getEglBaseContext(), null);
                        videoTrack.addSink((SurfaceViewRenderer) remoteVideoView);
                        remoteVideoView.setVisibility(View.VISIBLE);
                        videoPreviewContainer.setVisibility(View.VISIBLE);

                        // Скрываем информацию о звонящем
                        View callerInfo = findViewById(R.id.caller_info);
                        if (callerInfo != null) {
                            callerInfo.setVisibility(View.GONE);
                        }
                    }
                    callStatusText.setText("В разговоре");
                    callStatusText.setTextColor(getColor(android.R.color.holo_green_dark));
                });
            }

            @Override
            public void onRemoteAudioTrack(org.webrtc.AudioTrack audioTrack) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Удалённый аудио стрим получен");
                });
            }

            @Override
            public void onIceConnectionState(PeerConnection.IceConnectionState state) {
                runOnUiThread(() -> {
                    Log.d(TAG, "ICE состояние: " + state);
                    switch (state) {
                        case CONNECTED:
                            callStatusText.setText("Соединено");
                            break;
                        case DISCONNECTED:
                        case FAILED:
                            callStatusText.setText("Соединение потеряно");
                            if (isCallActive) {
                                endCall();
                            }
                            break;
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "WebRTC ошибка: " + error);
                runOnUiThread(() -> {
                    Toast.makeText(CallActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                    endCall();
                });
            }
        });
    }

    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;

        float alpha = isSpeakerOn ? 1.0f : 0.5f;
        if (btnSpeaker != null) btnSpeaker.setAlpha(alpha);
        if (btnVideoSpeaker != null) btnVideoSpeaker.setAlpha(alpha);

        Toast.makeText(this, isSpeakerOn ? "Динамик включен" : "Динамик выключен", Toast.LENGTH_SHORT).show();
    }

    private void toggleMute() {
        isMuted = !isMuted;

        float alpha = isMuted ? 0.5f : 1.0f;
        if (btnMute != null) btnMute.setAlpha(alpha);
        if (btnVideoMute != null) btnVideoMute.setAlpha(alpha);

        if (webRtcClient != null) {
            webRtcClient.toggleAudio(!isMuted);
        }

        Toast.makeText(this, isMuted ? "Микрофон выключен" : "Микрофон включен", Toast.LENGTH_SHORT).show();
    }

    private void toggleVideo() {
        if (!isVideo) {
            Toast.makeText(this, "Это аудиозвонок", Toast.LENGTH_SHORT).show();
            return;
        }

        isVideoOn = !isVideoOn;

        if (btnVideo != null) {
            btnVideo.setAlpha(isVideoOn ? 1.0f : 0.5f);
        }

        if (webRtcClient != null) {
            webRtcClient.toggleVideo(isVideoOn);
        }

        if (localVideoContainer != null) {
            localVideoContainer.setVisibility(isVideoOn ? View.VISIBLE : View.GONE);
        }

        Toast.makeText(this, isVideoOn ? "Камера включена" : "Камера выключена", Toast.LENGTH_SHORT).show();
    }

    private void switchCamera() {
        if (webRtcClient != null) {
            webRtcClient.switchCamera();
            Toast.makeText(this, "Камера переключена", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI() {
        callerNameText.setText(callerName != null ? callerName : "Неизвестный");

        if (isOutgoing) {
            callStatusText.setText("Звонок идёт...");
            callStatusText.setTextColor(getColor(android.R.color.holo_orange_dark));
        } else {
            callStatusText.setText("Входящий звонок...");
            callStatusText.setTextColor(getColor(android.R.color.holo_blue_dark));
        }

        if (btnVideo != null) {
            btnVideo.setVisibility(isVideo ? View.VISIBLE : View.GONE);
        }

        if (isVideo) {
            View callControls = findViewById(R.id.call_controls);
            if (callControls != null) {
                callControls.setVisibility(View.GONE);
            }
        }

        loadCallerAvatar();
    }

    private void startCall() {
        clearNotification();
        startTimer();

        if (isOutgoing) {
            webRtcClient.startCall(isVideo);
            callStatusText.setText("Вызов...");
        } else {
            webRtcClient.acceptCall(isVideo);
            callStatusText.setText("Подключение...");
        }
    }

    private void startTimer() {
        callStartTime = System.currentTimeMillis();
        timerHandler = new Handler(Looper.getMainLooper());
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
        if (callerId != null && !callerId.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("users").child(callerId).child("avatarUrl")
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
        if (!isCallActive) return;

        isCallActive = false;

        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }

        if (webRtcClient != null) {
            webRtcClient.hangUp();
        }

        Toast.makeText(this, "Звонок завершён", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }

        if (isCallActive) {
            endCall();
        }

        if (webRtcClient != null) {
            webRtcClient.dispose();
        }
    }

    @Override
    public void onBackPressed() {
        if (isCallActive) {
            Toast.makeText(this, "Используйте кнопку завершения звонка", Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
    }
}