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
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void updateVideoStatus(String userId, boolean enabled) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getUserId().equals(userId)) {
                participants.get(i).setVideoEnabled(enabled);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void setSpeaking(String userId, boolean isSpeaking) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getUserId().equals(userId)) {
                participants.get(i).setSpeaking(isSpeaking);
                notifyItemChanged(i);
                break;
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
            // Имя
            String displayName = participant.isLocal() ? participant.getUserName() + " (Вы)" : participant.getUserName();
            tvUserName.setText(displayName);

            // Индикатор говорящего
            if (speakingIndicator != null) {
                speakingIndicator.setVisibility(participant.isSpeaking() ? View.VISIBLE : View.GONE);
            }

            // Иконки статуса
            if (ivVideoIcon != null) {
                ivVideoIcon.setVisibility(View.VISIBLE);
                ivVideoIcon.setImageResource(participant.isVideoEnabled() ? R.drawable.ic_videocam : R.drawable.ic_video_off);
                ivVideoIcon.setAlpha(participant.isVideoEnabled() ? 1.0f : 0.5f);
            }

            if (ivAudioIcon != null) {
                ivAudioIcon.setVisibility(View.VISIBLE);
                ivAudioIcon.setImageResource(participant.isAudioEnabled() ? R.drawable.ic_microphone : R.drawable.ic_mic_off);
                ivAudioIcon.setAlpha(participant.isAudioEnabled() ? 1.0f : 0.5f);
            }

            // Видео или аватар
            if (participant.getVideoTrack() != null && participant.isVideoEnabled() && participant.getVideoRenderer() != null) {
                videoContainer.setVisibility(View.VISIBLE);
                ivAvatar.setVisibility(View.GONE);
                videoView.setVisibility(View.VISIBLE);

                SurfaceViewRenderer renderer = participant.getVideoRenderer();

                // Очищаем контейнер
                if (videoContainer.getChildCount() > 0 && videoContainer.getChildAt(0) != videoView) {
                    videoContainer.removeAllViews();
                }

                // Добавляем рендерер если нужно
                if (renderer.getParent() != null) {
                    ((ViewGroup) renderer.getParent()).removeView(renderer);
                }

                if (renderer.getParent() == null) {
                    videoContainer.addView(renderer, 0);
                }

                // Подключаем видео
                participant.getVideoTrack().addSink(renderer);

            } else {
                videoContainer.setVisibility(View.GONE);
                ivAvatar.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.GONE);
                loadAvatar(participant.getUserId());
            }
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
                                }
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