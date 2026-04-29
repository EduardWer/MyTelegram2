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
    private DatabaseReference groupsRef;

    public interface OnChatClickListener {
        void onChatClick(Chat chat);
    }

    public ChatsAdapter(List<Chat> chatList, OnChatClickListener listener) {
        this.chatList = chatList;
        this.onChatClickListener = listener;
        this.usersRef = FirebaseDatabase.getInstance().getReference().child("users");
        this.groupsRef = FirebaseDatabase.getInstance().getReference().child("groups");
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

        if (chat.isOnline()) {
            holder.onlineIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.onlineIndicator.setVisibility(View.GONE);
        }
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
        private ImageView groupIcon;
        View onlineIndicator;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);

            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
            textViewUserName = itemView.findViewById(R.id.textViewUserName);
            textViewLastMessage = itemView.findViewById(R.id.textViewLastMessage);
            textViewTime = itemView.findViewById(R.id.textViewTime);
            textViewUnreadCount = itemView.findViewById(R.id.textViewUnreadCount);
            imageViewAvatar = itemView.findViewById(R.id.imageViewAvatar);
            groupIcon = itemView.findViewById(R.id.groupIcon);
        }

        public void bind(Chat chat) {
            // Устанавливаем базовую информацию
            String lastMessage = chat.getLastMessage();
            textViewLastMessage.setText(lastMessage != null ? lastMessage : "");
            textViewTime.setText(formatTime(chat.getTimestamp()));

            // Счетчик непрочитанных
            int unreadCount = chat.getUnreadCount();
            if (unreadCount > 0) {
                textViewUnreadCount.setVisibility(View.VISIBLE);
                textViewUnreadCount.setText(String.valueOf(unreadCount));
            } else {
                textViewUnreadCount.setVisibility(View.GONE);
            }

            // Проверяем тип чата и загружаем соответствующую информацию
            String chatType = chat.getChatType();

            if ("group".equals(chatType)) {
                // Групповой чат
                if (groupIcon != null) {
                    groupIcon.setVisibility(View.VISIBLE);
                }
                loadGroupInfo(chat.getGroupId());
            } else {
                // Личный чат
                if (groupIcon != null) {
                    groupIcon.setVisibility(View.GONE);
                }
                loadUserInfo(chat.getParticipantId());
            }
        }

        private void loadGroupInfo(String groupId) {
            if (groupId == null || groupId.isEmpty()) {
                setDefaultGroupInfo();
                return;
            }

            groupsRef.child(groupId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        // Название группы
                        String groupName = dataSnapshot.child("name").getValue(String.class);
                        if (groupName == null) {
                            groupName = "Группа";
                        }
                        textViewUserName.setText(groupName);

                        // Аватар группы
                        String avatarUrl = dataSnapshot.child("avatarUrl").getValue(String.class);
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            Glide.with(itemView.getContext())
                                    .load(avatarUrl)
                                    .placeholder(R.drawable.ic_person)
                                    .error(R.drawable.ic_person)
                                    .circleCrop()
                                    .into(imageViewAvatar);
                        } else {
                            imageViewAvatar.setImageResource(R.drawable.ic_person);
                        }
                    } else {
                        setDefaultGroupInfo();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    setDefaultGroupInfo();
                }
            });
        }

        private void loadUserInfo(String userId) {
            if (userId == null || userId.isEmpty()) {
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
            DatabaseReference avatarRef = FirebaseDatabase.getInstance()
                    .getReference("avatars")
                    .child(userId);

            avatarRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        String avatarUrl = dataSnapshot.getValue(String.class);
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
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

        private void setDefaultGroupInfo() {
            textViewUserName.setText("Группа");
            imageViewAvatar.setImageResource(R.drawable.ic_person);
        }

        private void setDefaultAvatar() {
            imageViewAvatar.setImageResource(R.drawable.ic_person);
        }

        private String formatTime(long timestamp) {
            try {
                if (timestamp <= 0) {
                    return "";
                }
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            } catch (Exception e) {
                return "";
            }
        }
    }
}