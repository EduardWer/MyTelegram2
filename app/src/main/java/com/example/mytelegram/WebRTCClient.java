package com.example.mytelegram;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.List;

public class WebRTCClient {
    private static final String TAG = "WebRTCClient";

    private Context context;
    private WebSocketSignalingClient signalingClient;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private EglBase rootEglBase;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private AudioSource audioSource;
    private String targetUserId;
    private boolean isVideoCall;
    private WebRTCCallback callback;
    private List<IceCandidate> pendingIceCandidates = new ArrayList<>();
    private VideoCapturer videoCapturer;
    private List<RtpSender> localSenders = new ArrayList<>();
    private boolean isCallActive = false;

    // Состояния видео
    private boolean isVideoEnabled = true;
    private boolean isFrontCamera = true;

    // ICE серверы
    private static final List<PeerConnection.IceServer> ICE_SERVERS = new ArrayList<>();
    static {
        ICE_SERVERS.add(PeerConnection.IceServer.builder("turn:global.turn.metered.ca:443")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("turn:192.168.31.163:3478?transport=tcp")
                .setUsername("myuser")
                .setPassword("mypassword")
                .createIceServer());
    }

    public interface WebRTCCallback {
        void onLocalStreamReady(VideoTrack videoTrack, AudioTrack audioTrack);
        void onRemoteVideoTrack(VideoTrack videoTrack);
        void onRemoteAudioTrack(AudioTrack audioTrack);
        void onIceConnectionState(PeerConnection.IceConnectionState state);
        void onCallConnected();
        void onCallDisconnected();
        void onError(String error);
    }

    public WebRTCClient(Context context, WebSocketSignalingClient signalingClient) {
        this.context = context;
        this.signalingClient = signalingClient;
        initializePeerConnectionFactory();
    }

    public void setCallback(WebRTCCallback callback) {
        this.callback = callback;
    }

    public EglBase.Context getEglBaseContext() {
        if (rootEglBase == null) {
            rootEglBase = EglBase.create();
        }
        return rootEglBase.getEglBaseContext();
    }

    private void initializePeerConnectionFactory() {
        rootEglBase = EglBase.create();

        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        AudioDeviceModule audioDeviceModule = JavaAudioDeviceModule.builder(context)
                .createAudioDeviceModule();

        DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(
                rootEglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory();
    }

    public void createLocalTracks(boolean isVideo) {
        Log.d(TAG, "Creating local tracks, isVideo=" + isVideo);

        // Аудио трек (всегда)
        audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource);
        localAudioTrack.setEnabled(true);

        // Видео трек (только если нужно)
        if (isVideo) {
            createVideoTrack();
        }

        if (callback != null) {
            callback.onLocalStreamReady(localVideoTrack, localAudioTrack);
        }
    }

    private void createVideoTrack() {
        Log.d(TAG, "🎥 Creating video track...");
        try {
            videoSource = peerConnectionFactory.createVideoSource(false);
            videoCapturer = createCameraCapturer();

            if (videoCapturer != null) {
                // ✅ ПРИНУДИТЕЛЬНО УСТАНАВЛИВАЕМ РАЗРЕШЕНИЕ
                SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(
                        "CaptureThread", rootEglBase.getEglBaseContext());

                // Пробуем разные популярные разрешения
                int width = 640;
                int height = 480;

                // Если фронтальная камера, можно меньше
                if (isFrontCamera) {
                    width = 480;
                    height = 640;
                }

                videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
                videoCapturer.startCapture(width, height, 30);
                Log.d(TAG, "✅ Video capture started at " + width + "x" + height + ", 30fps");

                localVideoTrack = peerConnectionFactory.createVideoTrack("video_track", videoSource);
                localVideoTrack.setEnabled(true);
                Log.d(TAG, "✅ Video track created successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating video track: " + e.getMessage(), e);
        }
    }






    private VideoCapturer createCameraCapturer() {
        Log.d(TAG, "Creating camera capturer, front=" + isFrontCamera);

        CameraEnumerator enumerator;
        if (Camera2Enumerator.isSupported(context)) {
            enumerator = new Camera2Enumerator(context);
        } else {
            enumerator = new Camera1Enumerator();
        }

        String[] deviceNames = enumerator.getDeviceNames();


        for (String deviceName : deviceNames) {
            if (isFrontCamera && enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    Log.d(TAG, "Using front camera: " + deviceName);
                    return capturer;
                }
            } else if (!isFrontCamera && enumerator.isBackFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    Log.d(TAG, "Using back camera: " + deviceName);
                    return capturer;
                }
            }
        }


        for (String deviceName : deviceNames) {
            VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
            if (capturer != null) {
                Log.d(TAG, "Using any camera: " + deviceName);
                return capturer;
            }
        }

        Log.e(TAG, "No camera found");
        return null;
    }

    private PeerConnection createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(ICE_SERVERS);
        rtcConfig.enableDscp = true;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        PeerConnection.Observer pcObserver = new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: " + signalingState);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);
                if (callback != null) {
                    callback.onIceConnectionState(iceConnectionState);
                }

                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                    isCallActive = true;
                    if (callback != null) {
                        callback.onCallConnected();
                    }
                } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED ||
                        iceConnectionState == PeerConnection.IceConnectionState.CLOSED ||
                        iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                    isCallActive = false;
                    if (callback != null) {
                        callback.onCallDisconnected();
                    }
                }
            }

            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {
                Log.d(TAG, "onIceConnectionReceivingChange: " + receiving);
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: " + iceGatheringState);
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Log.d(TAG, "onIceCandidate");
                if (signalingClient != null && targetUserId != null) {
                    signalingClient.sendIceCandidate(targetUserId,
                            iceCandidate.sdp, iceCandidate.sdpMLineIndex, iceCandidate.sdpMid);
                }
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                Log.d(TAG, "onIceCandidatesRemoved");
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d(TAG, "onAddStream - deprecated");
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                Log.d(TAG, "onRemoveStream");
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                Log.d(TAG, "onDataChannel");
            }

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded");
            }


            // Добавьте этот метод для отладки
            public boolean hasRemoteVideoTrack() {
                if (peerConnection == null) return false;

                // Проверяем получаемые треки
                List<RtpReceiver> receivers = new ArrayList<>();
                // Примечание: в WebRTC нет прямого метода getReceivers, но можно через getSenders
                // или просто положиться на onAddTrack

                return false;
            }

            // Улучшите onAddTrack для лучшего логирования
            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                Log.d(TAG, "onAddTrack: " + rtpReceiver.id() + ", track type: " +
                        (rtpReceiver.track() instanceof VideoTrack ? "VIDEO" :
                                rtpReceiver.track() instanceof AudioTrack ? "AUDIO" : "UNKNOWN"));

                if (rtpReceiver.track() instanceof VideoTrack) {
                    VideoTrack remoteVideoTrack = (VideoTrack) rtpReceiver.track();
                    Log.d(TAG, "✅ Remote video track received, id: " + remoteVideoTrack.id());
                    Log.d(TAG, "✅ Remote video track enabled: " + remoteVideoTrack.enabled());

                    if (callback != null) {
                        callback.onRemoteVideoTrack(remoteVideoTrack);
                    }
                } else if (rtpReceiver.track() instanceof AudioTrack) {
                    AudioTrack remoteAudioTrack = (AudioTrack) rtpReceiver.track();
                    Log.d(TAG, "✅ Remote audio track received, id: " + remoteAudioTrack.id());

                    if (callback != null) {
                        callback.onRemoteAudioTrack(remoteAudioTrack);
                    }
                }
            }
        };

        return peerConnectionFactory.createPeerConnection(rtcConfig, pcObserver);
    }

    public void startCall(String targetUserId, boolean isVideo) {
        Log.d(TAG, "startCall: targetUserId=" + targetUserId + ", isVideo=" + isVideo);
        this.targetUserId = targetUserId;
        this.isVideoCall = isVideo;

        createLocalTracks(isVideo);
        peerConnection = createPeerConnection();

        // Добавляем аудио трек
        if (localAudioTrack != null) {
            RtpSender audioSender = peerConnection.addTrack(localAudioTrack);
            if (audioSender != null) {
                localSenders.add(audioSender);
            }
        }

        // Добавляем видео трек (если нужно)
        if (isVideo && localVideoTrack != null) {
            RtpSender videoSender = peerConnection.addTrack(localVideoTrack);
            if (videoSender != null) {
                localSenders.add(videoSender);
            }
        }

        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        if (isVideo) {
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        }

        peerConnection.createOffer(createSdpObserver(true), constraints);
    }







    private void recreatePeerConnectionWithVideo() {
        if (peerConnection != null && targetUserId != null) {
            // Создаем новый offer с видео
            MediaConstraints constraints = new MediaConstraints();
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

            peerConnection.createOffer(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    peerConnection.setLocalDescription(new SdpObserver() {
                        @Override
                        public void onSetSuccess() {
                            if (signalingClient != null && targetUserId != null) {
                                signalingClient.sendOffer(targetUserId, sessionDescription.description);
                            }
                        }
                        @Override public void onCreateSuccess(SessionDescription sd) {}
                        @Override public void onSetFailure(String s) {}
                        @Override public void onCreateFailure(String s) {}
                    }, sessionDescription);
                }
                @Override public void onSetSuccess() {}
                @Override public void onCreateFailure(String s) {}
                @Override public void onSetFailure(String s) {}
            }, constraints);
        }
    }





    public void acceptCall(String callerId, boolean isVideo) {
        Log.d(TAG, "Accepting call from: " + callerId + ", isVideo=" + isVideo);
        this.targetUserId = callerId;
        this.isVideoCall = isVideo;

        // Создаем треки только если их еще нет
        if (localAudioTrack == null) {
            createLocalTracks(isVideo);
        } else if (isVideo && localVideoTrack == null) {
            // Аудио уже есть, нужно добавить видео
            createVideoTrack();
        }

        if (peerConnection == null) {
            peerConnection = createPeerConnection();

            if (localAudioTrack != null) {
                peerConnection.addTrack(localAudioTrack);
            }
            if (isVideo && localVideoTrack != null) {
                peerConnection.addTrack(localVideoTrack);
            }
        }
    }

    public void onRemoteOffer(String fromUserId, String sdp) {
        Log.d(TAG, "Processing remote offer from " + fromUserId);
        this.targetUserId = fromUserId;

        // Определяем, есть ли видео в SDP
        boolean hasVideo = sdp.contains("m=video");
        if (hasVideo && !this.isVideoCall) {
            this.isVideoCall = true;
            Log.d(TAG, "Video detected in SDP, enabling video call");
        }

        if (peerConnection == null) {
            createLocalTracks(isVideoCall);
            peerConnection = createPeerConnection();

            if (localAudioTrack != null) {
                peerConnection.addTrack(localAudioTrack);
            }
            if (isVideoCall && localVideoTrack != null) {
                peerConnection.addTrack(localVideoTrack);
            }
        }

        SessionDescription sessionDescription = new SessionDescription(
                SessionDescription.Type.OFFER, sdp);

        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onSetSuccess() {
                Log.d(TAG, "✅ Remote description set, creating answer");

                MediaConstraints constraints = new MediaConstraints();
                constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
                if (isVideoCall) {
                    constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
                }

                peerConnection.createAnswer(createSdpObserver(false), constraints);
            }

            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "Failed to set remote description: " + s);
                if (callback != null) {
                    callback.onError("Failed to set remote description: " + s);
                }
            }
            @Override public void onCreateSuccess(SessionDescription sd) {}
            @Override public void onCreateFailure(String s) {}
        }, sessionDescription);
    }

    public void onRemoteAnswer(String fromUserId, String sdp) {
        Log.d(TAG, "Processing remote answer from " + fromUserId);
        handleAnswer(sdp);
    }

    public void addRemoteIceCandidate(String fromUserId, String candidate, int sdpMLineIndex, String sdpMid) {
        Log.d(TAG, "Adding remote ICE candidate from " + fromUserId);
        handleIceCandidate(candidate, sdpMLineIndex, sdpMid);
    }

    public void onSignalingMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            switch (type) {
                case "offer":
                    String sdp = json.getString("sdp");
                    handleOffer(sdp);
                    break;
                case "answer":
                    String answerSdp = json.getString("sdp");
                    handleAnswer(answerSdp);
                    break;
                case "ice-candidate":
                    String candidate = json.getString("candidate");
                    int sdpMLineIndex = json.getInt("sdpMLineIndex");
                    String sdpMid = json.getString("sdpMid");
                    handleIceCandidate(candidate, sdpMLineIndex, sdpMid);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing signaling message: " + e.getMessage());
        }
    }

    private void handleOffer(String sdp) {
        Log.d(TAG, "Handling offer");

        // Определяем, есть ли видео в SDP
        boolean hasVideo = sdp.contains("m=video");
        if (hasVideo && !this.isVideoCall) {
            this.isVideoCall = true;
            Log.d(TAG, "Video detected in SDP, enabling video call");
        }

        if (peerConnection == null) {
            createLocalTracks(isVideoCall);
            peerConnection = createPeerConnection();

            if (localAudioTrack != null) {
                peerConnection.addTrack(localAudioTrack);
            }
            if (isVideoCall && localVideoTrack != null) {
                peerConnection.addTrack(localVideoTrack);
            }
        }

        SessionDescription sessionDescription = new SessionDescription(
                SessionDescription.Type.OFFER, sdp);

        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Remote description set");

                // Добавляем накопленные ICE кандидаты
                for (IceCandidate candidate : pendingIceCandidates) {
                    peerConnection.addIceCandidate(candidate);
                }
                pendingIceCandidates.clear();

                MediaConstraints constraints = new MediaConstraints();
                constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
                if (isVideoCall) {
                    constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
                }

                peerConnection.createAnswer(createSdpObserver(false), constraints);
            }

            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "onSetFailure: " + s);
                if (callback != null) {
                    callback.onError("Failed to set remote description: " + s);
                }
            }
            @Override public void onCreateSuccess(SessionDescription sd) {}
            @Override public void onCreateFailure(String s) {}
        }, sessionDescription);
    }

    private void handleAnswer(String sdp) {
        Log.d(TAG, "Handling answer");
        SessionDescription sessionDescription = new SessionDescription(
                SessionDescription.Type.ANSWER, sdp);

        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Remote answer set");
                // Добавляем накопленные ICE кандидаты
                for (IceCandidate candidate : pendingIceCandidates) {
                    peerConnection.addIceCandidate(candidate);
                }
                pendingIceCandidates.clear();
            }

            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "onSetFailure: " + s);
                if (callback != null) {
                    callback.onError("Failed to set remote answer: " + s);
                }
            }
            @Override public void onCreateSuccess(SessionDescription sd) {}
            @Override public void onCreateFailure(String s) {}
        }, sessionDescription);
    }

    private void handleIceCandidate(String candidate, int sdpMLineIndex, String sdpMid) {
        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);

        if (peerConnection != null && peerConnection.getRemoteDescription() != null) {
            peerConnection.addIceCandidate(iceCandidate);
            Log.d(TAG, "ICE candidate added immediately");
        } else {
            pendingIceCandidates.add(iceCandidate);
            Log.d(TAG, "ICE candidate queued (remote description not set yet)");
        }
    }

    private SdpObserver createSdpObserver(boolean isOffer) {
        return new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Create " + (isOffer ? "offer" : "answer") + " success");
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set");
                        if (signalingClient != null && targetUserId != null) {
                            if (isOffer) {
                                signalingClient.sendOffer(targetUserId, sessionDescription.description);
                            } else {
                                signalingClient.sendAnswer(targetUserId, sessionDescription.description);
                            }
                        }
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        Log.e(TAG, "onCreateFailure: " + s);
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "onSetFailure: " + s);
                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {}

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "onCreateFailure: " + s);
                if (callback != null) {
                    callback.onError("Failed to create " + (isOffer ? "offer" : "answer") + ": " + s);
                }
            }

            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "onSetFailure: " + s);
                if (callback != null) {
                    callback.onError("Failed to set local description: " + s);
                }
            }
        };
    }

    // ==================== Управление видео ====================

    public void toggleVideo(boolean enable) {
        isVideoEnabled = enable;
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(enable);
            Log.d(TAG, "Video " + (enable ? "enabled" : "disabled"));
        }
    }

    public void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            isFrontCamera = !isFrontCamera;
            CameraVideoCapturer cameraCapturer = (CameraVideoCapturer) videoCapturer;
            cameraCapturer.switchCamera(null);
            Log.d(TAG, "Camera switched, isFront=" + isFrontCamera);
        } else {
            Log.w(TAG, "Cannot switch camera - not a CameraVideoCapturer");
        }
    }

    public boolean isVideoEnabled() {
        return isVideoEnabled;
    }

    // ==================== Управление аудио ====================

    public void toggleAudio(boolean enable) {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enable);
            Log.d(TAG, "Audio " + (enable ? "enabled" : "disabled"));
        }
    }

    // ==================== Завершение звонка ====================

    public void hangUp() {
        Log.d(TAG, "Hanging up call");
        isCallActive = false;

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping capture: " + e.getMessage());
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (localVideoTrack != null) {
            localVideoTrack.dispose();
            localVideoTrack = null;
        }

        localSenders.clear();
        pendingIceCandidates.clear();
    }

    public void dispose() {
        hangUp();

        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }

        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }

        if (rootEglBase != null) {
            rootEglBase.release();
            rootEglBase = null;
        }

        Log.d(TAG, "WebRTCClient disposed");
    }
}