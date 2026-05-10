package com.example.mytelegram;

import static androidx.core.util.TypedValueCompat.dpToPx;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ConferenceCallActivity extends AppCompatActivity {

    private static final String TAG = "ConferenceCall";

    private List<Participant> participants = new ArrayList<>();
    private List<String> participantsIds = new ArrayList<>(); // Для отслеживания ID

    private String roomCode;
    private String userId;
    private String userName;

    private RecyclerView rvParticipants;  // Сетка для всех участников
    private ParticipantsAdapter participantsAdapter;
    private VideoCapturer videoCapturer;
    private VideoSource videoSource;
    private ProgressBar progressBar;
    private String serverUrl;
    private boolean isCreator;

    private ConferenceWebRTCClient signalingClient;
    private PeerConnectionFactory peerConnectionFactory;
    private EglBase eglBase;

    private Map<String, PeerConnection> peerConnections = new HashMap<>();
    private Map<String, SurfaceViewRenderer> remoteVideoViews = new HashMap<>();



    private SurfaceViewRenderer localVideoView;
    private ImageButton btnToggleAudio, btnToggleVideo, btnSwitchCamera, btnEndCall, btnChat;
    private EditText etMessage;
    private ImageButton btnSend;
    private LinearLayout chatContainer;
    private RecyclerView rvChat;
    private TextView tvRoomCode;
    private TextView tvStatus;




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


    private FrameLayout videoContainer;  // Контейнер для видео


    private ChatAdapter chatAdapter;

    private List<ChatMessage> chatMessages = new ArrayList<>();
    private boolean isActivityDestroyed = false;
    private Handler mainHandler;
    private TextView tvParticipantsCount;

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

    @SuppressLint("SetTextI18n")
    private void initViews() {
        // Находим RecyclerView для списка участников
        rvParticipants = findViewById(R.id.rv_video_grid);

        // Настройка сетки (2 колонки для телефона, 3 для планшета)
        int spanCount = getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 2 : 1;
        rvParticipants.setLayoutManager(new GridLayoutManager(this, spanCount));
        participantsAdapter = new ParticipantsAdapter(participants);
        rvParticipants.setAdapter(participantsAdapter);
        progressBar = findViewById(R.id.progress_bar);
        // Находим остальные кнопки
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
        tvParticipantsCount = findViewById(R.id.tv_participants_count);



        tvRoomCode.setText("Комната: " + roomCode);

        chatAdapter = new ChatAdapter(chatMessages, userId);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(chatAdapter);

        btnToggleAudio.setOnClickListener(v -> toggleAudio());
        btnToggleVideo.setOnClickListener(v -> toggleVideo());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        btnEndCall.setOnClickListener(v -> endCall());
        btnChat.setOnClickListener(v -> toggleChat());
        btnChat.setOnClickListener(v -> loadUsersFromSameDomain());
    }

    private void initWebRTC() {
        try {
            PeerConnectionFactory.InitializationOptions initOptions = PeerConnectionFactory
                    .InitializationOptions.builder(this)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions();
            PeerConnectionFactory.initialize(initOptions);

            DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                    null,  // eglBase еще нет, передаем null
                    true, true
            );

            DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(null);

            PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
            peerConnectionFactory = PeerConnectionFactory.builder()
                    .setOptions(options)
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory();

            // ВАЖНО: СНАЧАЛА создаем eglBase
            eglBase = EglBase.create();
            Log.d(TAG, "✅ EglBase created");

            // ПОТОМ создаем локальный поток (здесь eglBase уже не null)
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

                // СОЗДАЕМ РЕНДЕРЕР ДЛЯ СЕБЯ
                SurfaceViewRenderer localRenderer = new SurfaceViewRenderer(this);
                localRenderer.init(eglBase.getEglBaseContext(), null);
                localRenderer.setMirror(true);
                videoTrack.addSink(localRenderer);

                // ДОБАВЛЯЕМ СЕБЯ В АДАПТЕР
                Participant localParticipant = new Participant(userId, userName, true, true);
                localParticipant.setIsLocal(true);
                localParticipant.setVideoTrack(videoTrack);
                localParticipant.setVideoRenderer(localRenderer);
                participantsAdapter.addParticipant(localParticipant);




                Log.d(TAG, "✅ Local video added to grid");
            } else {
                Log.e(TAG, "❌ Video capturer is null");
                // Добавляем себя без видео
                Participant localParticipant = new Participant(userId, userName, true, false);
                localParticipant.setIsLocal(true);
                participantsAdapter.addParticipant(localParticipant);
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
            public void onRoomJoined(String code, boolean creator, List<String> participantsList) {
                runOnUiThread(() -> {
                    int otherCount = participantsList != null ? participantsList.size() : 0;
                    updateStatus("В комнате. Участников: " + (otherCount + 1));

                    // Добавляем других участников (себя добавит createLocalStream)
                    if (participantsList != null) {
                        for (String participantId : participantsList) {
                            if (!participantId.equals(userId)) {
                                addParticipant(participantId, "Участник", true, false);
                                createPeerConnection(participantId);
                                createOffer(participantId);
                            }
                        }
                    }

                    updateParticipantsCount();
                });
            }

            private void updateParticipantsCount() {
                if (tvParticipantsCount != null) {
                    int count = participants.size();
                    String text = count + " " + getCountEnding(count);
                    tvParticipantsCount.setText(text);
                }
            }

            private String getCountEnding(int count) {
                if (count % 10 == 1 && count % 100 != 11) {
                    return "участник";
                } else if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20)) {
                    return "участника";
                } else {
                    return "участников";
                }
            }

            @Override
            public void onUserJoined(String uid, String uname) {
                runOnUiThread(() -> {
                    if (!uid.equals(userId)) {
                        addParticipant(uid, uname, true, false);
                        createPeerConnection(uid);
                        showToast(uname + " присоединился");
                    }
                });
            }

            // Обработчик выхода участника
            @Override
            public void onUserLeft(String uid, String uname) {
                runOnUiThread(() -> {
                    removeParticipant(uid);
                    closePeerConnection(uid);
                    showToast(uname + " покинул комнату");
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
        // Проверяем, не добавлен ли уже
        for (Participant p : participants) {
            if (p.getUserId().equals(uid)) {
                Log.d(TAG, "Participant already exists: " + uid);
                return;
            }
        }

        participants.add(new Participant(uid, uname, audio, video));
        if (participantsAdapter != null) participantsAdapter.notifyDataSetChanged();

        updateParticipantsCount();  // ← ДОБАВЬТЕ ЭТУ СТРОКУ
        Log.d(TAG, "➕ Participant added: " + uname + ", total: " + participants.size());
    }

    private void removeParticipant(String uid) {
        String removedName = null;
        for (Participant p : participants) {
            if (p.getUserId().equals(uid)) {
                removedName = p.getUserName();
                break;
            }
        }

        participants.removeIf(p -> p.getUserId().equals(uid));

        if (participantsAdapter != null) {
            participantsAdapter.removeParticipant(uid);
        }

        updateParticipantsCount();
        Log.d(TAG, "➖ Participant removed: " + removedName + ", total: " + participants.size());
    }

    private void updateParticipantsCount() {
        if (tvParticipantsCount != null) {
            int count = participants.size();
            String text;

            if (count == 0) {
                text = "0 участников";
            } else if (count == 1) {
                text = "1 участник";
            } else if (count >= 2 && count <= 4) {
                text = count + " участника";
            } else {
                text = count + " участников";
            }

            tvParticipantsCount.setText(text);
            Log.d(TAG, "📊 Participants count: " + text);
        } else {
            Log.e(TAG, "❌ tvParticipantsCount is null!");
        }
    }

    private void updateParticipantAudioStatus(String uid, boolean enabled) {
        participantsAdapter.updateAudioStatus(uid, enabled);
    }

    private void updateParticipantVideoStatus(String uid, boolean enabled) {
        participantsAdapter.updateVideoStatus(uid, enabled);
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

        // Обновляем статус в адаптере для себя
        participantsAdapter.updateAudioStatus(userId, isAudioEnabled);
    }

    private void toggleVideo() {
        isVideoEnabled = !isVideoEnabled;

        if (localStream != null) {
            for (VideoTrack track : localStream.videoTracks) {
                track.setEnabled(isVideoEnabled);
            }
        }

        if (signalingClient != null) {
            signalingClient.toggleVideo(isVideoEnabled);
        }

        // Обновляем иконку кнопки


        // ОБНОВЛЯЕМ СТАТУС В АДАПТЕРЕ ДЛЯ СЕБЯ
        if (participantsAdapter != null) {
            participantsAdapter.updateVideoStatus(userId, isVideoEnabled);
        }

        // Показываем уведомление
        String message = isVideoEnabled ? "Камера включена" : "Камера выключена";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        Log.d(TAG, "Video toggled: " + (isVideoEnabled ? "ON" : "OFF"));
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







// логика юзеров



    private void loadUsersFromSameDomain() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Current user is null");
            return;
        }

        String currentUserEmail = currentUser.getEmail();
        String currentUserId = currentUser.getUid();

        if (currentUserEmail == null) {
            Log.e(TAG, "Current user email is null");
            return;
        }

        String domain = currentUserEmail.split("@")[1];
        Log.d(TAG, "🔍 Searching for users with domain: " + domain);

        showLoading(true);

        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                showLoading(false);
                List<User> userList = new ArrayList<>();

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    try {
                        String uid = userSnapshot.child("uid").getValue(String.class);
                        String email = userSnapshot.child("email").getValue(String.class);
                        String username = userSnapshot.child("username").getValue(String.class);

                        if (email != null && uid != null &&
                                email.contains("@") &&
                                email.split("@")[1].equals(domain) &&
                                !uid.equals(currentUserId)) {

                            User user = new User();
                            user.setUid(uid);
                            user.setEmail(email);
                            user.setUsername(username != null ? username : email.split("@")[0]);
                            userList.add(user);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing user: " + e.getMessage());
                    }
                }

                // Сортируем по имени
                Collections.sort(userList, (u1, u2) -> {
                    if (u1.getUsername() == null) return 1;
                    if (u2.getUsername() == null) return -1;
                    return u1.getUsername().compareToIgnoreCase(u2.getUsername());
                });

                Log.d(TAG, "📊 Total users found: " + userList.size());

                if (userList.isEmpty()) {
                    Toast.makeText(ConferenceCallActivity.this,
                            "📭 Нет пользователей с доменом " + domain, Toast.LENGTH_SHORT).show();
                } else {
                    showUserDialogWithSearch(userList);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Error loading users: " + error.getMessage());
                Toast.makeText(ConferenceCallActivity.this,
                        "❌ Ошибка загрузки: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showUserDialogWithSearch(List<User> users) {
        // Сохраняем оригинальный список
        List<User> originalUsers = new ArrayList<>(users);

        // Сортируем пользователей по имени
        Collections.sort(originalUsers, (u1, u2) -> {
            String name1 = u1.getUsername() != null ? u1.getUsername() : "";
            String name2 = u2.getUsername() != null ? u2.getUsername() : "";
            return name1.compareToIgnoreCase(name2);
        });

        // Создаем кастомный адаптер с красивым отображением
        ArrayAdapter<User> adapter = new ArrayAdapter<User>(this, android.R.layout.select_dialog_item, originalUsers) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_user_dialog, parent, false);
                }

                User user = getItem(position);

                CircleImageView ivAvatar = convertView.findViewById(R.id.iv_avatar);
                TextView tvName = convertView.findViewById(R.id.tv_user_name);
                TextView tvEmail = convertView.findViewById(R.id.tv_user_email);

                tvName.setText(user.getUsername() != null ? user.getUsername() : "Без имени");
                tvEmail.setText(user.getEmail() != null ? user.getEmail() : "");

                // Загружаем аватар (если есть)
                if (user.getAvatarUrls() != null && !user.getAvatarUrls().isEmpty()) {
                    Glide.with(ConferenceCallActivity.this)
                            .load(user.getAvatarUrls())
                            .placeholder(R.drawable.ic_person)
                            .error(R.drawable.ic_person)
                            .circleCrop()
                            .into(ivAvatar);
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_person);
                }

                return convertView;
            }
        };

        // Создаем поле для поиска с иконкой
        LinearLayout searchLayout = new LinearLayout(this);
        searchLayout.setOrientation(LinearLayout.VERTICAL);
        searchLayout.setPadding(16, 16, 16, 8);

        EditText searchInput = new EditText(this);
        searchInput.setHint("🔍 Поиск по имени или email");
        searchInput.setBackgroundResource(android.R.drawable.editbox_dropdown_dark_frame);
        searchInput.setPadding(24, 16, 24, 16);

        // Иконка поиска в поле
        Drawable searchIcon = getResources().getDrawable(android.R.drawable.ic_menu_search, null);
        searchIcon.setBounds(0, 0, 40, 40);
        searchInput.setCompoundDrawables(searchIcon, null, null, null);
        searchInput.setCompoundDrawablePadding(16);

        searchLayout.addView(searchInput);

        // Счетчик результатов
        TextView tvCounter = new TextView(this);
        tvCounter.setText("👥 " + originalUsers.size() + " участников");
        tvCounter.setTextColor(getColor(android.R.color.white));
        tvCounter.setTextSize(12);
        tvCounter.setPadding(16, 8, 16, 8);
        tvCounter.setBackgroundColor(getColor(R.color.teal_700));
        tvCounter.setVisibility(View.GONE);
        searchLayout.addView(tvCounter);

        // Настраиваем диалог
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialog);
        builder.setTitle("✨ ПРИГЛАСИТЬ УЧАСТНИКА")
                .setView(searchLayout)
                .setAdapter(adapter, (dialog, which) -> {
                    User selectedUser = adapter.getItem(which);
                    if (selectedUser != null) {
                        sendInvitation(selectedUser);
                    }
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .setIcon(R.drawable.ic_group_add);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Настраиваем цвет кнопок
        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negativeButton != null) {
            negativeButton.setTextColor(getColor(android.R.color.holo_red_dark));
        }

        // Добавляем фильтрацию при вводе текста
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase().trim();
                List<User> filtered = new ArrayList<>();

                if (query.isEmpty()) {
                    filtered.addAll(originalUsers);
                    tvCounter.setVisibility(View.GONE);
                } else {
                    for (User user : originalUsers) {
                        String name = user.getUsername() != null ? user.getUsername().toLowerCase() : "";
                        String email = user.getEmail() != null ? user.getEmail().toLowerCase() : "";

                        if (name.contains(query) || email.contains(query)) {
                            filtered.add(user);
                        }
                    }
                    tvCounter.setVisibility(View.VISIBLE);
                    tvCounter.setText("🔍 Найдено: " + filtered.size() + " из " + originalUsers.size());
                }

                adapter.clear();
                adapter.addAll(filtered);
                adapter.notifyDataSetChanged();

                // Обновляем заголовок диалога
                if (filtered.size() > 0) {
                    dialog.setTitle("✨ ПРИГЛАСИТЬ УЧАСТНИКА (" + filtered.size() + ")");
                } else {
                    dialog.setTitle("✨ ПРИГЛАСИТЬ УЧАСТНИКА");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showUserDialog(List<User> users) {
        showUserDialogWithSearch(users);
    }

    private void sendInvitation(User user) {
        showLoading(true);

        Map<String, Object> invitation = new HashMap<>();
        invitation.put("roomCode", roomCode);
        invitation.put("inviterId", userId);
        invitation.put("inviterName", userName);
        invitation.put("roomName", "Конференция " + roomCode);
        invitation.put("timestamp", System.currentTimeMillis());

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users")
                .child(user.getUid()).child("invitations").push();

        userRef.setValue(invitation)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "✅ Приглашение отправлено пользователю " + user.getUsername(), Toast.LENGTH_LONG).show();
                    sendPushNotification(user, roomCode);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "❌ Ошибка отправки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void sendPushNotification(User user, String roomCode) {
        OkHttpClient client = new OkHttpClient();

        JSONObject json = new JSONObject();
        try {
            json.put("user_id", user.getUid());
            json.put("title", "📞 Приглашение в конференцию");
            json.put("body", userName + " приглашает вас в конференцию. Код: " + roomCode);
            json.put("chat_id", roomCode);
            json.put("type", "conference_invite");
            json.put("room_code", roomCode);

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url("http://192.168.31.163:8000/send-to-user")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Push notification failed: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    Log.d(TAG, "✅ Push notification sent");
                    response.close();
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error creating push notification", e);
        }
    }
//
    private void showLoading(boolean show) {
        runOnUiThread(() -> {
            if (progressBar != null) {
                progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
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

                // Обновляем статус аудио в адаптере
                mainHandler.post(() -> {
                    participantsAdapter.updateAudioStatus(participantId, true);
                });
            }

            if (receiver.track() instanceof VideoTrack) {
                VideoTrack videoTrack = (VideoTrack) receiver.track();
                videoTrack.setEnabled(true);
                Log.d(TAG, "Received video track from " + participantId);

                mainHandler.post(() -> {
                    try {
                        // Создаем рендерер для видео
                        SurfaceViewRenderer videoView = new SurfaceViewRenderer(ConferenceCallActivity.this);
                        videoView.init(eglBase.getEglBaseContext(), null);
                        videoView.setMirror(false);
                        videoTrack.addSink(videoView);

                        // ДОБАВЛЯЕМ ИЛИ ОБНОВЛЯЕМ УЧАСТНИКА В АДАПТЕРЕ
                        participantsAdapter.updateVideoTrack(participantId, videoTrack, videoView);
                        participantsAdapter.updateVideoStatus(participantId, true);

                        Log.d(TAG, "✅ Remote video added for: " + participantId);

                    } catch (Exception e) {
                        Log.e(TAG, "Error adding video", e);
                    }
                });
            }
        }

        private int dpToPx(int dp) {
            return (int) (dp * getResources().getDisplayMetrics().density);
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