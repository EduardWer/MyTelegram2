package com.example.mytelegram;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ParticipantsAdapter extends RecyclerView.Adapter<ParticipantsAdapter.ViewHolder> {

    private List<Participant> participants;

    public ParticipantsAdapter(List<Participant> participants) {
        this.participants = participants;
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
        holder.tvUserName.setText(participant.getUserName());

        // Индикаторы статуса
        holder.tvAudioStatus.setVisibility(participant.isAudioEnabled() ? View.GONE : View.VISIBLE);
        holder.tvVideoStatus.setVisibility(participant.isVideoEnabled() ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return participants.size();
    }

    public void updateParticipants(List<Participant> newParticipants) {
        this.participants = newParticipants;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName;
        TextView tvAudioStatus;
        TextView tvVideoStatus;
        View videoPlaceholder;

        ViewHolder(View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tv_user_name);
            tvAudioStatus = itemView.findViewById(R.id.tv_audio_status);
            tvVideoStatus = itemView.findViewById(R.id.tv_video_status);
            videoPlaceholder = itemView.findViewById(R.id.video_placeholder);
        }
    }
}