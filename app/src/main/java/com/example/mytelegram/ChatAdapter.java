package com.example.mytelegram;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private List<ChatMessage> messages;
    private String currentUserId;
    private SimpleDateFormat timeFormat;

    public ChatAdapter(List<ChatMessage> messages, String currentUserId) {
        this.messages = messages;
        this.currentUserId = currentUserId;
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message_received, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);

        holder.tvMessage.setText(message.getMessage());
        holder.tvTime.setText(timeFormat.format(new Date(message.getTimestamp())));

        if (message.getUserId().equals(currentUserId)) {
            // Своё сообщение - выравнивание справа
            holder.tvUserName.setText("Вы");
            holder.tvUserName.setTextColor(0xFF4CAF50);
            holder.itemView.setBackgroundResource(R.drawable.bg_message_sent);
        } else {
            // Чужое сообщение - выравнивание слева
            holder.tvUserName.setText(message.getUserName());
            holder.tvUserName.setTextColor(0xFF2196F3);
            holder.itemView.setBackgroundResource(R.drawable.bubble_received);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName;
        TextView tvMessage;
        TextView tvTime;

        ViewHolder(View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tv_user_name);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }
}