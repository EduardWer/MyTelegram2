package com.example.mytelegram;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.PeerConnection;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import de.hdodenhof.circleimageview.CircleImageView;

public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";
    private static final String WS_SERVER_URL = "ws://192.168.31.163:3001";

    // Данные звонка
    private boolean isFrontCamera = true;
    private String callId;
    private String callerName;
    private String callerId;
    private boolean isVideo;
    private boolean isOutgoing;
    private boolean isCallStarted = false;

    // UI Components
    private CircleImageView callerAvatar;
    private TextView callerNameText;
    private TextView callStatusText;
    private TextView callTimerText;
    private ImageButton btnSpeaker;
    private ImageButton btnMute;
    private ImageButton btnVideo;
    private ImageButton btnEndCall;
    private FrameLayout videoPreviewContainer;
    private SurfaceViewRenderer remoteVideoView;
    private FrameLayout localVideoContainer;
    private SurfaceViewRenderer localVideoView;
    private ImageButton btnVideoSpeaker;
    private ImageButton btnVideoMute;
    private ImageButton btnSwitchCamera;
    private ImageButton btnVideoEndCall;
    private View callControls;
    private View callerInfo;

    // Состояния
    private boolean isSpeakerOn = false;
    private boolean isMuted = false;
    private boolean isVideoOn = true;
    private boolean isCallActive = true;
    private boolean isConnected = false;

    // Таймер
    private Handler timerHandler;
    private Runnable timerRunnable;
    private long callStartTime;

    // Audio
    private AudioManager audioManager;

    // WebRTC
    private WebRTCClient webRtcClient;
    private WebSocketSignalingClient signalingClient;
    private String currentUserId;
    private String currentUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // Держим экран включенным
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        initViews();
        getIntentData();
        initUserInfo();
        initWebRTC();
        setupClickListeners();
        updateUI();

        CallManager callManager = CallManager.getInstance(this);
        if (!isOutgoing) {
            callManager.setActiveCall(callId, callerId, callerName, isVideo, false);
            Log.d(TAG, "Set active call in CallManager: callId=" + callId + ", callerId=" + callerId);
        } else {
            callManager.setActiveCall(callId, callerId, callerName, isVideo, true);
            Log.d(TAG, "Set active call in CallManager: callId=" + callId + ", callerId=" + callerId);
        }
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
        callControls = findViewById(R.id.call_controls);
        callerInfo = findViewById(R.id.caller_info);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    private void getIntentData() {
        Intent intent = getIntent();
        callId = intent.getStringExtra("call_id");
        callerName = intent.getStringExtra("caller_name");
        callerId = intent.getStringExtra("caller_id");
        isVideo = intent.getBooleanExtra("is_video", false);
        isOutgoing = intent.getBooleanExtra("is_outgoing", true);

        Log.d(TAG, "Call data - callId: " + callId + ", isVideo: " + isVideo +
                ", isOutgoing: " + isOutgoing + ", callerId: " + callerId);
    }

    private void initUserInfo() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            currentUserName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
            if (currentUserName == null) currentUserName = "User";
        } else {
            currentUserId = "user_" + System.currentTimeMillis();
            currentUserName = "Test User";
        }
        Log.d(TAG, "Current user: " + currentUserId + " (" + currentUserName + ")");
    }

    private void initWebRTC() {
        signalingClient = new WebSocketSignalingClient(WS_SERVER_URL, currentUserId, currentUserName);

        signalingClient.setListener(new WebSocketSignalingClient.SignalingListener() {
            @Override
            public void onConnected() {
                Log.d(TAG, "✅ Signaling connected");
                signalingClient.getOnlineUsers();

                runOnUiThread(() -> {
                    callStatusText.setText("Подключение...");
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        startCallProcedure();
                    }, 1000);
                });
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "Signaling disconnected");
                runOnUiThread(() -> {
                    if (isCallActive) {
                        Toast.makeText(CallActivity.this, "Соединение с сервером потеряно", Toast.LENGTH_SHORT).show();
                        endCall();
                    }
                });
            }

            @Override
            public void onMessage(String message) {
                if (webRtcClient != null) {
                    webRtcClient.onSignalingMessage(message);
                }
            }

            @Override
            public void onIncomingCall(String callId, String fromUserId, String fromUserName, boolean isVideo) {
                Log.d(TAG, "Incoming call from " + fromUserName);
            }

            @Override
            public void onCallAccepted(String callId) {
                Log.d(TAG, "Call accepted: " + callId);
                runOnUiThread(() -> callStatusText.setText("Соединение..."));
            }

            @Override
            public void onCallRejected() {
                Log.d(TAG, "Call rejected");
                runOnUiThread(() -> {
                    Toast.makeText(CallActivity.this, "Звонок отклонен", Toast.LENGTH_SHORT).show();
                    endCall();
                });
            }

            @Override
            public void onUserList(JSONArray users) {
                Log.d(TAG, "Online users: " + users.toString());

                runOnUiThread(() -> {
                    if (isOutgoing && callerId != null && !isCallStarted) {
                        boolean isTargetOnline = false;
                        try {
                            for (int i = 0; i < users.length(); i++) {
                                JSONObject user = users.getJSONObject(i);
                                String userId = user.getString("userId");
                                if (userId.equals(callerId)) {
                                    isTargetOnline = true;
                                    Log.d(TAG, "✅ Target user " + callerId + " is online!");
                                    isCallStarted = true;
                                    callStatusText.setText("Вызов...");
                                    webRtcClient.startCall(callerId, isVideo);
                                    signalingClient.sendCallRequest(callerId, isVideo);
                                    break;
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing user: " + e.getMessage());
                        }

                        if (!isTargetOnline) {
                            callStatusText.setText("Ожидание пользователя...");
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (!isCallStarted && isCallActive) {
                                    signalingClient.getOnlineUsers();
                                }
                            }, 2000);
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Signaling error: " + error);
                runOnUiThread(() -> {
                    Toast.makeText(CallActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                    if (!isCallActive) {
                        endCall();
                    }
                });
            }

            @Override
            public void onUserStatusChanged(String userId, boolean isOnline) {
                Log.d(TAG, "User " + userId + " is now " + (isOnline ? "online" : "offline"));
            }

            @Override
            public void onOfferReceived(String fromUserId, String sdp) {
                Log.d(TAG, "📞 Offer received from " + fromUserId);
                if (webRtcClient != null) {
                    webRtcClient.onRemoteOffer(fromUserId, sdp);
                }
            }

            @Override
            public void onAnswerReceived(String fromUserId, String sdp) {
                Log.d(TAG, "📞 Answer received from " + fromUserId);
                if (webRtcClient != null) {
                    webRtcClient.onRemoteAnswer(fromUserId, sdp);
                }
            }

            @Override
            public void onIceCandidateReceived(String fromUserId, String candidate, int sdpMLineIndex, String sdpMid) {
                Log.d(TAG, "❄️ ICE candidate received from " + fromUserId);
                if (webRtcClient != null) {
                    webRtcClient.addRemoteIceCandidate(fromUserId, candidate, sdpMLineIndex, sdpMid);
                }
            }
        });

        webRtcClient = new WebRTCClient(this, signalingClient);

        webRtcClient.setCallback(new WebRTCClient.WebRTCCallback() {
            @Override
            public void onLocalStreamReady(VideoTrack videoTrack, AudioTrack audioTrack) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Local stream ready");

                    if (isVideo && videoTrack != null && localVideoView != null) {
                        localVideoView.init(webRtcClient.getEglBaseContext(), null);
                        videoTrack.addSink(localVideoView);
                        localVideoView.setMirror(true);
                        localVideoView.setEnableHardwareScaler(true);
                        localVideoContainer.setVisibility(View.VISIBLE);
                        videoPreviewContainer.setVisibility(View.VISIBLE);

                        if (btnSwitchCamera != null) {
                            btnSwitchCamera.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }

            @Override
            public void onRemoteVideoTrack(VideoTrack videoTrack) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Remote video track received");
                    if (isVideo && videoTrack != null && remoteVideoView != null) {
                        remoteVideoView.init(webRtcClient.getEglBaseContext(), null);
                        videoTrack.addSink(remoteVideoView);
                        remoteVideoView.setEnableHardwareScaler(true);
                        remoteVideoView.setVisibility(View.VISIBLE);
                        videoPreviewContainer.setVisibility(View.VISIBLE);

                        if (callerInfo != null) {
                            callerInfo.setVisibility(View.GONE);
                        }
                    }
                });
            }

            @Override
            public void onRemoteAudioTrack(AudioTrack audioTrack) {
                Log.d(TAG, "Remote audio track received");
            }

            @Override
            public void onIceConnectionState(PeerConnection.IceConnectionState state) {
                runOnUiThread(() -> {
                    Log.d(TAG, "ICE state: " + state);
                    switch (state) {
                        case CONNECTED:
                            onCallConnected();
                            break;
                        case FAILED:
                        case CLOSED:
                        case DISCONNECTED:
                            if (isCallActive) {
                                Toast.makeText(CallActivity.this, "Соединение разорвано", Toast.LENGTH_SHORT).show();
                                endCall();
                            }
                            break;
                    }
                });
            }

            @Override
            public void onCallConnected() {
                runOnUiThread(() -> {
                    Log.d(TAG, "✅ Call connected");
                    isConnected = true;
                    callStatusText.setText("В разговоре");
                    callStatusText.setTextColor(getColor(android.R.color.holo_green_dark));
                    startTimer();
                    setupAudioForCall();
                    setupVideoForCall();
                });
            }

            @Override
            public void onCallDisconnected() {
                runOnUiThread(() -> {
                    Log.d(TAG, "Call disconnected");
                    endCall();
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "WebRTC error: " + error);
                runOnUiThread(() -> {
                    Toast.makeText(CallActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                    endCall();
                });
            }
        });

        signalingClient.connect();
    }

    private void setupAudioForCall() {
        Log.d(TAG, "🎧 Configuring audio for call (earpiece mode)...");

        if (audioManager != null) {
            try {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0);
                audioManager.setSpeakerphoneOn(false);
                isSpeakerOn = false;
                updateButtonAlpha(btnSpeaker, isSpeakerOn);
                updateButtonAlpha(btnVideoSpeaker, isSpeakerOn);
                Log.d(TAG, "Audio configured: mode=COMMUNICATION, volume=" + maxVolume + ", speakerphone=OFF");
            } catch (Exception e) {
                Log.e(TAG, "Error configuring audio: " + e.getMessage());
            }
        }
    }

    private void setupVideoForCall() {
        Log.d(TAG, "🎥 Configuring video for call...");

        if (isVideo) {
            if (videoPreviewContainer != null) {
                videoPreviewContainer.setVisibility(View.VISIBLE);
            }
            if (localVideoContainer != null) {
                localVideoContainer.setVisibility(View.VISIBLE);
            }
            if (localVideoView != null && webRtcClient != null) {
                localVideoView.init(webRtcClient.getEglBaseContext(), null);
                localVideoView.setMirror(true);
                localVideoView.setEnableHardwareScaler(true);
            }
            if (remoteVideoView != null && webRtcClient != null) {
                remoteVideoView.init(webRtcClient.getEglBaseContext(), null);
                remoteVideoView.setEnableHardwareScaler(true);
            }
            if (btnSwitchCamera != null) {
                btnSwitchCamera.setVisibility(View.VISIBLE);
            }
            if (callerInfo != null) {
                callerInfo.setVisibility(View.GONE);
            }
            updateVideoButtonsState();
            Log.d(TAG, "Video UI configured");
        } else {
            if (videoPreviewContainer != null) {
                videoPreviewContainer.setVisibility(View.GONE);
            }
            if (localVideoContainer != null) {
                localVideoContainer.setVisibility(View.GONE);
            }
            if (btnSwitchCamera != null) {
                btnSwitchCamera.setVisibility(View.GONE);
            }
            if (callerInfo != null) {
                callerInfo.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateVideoButtonsState() {
        if (btnVideo != null) {
            btnVideo.setImageResource(isVideoOn ? R.drawable.ic_video : R.drawable.ic_video_off);
            btnVideo.setAlpha(isVideoOn ? 1.0f : 0.5f);
        }
        if (btnVideoMute != null) {
            btnVideoMute.setImageResource(isVideoOn ? R.drawable.ic_video : R.drawable.ic_video_off);
            btnVideoMute.setAlpha(isVideoOn ? 1.0f : 0.5f);
        }
    }

    private void startCallProcedure() {
        if (isCallStarted) {
            Log.d(TAG, "Call already started, ignoring");
            return;
        }

        if (isOutgoing) {
            if (callerId == null) {
                Log.e(TAG, "callerId is null");
                Toast.makeText(this, "Ошибка: ID получателя не указан", Toast.LENGTH_SHORT).show();
                endCall();
                return;
            }
            signalingClient.getOnlineUsers();
            callStatusText.setText("Проверка пользователя...");
        } else {
            if (callId == null) {
                Log.e(TAG, "callId is null");
                endCall();
                return;
            }
            Log.d(TAG, "Accepting incoming call from: " + callerId);
            isCallStarted = true;
            webRtcClient.acceptCall(callerId, isVideo);
            callStatusText.setText("Подключение...");
        }
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

    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(isSpeakerOn);
        }
        updateButtonAlpha(btnSpeaker, isSpeakerOn);
        updateButtonAlpha(btnVideoSpeaker, isSpeakerOn);
        String message = isSpeakerOn ? "Динамик включен" : "Динамик выключен";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void toggleMute() {
        isMuted = !isMuted;
        updateButtonAlpha(btnMute, !isMuted);
        updateButtonAlpha(btnVideoMute, !isMuted);
        if (webRtcClient != null) {
            webRtcClient.toggleAudio(!isMuted);
        }
        String message = isMuted ? "Микрофон выключен" : "Микрофон включен";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void toggleVideo() {
        if (!isVideo) {
            Toast.makeText(this, "Это аудиозвонок", Toast.LENGTH_SHORT).show();
            return;
        }

        isVideoOn = !isVideoOn;
        updateButtonAlpha(btnVideo, isVideoOn);
        if (btnVideoMute != null) {
            updateButtonAlpha(btnVideoMute, isVideoOn);
        }
        if (webRtcClient != null) {
            webRtcClient.toggleVideo(isVideoOn);
        }
        if (localVideoContainer != null) {
            localVideoContainer.setVisibility(isVideoOn ? View.VISIBLE : View.GONE);
        }
        updateVideoButtonsState();
        String message = isVideoOn ? "Камера включена" : "Камера выключена";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void switchCamera() {
        if (webRtcClient != null) {
            isFrontCamera = !isFrontCamera;
            webRtcClient.switchCamera();
            if (localVideoView != null) {
                localVideoView.setMirror(isFrontCamera);
            }
            Toast.makeText(this, "Камера переключена", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateButtonAlpha(ImageButton button, boolean isActive) {
        if (button != null) {
            button.setAlpha(isActive ? 1.0f : 0.5f);
        }
    }

    private void updateUI() {
        if (callerNameText != null) {
            callerNameText.setText(callerName != null ? callerName : "Неизвестный");
        }

        if (isOutgoing) {
            callStatusText.setText("Звонок...");
            callStatusText.setTextColor(getColor(android.R.color.holo_orange_dark));
        } else {
            callStatusText.setText("Входящий звонок...");
            callStatusText.setTextColor(getColor(android.R.color.holo_blue_dark));
        }

        if (btnVideo != null) {
            btnVideo.setVisibility(isVideo ? View.VISIBLE : View.GONE);
        }

        if (isVideo && callControls != null) {
            callControls.setVisibility(View.GONE);
        }

        loadCallerAvatar();
    }

    private void loadCallerAvatar() {
        if (callerId != null && !callerId.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("users")
                    .child(callerId)
                    .child("avatarUrl")
                    .get()
                    .addOnSuccessListener(dataSnapshot -> {
                        if (dataSnapshot.exists() && callerAvatar != null) {
                            String avatarUrl = dataSnapshot.getValue(String.class);
                            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                Glide.with(CallActivity.this)
                                        .load(avatarUrl)
                                        .placeholder(R.drawable.ic_person)
                                        .error(R.drawable.ic_person)
                                        .circleCrop()
                                        .into(callerAvatar);
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Ошибка загрузки аватара: " + e.getMessage()));
        }
    }

    private void startTimer() {
        if (timerHandler != null) return;

        callStartTime = System.currentTimeMillis();
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isCallActive || !isConnected) return;

                long duration = System.currentTimeMillis() - callStartTime;
                long seconds = duration / 1000;
                long minutes = seconds / 60;
                seconds = seconds % 60;

                String timeString = String.format("%02d:%02d", minutes, seconds);
                if (callTimerText != null) {
                    callTimerText.setText(timeString);
                    callTimerText.setVisibility(View.VISIBLE);
                }
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void endCall() {
        if (!isCallActive) return;

        Log.d(TAG, "Ending call");
        isCallActive = false;

        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler = null;
            timerRunnable = null;
        }

        if (webRtcClient != null) {
            webRtcClient.hangUp();
        }

        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(false);
        }

        Toast.makeText(this, "Звонок завершён", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler = null;
            timerRunnable = null;
        }

        if (webRtcClient != null && isCallActive) {
            webRtcClient.hangUp();
            webRtcClient.dispose();
            webRtcClient = null;
        }

        if (signalingClient != null) {
            signalingClient.disconnect();
            signalingClient = null;
        }

        getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );
    }

    @Override
    public void onBackPressed() {
        if (isCallActive && isConnected) {
            Toast.makeText(this, "Используйте кнопку завершения звонка", Toast.LENGTH_SHORT).show();
        } else {
            endCall();
        }
        super.onBackPressed();
    }
}