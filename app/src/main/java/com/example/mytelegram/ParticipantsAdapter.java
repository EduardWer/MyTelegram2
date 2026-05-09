package com.example.mytelegram;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.firebase.database.FirebaseDatabase;
import de.hdodenhof.circleimageview.CircleImageView;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;
import java.util.List;

public class ParticipantsAdapter extends RecyclerView.Adapter<ParticipantsAdapter.ViewHolder> {

    private List<Participant> participants;
    private OnParticipantClickListener listener;

    public interface OnParticipantClickListener {
        void onParticipantClick(String userId, String userName);
    }

    public ParticipantsAdapter(List<Participant> participants) {
        this.participants = participants;
    }

    public void setOnParticipantClickListener(OnParticipantClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_participant, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Participant participant = participants.get(position);
        holder.bind(participant);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onParticipantClick(participant.getUserId(), participant.getUserName());
            }
        });
    }

    @Override
    public int getItemCount() {
        return participants.size();
    }

    public void addParticipant(Participant participant) {
        participants.add(participant);
        notifyItemInserted(participants.size() - 1);
    }

    public void removeParticipant(String userId) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getUserId().equals(userId)) {
                participants.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    public void updateVideoTrack(String userId, VideoTrack videoTrack, SurfaceViewRenderer renderer) {
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            if (p.getUserId().equals(userId)) {
                p.setVideoTrack(videoTrack);
                p.setVideoRenderer(renderer);
                p.setVideoEnabled(true);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void updateAudioStatus(String userId, boolean enabled) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getUserId().equals(userId)) {
                participants.get(i).setAudioEnabled(enabled);
                // Только обновляем иконку, не пересоздаем видео
                notifyItemChanged(i, "audio");
                break;
            }
        }
    }

    public void updateVideoStatus(String userId, boolean enabled) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getUserId().equals(userId)) {
                participants.get(i).setVideoEnabled(enabled);
                notifyItemChanged(i, "video");
                break;
            }
        }
    }

    public void setSpeaking(String userId, boolean isSpeaking) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getUserId().equals(userId)) {
                participants.get(i).setSpeaking(isSpeaking);
                notifyItemChanged(i, "speaking");
                break;
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            // Частичное обновление - только статусы, без пересоздания видео
            Participant participant = participants.get(position);
            for (Object payload : payloads) {
                if (payload.equals("audio")) {
                    holder.updateAudioIcon(participant.isAudioEnabled());
                } else if (payload.equals("video")) {
                    holder.updateVideoIcon(participant.isVideoEnabled());
                } else if (payload.equals("speaking")) {
                    holder.updateSpeakingIndicator(participant.isSpeaking());
                }
            }
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        FrameLayout videoContainer;
        SurfaceViewRenderer videoView;
        CircleImageView ivAvatar;
        TextView tvUserName;
        ImageView ivVideoIcon, ivAudioIcon;
        View speakingIndicator;
        private SurfaceViewRenderer currentRenderer;
        private String currentUserId;

        ViewHolder(View itemView) {
            super(itemView);
            videoContainer = itemView.findViewById(R.id.video_container);
            videoView = itemView.findViewById(R.id.video_view);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvUserName = itemView.findViewById(R.id.tv_user_name);
            ivVideoIcon = itemView.findViewById(R.id.iv_video_icon);
            ivAudioIcon = itemView.findViewById(R.id.iv_audio_icon);
            speakingIndicator = itemView.findViewById(R.id.speaking_indicator);
        }

        void bind(Participant participant) {
            currentUserId = participant.getUserId();

            // Имя (обновляется всегда)
            String displayName = participant.isLocal() ? participant.getUserName() + " (Вы)" : participant.getUserName();
            tvUserName.setText(displayName);

            // Обновляем иконки статуса
            updateSpeakingIndicator(participant.isSpeaking());
            updateAudioIcon(participant.isAudioEnabled());
            updateVideoIcon(participant.isVideoEnabled());

            // Видео или аватар (только если изменилось состояние видео)
            boolean hasVideo = participant.getVideoTrack() != null && participant.isVideoEnabled() && participant.getVideoRenderer() != null;
            boolean wasShowingVideo = currentRenderer != null && currentRenderer.getVisibility() == View.VISIBLE;

            if (hasVideo && !wasShowingVideo) {
                // Включаем видео
                showVideo(participant);
            } else if (!hasVideo && wasShowingVideo) {
                // Выключаем видео, показываем аватар
                showAvatar(participant);
            } else if (hasVideo && wasShowingVideo && participant.getVideoRenderer() != currentRenderer) {
                // Меняем рендерер
                showVideo(participant);
            } else if (!hasVideo && !wasShowingVideo) {
                // Обновляем аватар если нужно
                loadAvatar(participant.getUserId());
            }
        }

        void updateSpeakingIndicator(boolean isSpeaking) {
            if (speakingIndicator != null) {
                speakingIndicator.setVisibility(isSpeaking ? View.VISIBLE : View.GONE);
            }
        }

        void updateAudioIcon(boolean isAudioEnabled) {
            if (ivAudioIcon != null) {
                if (!isAudioEnabled) {
                    ivAudioIcon.setVisibility(View.VISIBLE);
                    ivAudioIcon.setImageResource(R.drawable.ic_mic_off);
                } else {
                    ivAudioIcon.setVisibility(View.GONE);
                }
            }
        }

        void updateVideoIcon(boolean isVideoEnabled) {
            if (ivVideoIcon != null) {
                if (!isVideoEnabled) {
                    ivVideoIcon.setVisibility(View.VISIBLE);
                    ivVideoIcon.setImageResource(R.drawable.ic_video_off);
                } else {
                    ivVideoIcon.setVisibility(View.GONE);
                }
            }
        }

        private void showVideo(Participant participant) {
            if (participant.getVideoRenderer() == null) return;

            videoContainer.setVisibility(View.VISIBLE);
            ivAvatar.setVisibility(View.GONE);
            videoView.setVisibility(View.GONE);

            SurfaceViewRenderer renderer = participant.getVideoRenderer();
            currentRenderer = renderer;

            // Очищаем контейнер
            if (videoContainer.getChildCount() > 0) {
                videoContainer.removeAllViews();
            }

            // Добавляем рендерер
            if (renderer.getParent() != null) {
                ((ViewGroup) renderer.getParent()).removeView(renderer);
            }

            videoContainer.addView(renderer);

            // Подключаем видео
            if (participant.getVideoTrack() != null) {
                participant.getVideoTrack().addSink(renderer);
            }
        }

        private void showAvatar(Participant participant) {
            videoContainer.setVisibility(View.VISIBLE);
            ivAvatar.setVisibility(View.VISIBLE);
            videoView.setVisibility(View.GONE);

            // Отключаем видео от старого рендерера
            if (currentRenderer != null && participant.getVideoTrack() != null) {
                participant.getVideoTrack().removeSink(currentRenderer);
            }
            currentRenderer = null;

            loadAvatar(participant.getUserId());
        }

        private void loadAvatar(String userId) {
            if (ivAvatar != null && userId != null && !userId.isEmpty()) {
                FirebaseDatabase.getInstance().getReference("users")
                        .child(userId)
                        .child("avatarUrl")
                        .get()
                        .addOnSuccessListener(dataSnapshot -> {
                            if (dataSnapshot.exists() && ivAvatar != null) {
                                String avatarUrl = dataSnapshot.getValue(String.class);
                                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                    Glide.with(ivAvatar.getContext())
                                            .load(avatarUrl)
                                            .placeholder(R.drawable.ic_person)
                                            .error(R.drawable.ic_person)
                                            .circleCrop()
                                            .into(ivAvatar);
                                } else {
                                    ivAvatar.setImageResource(R.drawable.ic_person);
                                }
                            } else if (ivAvatar != null) {
                                ivAvatar.setImageResource(R.drawable.ic_person);
                            }
                        })
                        .addOnFailureListener(e -> {
                            if (ivAvatar != null) {
                                ivAvatar.setImageResource(R.drawable.ic_person);
                            }
                        });
            }
        }
    }
}