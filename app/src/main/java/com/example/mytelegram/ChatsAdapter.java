
package com.example.mytelegram;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatsAdapter extends RecyclerView.Adapter<ChatsAdapter.ChatViewHolder> {
    private List<Chat> chatList;
    private final OnChatClickListener onChatClickListener;
    private DatabaseReference usersRef;

    public interface OnChatClickListener {
        void onChatClick(Chat chat);
    }

    public ChatsAdapter(List<Chat> chatList, OnChatClickListener listener) {
        this.chatList = chatList;
        this.onChatClickListener = listener;
        this.usersRef = FirebaseDatabase.getInstance().getReference().child("users");
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Chat chat = chatList.get(position);
        holder.bind(chat);

        holder.itemView.setOnClickListener(v -> {
            onChatClickListener.onChatClick(chat);
        });
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public void setChats(List<Chat> chats) {
        this.chatList = chats;
        notifyDataSetChanged();
    }

    public List<Chat> getChats() {
        return chatList;
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        private TextView textViewUserName;
        private TextView textViewLastMessage;
        private TextView textViewTime;
        private TextView textViewUnreadCount;
        private ImageView imageViewAvatar;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewUserName = itemView.findViewById(R.id.textViewUserName);
            textViewLastMessage = itemView.findViewById(R.id.textViewLastMessage);
            textViewTime = itemView.findViewById(R.id.textViewTime);
            textViewUnreadCount = itemView.findViewById(R.id.textViewUnreadCount);
            imageViewAvatar = itemView.findViewById(R.id.imageViewAvatar);
        }

        public void bind(Chat chat) {
            // Устанавливаем базовую информацию
            textViewLastMessage.setText(chat.getLastMessage());
            textViewTime.setText(formatTime(chat.getTimestamp()));

            // Счетчик непрочитанных
            if (chat.getUnreadCount() > 0) {
                textViewUnreadCount.setVisibility(View.VISIBLE);
                textViewUnreadCount.setText(String.valueOf(chat.getUnreadCount()));
            } else {
                textViewUnreadCount.setVisibility(View.GONE);
            }

            // Загружаем информацию о пользователе
            loadUserInfo(chat.getParticipantId());
        }

        private void loadUserInfo(String userId) {
            if (userId == null) {
                setDefaultUserInfo();
                return;
            }

            usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        // Получаем имя пользователя
                        String userName = dataSnapshot.child("username").getValue(String.class);
                        if (userName == null) {
                            userName = dataSnapshot.child("name").getValue(String.class);
                        }
                        if (userName == null) {
                            userName = "Пользователь";
                        }

                        textViewUserName.setText(userName);

                        // Загружаем аватар
                        loadUserAvatar(userId);

                    } else {
                        setDefaultUserInfo();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    setDefaultUserInfo();
                }
            });
        }

        private void loadUserAvatar(String userId) {
            // Загружаем аватар из узла avatars
            DatabaseReference avatarRef = FirebaseDatabase.getInstance()
                    .getReference("avatars")
                    .child(userId);

            avatarRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        String avatarUrl = dataSnapshot.getValue(String.class);
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            // Загружаем аватар с помощью Glide
                            Glide.with(itemView.getContext())
                                    .load(avatarUrl)
                                    .placeholder(R.drawable.ic_person)
                                    .error(R.drawable.ic_person)
                                    .circleCrop()
                                    .into(imageViewAvatar);
                        } else {
                            setDefaultAvatar();
                        }
                    } else {
                        setDefaultAvatar();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    setDefaultAvatar();
                }
            });
        }

        private void setDefaultUserInfo() {
            textViewUserName.setText("Неизвестный пользователь");
            setDefaultAvatar();
        }

        private void setDefaultAvatar() {
            imageViewAvatar.setImageResource(R.drawable.ic_person);
        }

        private String formatTime(long timestamp) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            } catch (Exception e) {
                return "";
            }
        }
    }
}