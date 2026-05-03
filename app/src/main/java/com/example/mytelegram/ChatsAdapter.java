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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatsAdapter extends RecyclerView.Adapter<ChatsAdapter.ChatViewHolder> {
    private List<Chat> chatList;
    private final OnChatClickListener onChatClickListener;
    private DatabaseReference usersRef;
    private DatabaseReference groupsRef;
    private DatabaseReference avatarsRef;

    // Кэш для аватарок, чтобы не перезагружать
    private final Map<String, String> avatarCache = new HashMap<>();
    private final Map<String, String> userNameCache = new HashMap<>();
    private final Map<String, String> groupNameCache = new HashMap<>();

    public interface OnChatClickListener {
        void onChatClick(Chat chat);
    }

    public ChatsAdapter(List<Chat> chatList, OnChatClickListener listener) {
        this.chatList = chatList != null ? chatList : new ArrayList<>();
        this.onChatClickListener = listener;
        this.usersRef = FirebaseDatabase.getInstance().getReference().child("users");
        this.groupsRef = FirebaseDatabase.getInstance().getReference().child("groups");
        this.avatarsRef = FirebaseDatabase.getInstance().getReference().child("avatars");
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
        this.chatList = chats != null ? chats : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<Chat> getChats() {
        return chatList;
    }

    /**
     * Обновляет один чат в списке И ОБНОВЛЯЕТ КЭШ
     */
    public void updateChat(Chat updatedChat) {
        if (updatedChat == null || updatedChat.getChatId() == null) return;

        for (int i = 0; i < chatList.size(); i++) {
            Chat currentChat = chatList.get(i);
            if (currentChat != null && updatedChat.getChatId().equals(currentChat.getChatId())) {
                chatList.set(i, updatedChat);

                // ОБНОВЛЯЕМ КЭШ
                if (!updatedChat.isGroupChat()) {
                    String userId = updatedChat.getParticipantId();
                    if (userId != null) {
                        if (updatedChat.getParticipantName() != null && !updatedChat.getParticipantName().isEmpty()) {
                            userNameCache.put(userId, updatedChat.getParticipantName());
                        }
                        if (updatedChat.getParticipantAvatar() != null && !updatedChat.getParticipantAvatar().isEmpty()) {
                            avatarCache.put(userId, updatedChat.getParticipantAvatar());
                        }
                    }
                } else {
                    // Для группы
                    String groupId = updatedChat.getChatId();
                    if (updatedChat.getGroupName() != null && !updatedChat.getGroupName().isEmpty()) {
                        groupNameCache.put(groupId, updatedChat.getGroupName());
                    }
                    if (updatedChat.getParticipantAvatar() != null && !updatedChat.getParticipantAvatar().isEmpty()) {
                        avatarCache.put(groupId, updatedChat.getParticipantAvatar());
                    }
                }

                notifyItemChanged(i);
                break;
            }
        }
    }

    /**
     * Обновляет чат на конкретной позиции И ОБНОВЛЯЕТ КЭШ
     */
    public void updateChatAtPosition(Chat chat, int position) {
        if (position >= 0 && position < chatList.size()) {
            chatList.set(position, chat);

            // ОБНОВЛЯЕМ КЭШ
            if (!chat.isGroupChat()) {
                String userId = chat.getParticipantId();
                if (userId != null) {
                    if (chat.getParticipantName() != null && !chat.getParticipantName().isEmpty()) {
                        userNameCache.put(userId, chat.getParticipantName());
                    }
                    if (chat.getParticipantAvatar() != null && !chat.getParticipantAvatar().isEmpty()) {
                        avatarCache.put(userId, chat.getParticipantAvatar());
                    }
                }
            } else {
                // Для группы
                String groupId = chat.getChatId();
                if (chat.getGroupName() != null && !chat.getGroupName().isEmpty()) {
                    groupNameCache.put(groupId, chat.getGroupName());
                }
                if (chat.getParticipantAvatar() != null && !chat.getParticipantAvatar().isEmpty()) {
                    avatarCache.put(groupId, chat.getParticipantAvatar());
                }
            }

            notifyItemChanged(position);
        }
    }

    /**
     * Удаляет чат по ID
     */
    public void removeChatById(String chatId) {
        for (int i = 0; i < chatList.size(); i++) {
            if (chatList.get(i).getChatId().equals(chatId)) {
                chatList.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    /**
     * Обновляет счетчик непрочитанных для чата
     */
    public void updateUnreadCount(String chatId, int unreadCount) {
        if (chatId == null) return;

        for (int i = 0; i < chatList.size(); i++) {
            Chat chat = chatList.get(i);
            if (chat != null && chatId.equals(chat.getChatId())) {
                chat.setUnreadCount(unreadCount);
                notifyItemChanged(i);
                break;
            }
        }
    }

    /**
     * Очищает весь кэш
     */
    public void clearCache() {
        avatarCache.clear();
        userNameCache.clear();
        groupNameCache.clear();
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewUserName;
        private final TextView textViewLastMessage;
        private final TextView textViewTime;
        private final TextView textViewUnreadCount;
        private final ImageView imageViewAvatar;
        private final ImageView groupIcon;
        final View onlineIndicator;

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

            // Проверяем тип чата
            String chatType = chat.getChatType();
            String chatId = chat.getChatId();

            if ("group".equals(chatType)) {
                // Групповой чат
                if (groupIcon != null) {
                    groupIcon.setVisibility(View.VISIBLE);
                }
                // Используем кэш для имени группы
                String cachedName = groupNameCache.get(chatId);
                if (cachedName != null) {
                    textViewUserName.setText(cachedName);
                } else if (chat.getGroupName() != null && !chat.getGroupName().isEmpty()) {
                    textViewUserName.setText(chat.getGroupName());
                    groupNameCache.put(chatId, chat.getGroupName());
                } else if (chat.getGroupId() != null) {
                    loadGroupInfo(chat.getGroupId(), chatId);
                } else {
                    textViewUserName.setText("Группа");
                }

                // Загружаем аватарку группы
                loadGroupAvatar(chat.getGroupId(), chatId);
            } else {
                // Личный чат
                if (groupIcon != null) {
                    groupIcon.setVisibility(View.GONE);
                }

                String participantId = chat.getParticipantId();

                // Используем кэш для имени пользователя
                String cachedName = userNameCache.get(participantId);
                if (cachedName != null) {
                    textViewUserName.setText(cachedName);
                } else if (chat.getParticipantName() != null && !chat.getParticipantName().isEmpty()) {
                    textViewUserName.setText(chat.getParticipantName());
                    userNameCache.put(participantId, chat.getParticipantName());
                } else if (participantId != null) {
                    loadUserInfo(participantId);
                } else {
                    textViewUserName.setText("Пользователь");
                }

                // Загружаем аватарку пользователя
                String cachedAvatar = avatarCache.get(participantId);
                if (cachedAvatar != null && !cachedAvatar.isEmpty()) {
                    loadAvatarFromUrl(cachedAvatar);
                } else if (chat.getParticipantAvatar() != null && !chat.getParticipantAvatar().isEmpty()) {
                    loadAvatarFromUrl(chat.getParticipantAvatar());
                    avatarCache.put(participantId, chat.getParticipantAvatar());
                } else if (participantId != null) {
                    loadUserAvatar(participantId);
                } else {
                    setDefaultAvatar();
                }
            }
        }

        private void loadGroupInfo(String groupId, String chatId) {
            if (groupId == null || groupId.isEmpty()) {
                textViewUserName.setText("Группа");
                return;
            }

            groupsRef.child(groupId).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (itemView.getContext() == null) return;

                    String groupName = dataSnapshot.getValue(String.class);
                    if (groupName == null || groupName.isEmpty()) {
                        groupName = "Группа";
                    }
                    textViewUserName.setText(groupName);
                    groupNameCache.put(chatId, groupName);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    textViewUserName.setText("Группа");
                }
            });
        }

        private void loadGroupAvatar(String groupId, String chatId) {
            if (groupId == null || groupId.isEmpty()) {
                setDefaultAvatar();
                return;
            }

            // Проверяем кэш
            String cachedAvatar = avatarCache.get(groupId);
            if (cachedAvatar != null) {
                loadAvatarFromUrl(cachedAvatar);
                return;
            }

            groupsRef.child(groupId).child("avatarUrl").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (itemView.getContext() == null) return;

                    String avatarUrl = dataSnapshot.getValue(String.class);
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        loadAvatarFromUrl(avatarUrl);
                        avatarCache.put(groupId, avatarUrl);
                    } else {
                        setDefaultAvatar();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    setDefaultAvatar();
                }
            });
        }

        private void loadUserInfo(String userId) {
            if (userId == null || userId.isEmpty()) {
                textViewUserName.setText("Пользователь");
                return;
            }

            // Проверяем кэш
            String cachedName = userNameCache.get(userId);
            if (cachedName != null) {
                textViewUserName.setText(cachedName);
                return;
            }

            usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (itemView.getContext() == null) return;

                    String userName = null;
                    if (dataSnapshot.exists()) {
                        userName = dataSnapshot.child("username").getValue(String.class);
                        if (userName == null) {
                            userName = dataSnapshot.child("name").getValue(String.class);
                        }
                    }

                    if (userName == null || userName.isEmpty()) {
                        userName = "Пользователь";
                    }
                    textViewUserName.setText(userName);
                    userNameCache.put(userId, userName);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    textViewUserName.setText("Пользователь");
                }
            });
        }

        private void loadUserAvatar(String userId) {
            if (userId == null || userId.isEmpty()) {
                setDefaultAvatar();
                return;
            }

            // Проверяем кэш
            String cachedAvatar = avatarCache.get(userId);
            if (cachedAvatar != null) {
                loadAvatarFromUrl(cachedAvatar);
                return;
            }

            avatarsRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (itemView.getContext() == null) return;

                    String avatarUrl = dataSnapshot.getValue(String.class);
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        loadAvatarFromUrl(avatarUrl);
                        avatarCache.put(userId, avatarUrl);
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

        private void loadAvatarFromUrl(String avatarUrl) {
            if (avatarUrl == null || avatarUrl.isEmpty() || imageViewAvatar == null) {
                setDefaultAvatar();
                return;
            }

            // Проверяем, что Activity/View еще жива
            if (itemView.getContext() == null) {
                return;
            }

            Glide.with(itemView.getContext())
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(imageViewAvatar);
        }

        private void setDefaultAvatar() {
            if (imageViewAvatar != null) {
                imageViewAvatar.setImageResource(R.drawable.ic_person);
            }
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