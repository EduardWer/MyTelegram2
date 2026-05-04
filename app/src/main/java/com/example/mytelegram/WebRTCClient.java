package com.example.mytelegram;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WebRTCClient {
    private static final String TAG = "WebRTCClient";

    private final Context context;
    private final CallManager callManager;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private EglBase rootEglBase;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private AudioSource audioSource;
    private VideoCapturer videoCapturer;
    private boolean isVideoEnabled = false;

    private WebRTCCallback callback;

    public interface WebRTCCallback {
        void onLocalStreamReady(VideoTrack videoTrack, AudioTrack audioTrack);
        void onRemoteVideoTrack(VideoTrack videoTrack);
        void onRemoteAudioTrack(AudioTrack audioTrack);
        void onIceConnectionState(PeerConnection.IceConnectionState state);
        void onError(String error);
    }

    public WebRTCClient(Context context, CallManager callManager) {
        this.context = context.getApplicationContext();
        this.callManager = callManager;
        initializePeerConnectionFactory();
        setupCallManagerCallbacks();
    }

    private void initializePeerConnectionFactory() {
        try {
            rootEglBase = EglBase.create();

            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                            .createInitializationOptions()
            );

            JavaAudioDeviceModule audioDeviceModule = JavaAudioDeviceModule.builder(context)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true)
                    .createAudioDeviceModule();

            peerConnectionFactory = PeerConnectionFactory.builder()
                    .setAudioDeviceModule(audioDeviceModule)
                    .setVideoEncoderFactory(new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true))
                    .setVideoDecoderFactory(new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext()))
                    .createPeerConnectionFactory();

            Log.d(TAG, "PeerConnectionFactory initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing PeerConnectionFactory: " + e.getMessage());
            if (callback != null) {
                callback.onError("Failed to initialize WebRTC: " + e.getMessage());
            }
        }
    }

    private void setupCallManagerCallbacks() {
        callManager.setDataCallback(new CallManager.CallDataCallback() {
            @Override
            public void onOfferReceived(String callId, String sdp) {
                try {
                    SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, sdp);
                    peerConnection.setRemoteDescription(new SimpleSdpObserver() {
                        @Override
                        public void onSetSuccess() {
                            createAnswer();
                        }
                    }, offer);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing offer: " + e.getMessage());
                }
            }

            @Override
            public void onAnswerReceived(String callId, String sdp) {
                try {
                    SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                    peerConnection.setRemoteDescription(new SimpleSdpObserver(), answer);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing answer: " + e.getMessage());
                }
            }

            @Override
            public void onIceCandidateReceived(String callId, String candidate, int lineIndex, String mid) {
                IceCandidate iceCandidate = new IceCandidate(mid, lineIndex, candidate);
                peerConnection.addIceCandidate(iceCandidate);
            }

            @Override
            public void onCallTerminated(String callId) {
                if (callback != null) {
                    callback.onError("Call terminated by other party");
                }
            }
        });
    }

    public void startCall(boolean isVideo) {
        this.isVideoEnabled = isVideo;
        createPeerConnection();
        createLocalTracks();
        createOffer();
    }

    public void acceptCall(boolean isVideo) {
        this.isVideoEnabled = isVideo;
        createPeerConnection();
        createLocalTracks();
    }

    private void createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = peerConnectionFactory.createPeerConnection(config, new PeerConnectionObserver());

        if (peerConnection == null) {
            Log.e(TAG, "Failed to create PeerConnection");
            if (callback != null) {
                callback.onError("Failed to create PeerConnection");
            }
        }
    }

    private void createLocalTracks() {
        // Создаем аудио трек
        audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource);

        // Добавляем аудио трек в PeerConnection
        if (peerConnection != null) {
            peerConnection.addTrack(localAudioTrack, Collections.singletonList("local_stream"));
            Log.d(TAG, "Audio track added to PeerConnection");
        }

        // Создаем видео трек если нужно
        if (isVideoEnabled) {
            videoCapturer = createVideoCapturer();
            if (videoCapturer != null) {
                videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
                try {
                    videoCapturer.startCapture(1280, 720, 30);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting video capture: " + e.getMessage());
                }
                localVideoTrack = peerConnectionFactory.createVideoTrack("video_track", videoSource);

                // Добавляем видео трек в PeerConnection
                if (peerConnection != null) {
                    peerConnection.addTrack(localVideoTrack, Collections.singletonList("local_stream"));
                    Log.d(TAG, "Video track added to PeerConnection");
                }
            }
        }

        if (callback != null) {
            callback.onLocalStreamReady(localVideoTrack, localAudioTrack);
        }
    }

    private VideoCapturer createVideoCapturer() {
        if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator enumerator = new Camera2Enumerator(context);
            for (String deviceName : enumerator.getDeviceNames()) {
                if (enumerator.isFrontFacing(deviceName)) {
                    VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                    if (capturer != null) {
                        Log.d(TAG, "Using Camera2 capturer: " + deviceName);
                        return capturer;
                    }
                }
            }
        }

        Camera1Enumerator enumerator = new Camera1Enumerator(true);
        for (String deviceName : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    Log.d(TAG, "Using Camera1 capturer: " + deviceName);
                    return capturer;
                }
            }
        }

        Log.e(TAG, "No camera found");
        return null;
    }

    private void createOffer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(isVideoEnabled)));

        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sdp);
                String callId = callManager.getActiveCallId();
                if (callId != null) {
                    callManager.sendOffer(callId, sdp.description);
                    Log.d(TAG, "Offer sent successfully");
                }
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Failed to create offer: " + error);
                if (callback != null) {
                    callback.onError("Failed to create offer: " + error);
                }
            }

            @Override
            public void onSetSuccess() {}

            @Override
            public void onSetFailure(String error) {
                Log.e(TAG, "Failed to set local description: " + error);
            }
        }, constraints);
    }

    private void createAnswer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(isVideoEnabled)));

        peerConnection.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sdp);
                String callId = callManager.getActiveCallId();
                if (callId != null) {
                    callManager.sendAnswer(callId, sdp.description);
                    Log.d(TAG, "Answer sent successfully");
                }
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Failed to create answer: " + error);
                if (callback != null) {
                    callback.onError("Failed to create answer: " + error);
                }
            }

            @Override
            public void onSetSuccess() {}

            @Override
            public void onSetFailure(String error) {
                Log.e(TAG, "Failed to set local description: " + error);
            }
        }, constraints);
    }

    public void toggleAudio(boolean enable) {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enable);
            Log.d(TAG, "Audio " + (enable ? "enabled" : "disabled"));
        }
    }

    public void toggleVideo(boolean enable) {
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(enable);
            Log.d(TAG, "Video " + (enable ? "enabled" : "disabled"));
        }
    }

    public void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
            cameraVideoCapturer.switchCamera(null);
            Log.d(TAG, "Camera switched");
        }
    }

    public void hangUp() {
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
                videoCapturer.dispose();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping video capture: " + e.getMessage());
            }
            videoCapturer = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        String callId = callManager.getActiveCallId();
        if (callId != null) {
            callManager.endCall(callId);
        }

        Log.d(TAG, "Call hung up");
    }

    public void dispose() {
        hangUp();

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

    public void setCallback(WebRTCCallback callback) {
        this.callback = callback;
    }

    public EglBase getRootEglBase() {
        return rootEglBase;
    }

    // PeerConnection Observer - реализуем ВСЕ абстрактные методы
    private class PeerConnectionObserver implements PeerConnection.Observer {
        @Override
        public void onIceCandidate(IceCandidate candidate) {
            String callId = callManager.getActiveCallId();
            if (callId != null) {
                callManager.sendIceCandidate(callId, candidate.sdp, candidate.sdpMLineIndex, candidate.sdpMid);
                Log.d(TAG, "ICE candidate sent");
            }
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d(TAG, "ICE connection state: " + iceConnectionState);
            if (callback != null) {
                callback.onIceConnectionState(iceConnectionState);
            }

            if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                Log.d(TAG, "ICE connected");
            } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED ||
                    iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                Log.d(TAG, "ICE disconnected or failed");
            }
        }

        @Override
        public void onTrack(RtpTransceiver transceiver) {
            // Современный метод получения треков
            if (transceiver.getReceiver() != null && transceiver.getReceiver().track() != null) {
                org.webrtc.MediaStreamTrack track = transceiver.getReceiver().track();
                Log.d(TAG, "onTrack: " + track.id() + " kind: " + track.kind());

                if (track.kind().equals("video") && callback != null) {
                    VideoTrack videoTrack = (VideoTrack) track;
                    callback.onRemoteVideoTrack(videoTrack);
                } else if (track.kind().equals("audio") && callback != null) {
                    AudioTrack audioTrack = (AudioTrack) track;
                    callback.onRemoteAudioTrack(audioTrack);
                }
            }
        }

        @Override
        public void onAddStream(MediaStream stream) {
            // Устаревший метод, но должен быть реализован
            Log.d(TAG, "onAddStream (deprecated): " + stream.getId());
            if (stream.videoTracks.size() > 0 && callback != null) {
                callback.onRemoteVideoTrack(stream.videoTracks.get(0));
            }
            if (stream.audioTracks.size() > 0 && callback != null) {
                callback.onRemoteAudioTrack(stream.audioTracks.get(0));
            }
        }

        @Override
        public void onRemoveStream(MediaStream stream) {
            Log.d(TAG, "onRemoveStream: " + stream.getId());
        }

        @Override
        public void onDataChannel(org.webrtc.DataChannel dataChannel) {
            Log.d(TAG, "onDataChannel: " + dataChannel.label());
        }

        @Override
        public void onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded");
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            Log.d(TAG, "onIceCandidatesRemoved: " + iceCandidates.length);
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d(TAG, "Signaling state: " + signalingState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.d(TAG, "ICE connection receiving: " + receiving);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.d(TAG, "ICE gathering state: " + iceGatheringState);
        }

        @Override
        public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
            Log.d(TAG, "onAddTrack: " + receiver.id());
        }
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {}
        @Override
        public void onSetSuccess() {}
        @Override
        public void onCreateFailure(String error) {}
        @Override
        public void onSetFailure(String error) {}
    }
}