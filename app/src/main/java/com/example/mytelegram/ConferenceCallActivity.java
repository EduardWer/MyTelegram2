package com.example.mytelegram;

import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.webrtc.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConferenceCallActivity extends AppCompatActivity {

    private static final String TAG = "ConferenceCall";

    private String roomCode;
    private String userId;
    private String userName;
    private String serverUrl;
    private boolean isCreator;

    private ConferenceWebRTCClient signalingClient;
    private PeerConnectionFactory peerConnectionFactory;
    private EglBase eglBase;

    private Map<String, PeerConnection> peerConnections = new HashMap<>();
    private Map<String, SurfaceViewRenderer> remoteVideoViews = new HashMap<>();

    private FrameLayout localVideoContainer;
    private RecyclerView rvParticipants;
    private SurfaceViewRenderer localVideoView;
    private ImageButton btnToggleAudio, btnToggleVideo, btnSwitchCamera, btnEndCall, btnChat;
    private EditText etMessage;
    private Button btnSend;
    private LinearLayout chatContainer;
    private RecyclerView rvChat;
    private TextView tvRoomCode;
    private TextView tvStatus;

    private VideoCapturer videoCapturer;
    private VideoSource videoSource;

    // Добавьте константы в начало класса:
    private static final String STUN_SERVER = "stun:stun.l.google.com:19302";
    private static final String TURN_SERVER = "turn:192.168.31.163:3478";
    private static final String TURN_USER = "myuser";
    private static final String TURN_PASS = "mypassword";
    private AudioSource audioSource;
    private MediaStream localStream;
    private boolean isAudioEnabled = true;
    private boolean isVideoEnabled = true;
    private boolean isFrontCamera = true;

    private ParticipantsAdapter participantsAdapter;
    private ChatAdapter chatAdapter;
    private List<Participant> participants = new ArrayList<>();
    private List<ChatMessage> chatMessages = new ArrayList<>();
    private boolean isActivityDestroyed = false;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference_call);

        String[] permissions = {
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
        };

        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(permissions, 100);
        }

        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);  // Включаем громкую связь
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0);
            Log.d(TAG, "Speakerphone ON, volume MAX");
        }

        mainHandler = new Handler(Looper.getMainLooper());

        roomCode = getIntent().getStringExtra("room_code");
        userId = getIntent().getStringExtra("user_id");
        userName = getIntent().getStringExtra("user_name");
        serverUrl = getIntent().getStringExtra("server_url");
        isCreator = getIntent().getBooleanExtra("is_creator", false);

        Log.d(TAG, "onCreate: room=" + roomCode + " user=" + userName);

        initViews();
        initWebRTC();
        connectToServer();
    }

    private void initViews() {
        localVideoContainer = findViewById(R.id.local_video_container);
        rvParticipants = findViewById(R.id.rv_participants);
        localVideoView = findViewById(R.id.local_video_view);
        btnToggleAudio = findViewById(R.id.btn_toggle_audio);
        btnToggleVideo = findViewById(R.id.btn_toggle_video);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        btnEndCall = findViewById(R.id.btn_end_call);
        btnChat = findViewById(R.id.btn_chat);
        etMessage = findViewById(R.id.et_message);
        tvStatus = findViewById(R.id.tv_status);
        btnSend = findViewById(R.id.btn_send);
        chatContainer = findViewById(R.id.chat_container);
        rvChat = findViewById(R.id.rv_chat);
        tvRoomCode = findViewById(R.id.tv_room_code);

        tvRoomCode.setText("Комната: " + roomCode);
        if (tvStatus != null) {
            tvStatus.setText("Подключение...");
        }

        participantsAdapter = new ParticipantsAdapter(participants);
        rvParticipants.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvParticipants.setAdapter(participantsAdapter);

        chatAdapter = new ChatAdapter(chatMessages, userId);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(chatAdapter);

        btnToggleAudio.setOnClickListener(v -> toggleAudio());
        btnToggleVideo.setOnClickListener(v -> toggleVideo());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        btnEndCall.setOnClickListener(v -> endCall());
        btnChat.setOnClickListener(v -> toggleChat());
        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void initWebRTC() {
        try {
            PeerConnectionFactory.InitializationOptions initOptions = PeerConnectionFactory
                    .InitializationOptions.builder(this)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions();
            PeerConnectionFactory.initialize(initOptions);

            // ВАЖНО: Добавляем видео кодеки
            DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                    eglBase != null ? eglBase.getEglBaseContext() : null,
                    true,  // enableIntelVp8Encoder
                    true   // enableH264HighProfile
            );

            DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(
                    eglBase != null ? eglBase.getEglBaseContext() : null
            );

            PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
            peerConnectionFactory = PeerConnectionFactory.builder()
                    .setOptions(options)
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory();

            eglBase = EglBase.create();
            localVideoView.setMirror(true);
            localVideoView.init(eglBase.getEglBaseContext(), null);

            createLocalStream();
        } catch (Exception e) {
            Log.e(TAG, "initWebRTC error", e);
            showToast("Ошибка инициализации: " + e.getMessage());
        }
    }



    private void createLocalStream() {
        try {
            localStream = peerConnectionFactory.createLocalMediaStream("stream_" + userId);

            // Аудио - используем стандартные настройки
            MediaConstraints audioConstraints = new MediaConstraints();
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
            // Убираем отключение шумоподавления - пусть работает стандартно

            audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
            AudioTrack audioTrack = peerConnectionFactory.createAudioTrack("audio_" + userId, audioSource);
            audioTrack.setEnabled(true);
            localStream.addTrack(audioTrack);
            Log.d(TAG, "Audio track added, enabled: " + audioTrack.enabled());

            // Видео - тоже нужно создать для локального превью
            videoCapturer = createVideoCapturer();
            if (videoCapturer != null) {
                videoSource = peerConnectionFactory.createVideoSource(false);
                SurfaceTextureHelper helper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
                videoCapturer.initialize(helper, this, videoSource.getCapturerObserver());
                videoCapturer.startCapture(640, 480, 30);

                VideoTrack videoTrack = peerConnectionFactory.createVideoTrack("video_" + userId, videoSource);
                localStream.addTrack(videoTrack);
                videoTrack.addSink(localVideoView);
                Log.d(TAG, "Video track added for local preview");
            } else {
                Log.e(TAG, "Video capturer is null - camera unavailable");
            }
        } catch (Exception e) {
            Log.e(TAG, "createLocalStream error", e);
        }
    }

    private VideoCapturer createVideoCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(this);
        for (String deviceName : enumerator.getDeviceNames()) {
            if (isFrontCamera && enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            }
            if (!isFrontCamera && enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            }
        }
        return null;
    }

    private void connectToServer() {
        signalingClient = new ConferenceWebRTCClient(this, serverUrl, userId, userName);

        signalingClient.setListener(new ConferenceWebRTCClient.ConferenceListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> updateStatus("Подключен к серверу"));
            }

            @Override
            public void onRegistered(String uid, String uname) {}

            @Override
            public void onRoomJoined(String code, boolean creator, List<String> participants) {
                runOnUiThread(() -> {
                    updateStatus("В комнате. Участников: " + participants.size());
                    for (String participantId : participants) {
                        addParticipant(participantId, "Участник", false, false);
                        createPeerConnection(participantId);
                        createOffer(participantId);
                    }
                });
            }

            @Override
            public void onUserJoined(String uid, String uname) {
                runOnUiThread(() -> {
                    addParticipant(uid, uname, false, false);
                    showToast(uname + " присоединился");
                });
            }

            @Override
            public void onUserLeft(String uid, String uname) {
                runOnUiThread(() -> {
                    removeParticipant(uid);
                    closePeerConnection(uid);
                });
            }

            @Override
            public void onOfferReceived(String fromUserId, String fromUserName, String sdp) {
                Log.d(TAG, "Offer received from: " + fromUserName);
                mainHandler.post(() -> {
                    if (!peerConnections.containsKey(fromUserId)) {
                        createPeerConnection(fromUserId);
                    }
                    setRemoteDescription(fromUserId, new SessionDescription(SessionDescription.Type.OFFER, sdp));
                    createAnswer(fromUserId);
                });
            }

            @Override
            public void onAnswerReceived(String fromUserId, String fromUserName, String sdp) {
                Log.d(TAG, "Answer received from: " + fromUserName);
                mainHandler.post(() -> {
                    setRemoteDescription(fromUserId, new SessionDescription(SessionDescription.Type.ANSWER, sdp));
                    updateStatus("Соединение установлено с " + fromUserName);
                });
            }

            @Override
            public void onIceCandidateReceived(String fromUserId, String fromUserName, String candidate, int sdpMLineIndex, String sdpMid) {
                mainHandler.post(() -> {
                    PeerConnection pc = peerConnections.get(fromUserId);
                    if (pc != null) {
                        pc.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, candidate));
                    }
                });
            }

            @Override
            public void onChatMessage(String fromUserId, String fromUserName, String message, long timestamp) {
                mainHandler.post(() -> {
                    chatMessages.add(new ChatMessage(fromUserId, fromUserName, message, timestamp));
                    if (chatAdapter != null) {
                        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                        rvChat.scrollToPosition(chatMessages.size() - 1);
                    }
                });
            }

            @Override
            public void onUserAudioStatusChanged(String uid, boolean enabled) {
                mainHandler.post(() -> updateParticipantAudioStatus(uid, enabled));
            }

            @Override
            public void onUserVideoStatusChanged(String uid, boolean enabled) {
                mainHandler.post(() -> updateParticipantVideoStatus(uid, enabled));
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> showToast("Ошибка: " + error));
            }

            @Override public void onDisconnected() {}
            @Override public void onRoomCreated(String code) {}
            @Override public void onRoomExists(String code, boolean exists) {}
        });

        signalingClient.connect();
        mainHandler.postDelayed(() -> {
            if (signalingClient != null && !isActivityDestroyed) {
                signalingClient.joinRoom(roomCode);
            }
        }, 1000);
    }

    private void updateStatus(String status) {
        if (tvStatus != null) tvStatus.setText(status);
        Log.d(TAG, "Status: " + status);
    }

    private void addParticipant(String uid, String uname, boolean audio, boolean video) {
        participants.add(new Participant(uid, uname, audio, video));
        if (participantsAdapter != null) participantsAdapter.notifyDataSetChanged();
    }

    private void removeParticipant(String uid) {
        participants.removeIf(p -> p.getUserId().equals(uid));
        if (participantsAdapter != null) participantsAdapter.notifyDataSetChanged();
    }

    private void updateParticipantAudioStatus(String uid, boolean enabled) {
        for (Participant p : participants) {
            if (p.getUserId().equals(uid)) {
                p.setAudioEnabled(enabled);
                break;
            }
        }
        if (participantsAdapter != null) participantsAdapter.notifyDataSetChanged();
    }

    private void updateParticipantVideoStatus(String uid, boolean enabled) {
        for (Participant p : participants) {
            if (p.getUserId().equals(uid)) {
                p.setVideoEnabled(enabled);
                break;
            }
        }
        if (participantsAdapter != null) participantsAdapter.notifyDataSetChanged();
    }

    private void createPeerConnection(String participantId) {
        try {
            Log.d(TAG, "Creating PeerConnection for: " + participantId);

            PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(new ArrayList<>());

            config.iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
            config.iceServers.add(PeerConnection.IceServer.builder("turn:192.168.31.163:3478")
                    .setUsername("myuser")
                    .setPassword("mypassword")
                    .createIceServer());

            config.iceTransportsType = PeerConnection.IceTransportsType.ALL;
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
            config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
            config.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
            config.iceConnectionReceivingTimeout = 30000;

            PeerConnection pc = peerConnectionFactory.createPeerConnection(config, new ConfPeerObserver(participantId));

            if (localStream != null) {
                for (AudioTrack track : localStream.audioTracks) {
                    pc.addTrack(track, Arrays.asList("stream_" + userId));
                    Log.d(TAG, "Added audio track: " + track.id());
                }
                for (VideoTrack track : localStream.videoTracks) {
                    pc.addTrack(track, Arrays.asList("stream_" + userId));
                    Log.d(TAG, "Added video track: " + track.id());
                }
            }

            peerConnections.put(participantId, pc);
            Log.d(TAG, "PeerConnection created for: " + participantId);
        } catch (Exception e) {
            Log.e(TAG, "Error creating PeerConnection", e);
        }
    }

    private void createOffer(String participantId) {
        PeerConnection pc = peerConnections.get(participantId);
        if (pc != null) {
            MediaConstraints constraints = new MediaConstraints();
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")); // видео ВКЛЮЧЕНО
            pc.createOffer(new SdpCallback(participantId, "offer"), constraints);
        }
    }

    private void createAnswer(String participantId) {
        PeerConnection pc = peerConnections.get(participantId);
        if (pc != null) {
            MediaConstraints constraints = new MediaConstraints();
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")); // видео ВКЛЮЧЕНО
            pc.createAnswer(new SdpCallback(participantId, "answer"), constraints);
        }
    }

    private void setRemoteDescription(String participantId, SessionDescription sdp) {
        PeerConnection pc = peerConnections.get(participantId);
        if (pc != null) {
            pc.setRemoteDescription(new BaseSdpObserver(), sdp);
        }
    }

    private void closePeerConnection(String participantId) {
        PeerConnection pc = peerConnections.remove(participantId);
        if (pc != null) {
            pc.close();
            pc.dispose();
        }
    }

    private void toggleAudio() {
        isAudioEnabled = !isAudioEnabled;
        if (localStream != null) {
            for (AudioTrack track : localStream.audioTracks) track.setEnabled(isAudioEnabled);
        }
        if (signalingClient != null) signalingClient.toggleAudio(isAudioEnabled);
    }

    private void toggleVideo() {
        isVideoEnabled = !isVideoEnabled;
        if (localStream != null) {
            for (VideoTrack track : localStream.videoTracks) track.setEnabled(isVideoEnabled);
        }
        if (signalingClient != null) signalingClient.toggleVideo(isVideoEnabled);
    }

    private void switchCamera() {
        isFrontCamera = !isFrontCamera;
        if (videoCapturer != null) {
            try { videoCapturer.stopCapture(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            videoCapturer.dispose();
        }
        videoCapturer = createVideoCapturer();
        if (videoCapturer != null && videoSource != null) {
            SurfaceTextureHelper helper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
            videoCapturer.initialize(helper, this, videoSource.getCapturerObserver());
            videoCapturer.startCapture(640, 480, 30);
        }
    }

    private void toggleChat() {
        chatContainer.setVisibility(chatContainer.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void sendMessage() {
        String message = etMessage.getText().toString().trim();
        if (message.isEmpty()) return;
        if (signalingClient != null) signalingClient.sendChatMessage(message);
        chatMessages.add(new ChatMessage(userId, "Вы", message, System.currentTimeMillis()));
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        rvChat.scrollToPosition(chatMessages.size() - 1);
        etMessage.setText("");
    }

    private void endCall() {
        if (signalingClient != null) {
            signalingClient.leaveRoom();
            signalingClient.disconnect();
        }
        for (PeerConnection pc : peerConnections.values()) { pc.close(); pc.dispose(); }
        peerConnections.clear();
        for (SurfaceViewRenderer view : remoteVideoViews.values()) view.release();
        remoteVideoViews.clear();
        if (localVideoView != null) localVideoView.release();
        if (videoCapturer != null) {
            try { videoCapturer.stopCapture(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            videoCapturer.dispose();
        }
        finish();
    }

    private void showToast(String message) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> showToast(message));
            return;
        }
        if (!isActivityDestroyed && !isFinishing()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true;
        endCall();
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);
    }

    // --- Внутренние классы ---

    private class BaseSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sdp) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) { Log.e(TAG, "SDP create fail: " + s); }
        @Override public void onSetFailure(String s) { Log.e(TAG, "SDP set fail: " + s); }
    }

    private class SdpCallback implements SdpObserver {
        private String participantId;
        private String type;

        SdpCallback(String participantId, String type) {
            this.participantId = participantId;
            this.type = type;
        }

        @Override
        public void onCreateSuccess(SessionDescription sdp) {
            Log.d(TAG, "SDP created, type: " + sdp.type);

            PeerConnection pc = peerConnections.get(participantId);
            if (pc != null) {
                pc.setLocalDescription(new BaseSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set. Sending " + type);
                        if (signalingClient != null) {
                            if ("offer".equals(type)) signalingClient.sendOffer(participantId, sdp.description);
                            else signalingClient.sendAnswer(participantId, sdp.description);
                        }
                    }

                    @Override
                    public void onSetFailure(String error) {
                        Log.e(TAG, "Failed: " + error);
                    }
                }, sdp);
            }
        }

        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) { Log.e(TAG, "Create fail: " + s); }
        @Override public void onSetFailure(String s) { Log.e(TAG, "Set fail: " + s); }
    }

    private class ConfPeerObserver implements PeerConnection.Observer {
        private String participantId;

        ConfPeerObserver(String participantId) { this.participantId = participantId; }

        @Override
        public void onIceCandidate(IceCandidate candidate) {
            if (signalingClient != null) {
                signalingClient.sendIceCandidate(participantId, candidate.sdp, candidate.sdpMLineIndex, candidate.sdpMid);
            }
        }

        @Override
        public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
            Log.d(TAG, "onAddTrack from " + participantId + ", kind: " + receiver.track().kind());

            if (receiver.track() instanceof AudioTrack) {
                AudioTrack audioTrack = (AudioTrack) receiver.track();
                audioTrack.setEnabled(true);
                Log.d(TAG, "Received audio track from " + participantId);

                // Включаем громкий динамик
                mainHandler.post(() -> {
                    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                    if (am != null) {
                        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        am.setSpeakerphoneOn(true);
                        int max = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
                        am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, max, 0);
                        Log.d(TAG, "Speakerphone ON, volume: " + max);
                    }
                });
            }

            if (receiver.track() instanceof VideoTrack) {
                VideoTrack videoTrack = (VideoTrack) receiver.track();
                videoTrack.setEnabled(true);
                Log.d(TAG, "Received video track from " + participantId);
                mainHandler.post(() -> {
                    SurfaceViewRenderer videoView = new SurfaceViewRenderer(ConferenceCallActivity.this);
                    videoView.init(eglBase.getEglBaseContext(), null);
                    videoView.setZOrderMediaOverlay(true);
                    remoteVideoViews.put(participantId, videoView);
                    videoTrack.addSink(videoView);

                    FrameLayout container = findViewById(R.id.video_container);
                    if (container != null) {
                        container.addView(videoView, new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT));
                    }
                });
            }
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
            Log.d(TAG, "*** ICE: " + state + " for " + participantId + " ***");
            if (state == PeerConnection.IceConnectionState.CONNECTED) {
                mainHandler.post(() -> {
                    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                    if (am != null) {
                        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        am.setSpeakerphoneOn(true);
                        Log.d(TAG, "*** ICE CONNECTED - SPEAKERPHONE ON ***");
                    }
                });
            }
        }




        @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
        @Override public void onSignalingChange(PeerConnection.SignalingState state) {}

        @Override public void onIceConnectionReceivingChange(boolean receiving) {}
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
        @Override public void onAddStream(MediaStream stream) {}
        @Override public void onRemoveStream(MediaStream stream) {}
        @Override public void onDataChannel(DataChannel channel) {}
        @Override public void onRenegotiationNeeded() {}
    }
}