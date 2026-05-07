package com.example.mytelegram.ui.home;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mytelegram.Chat;
import com.example.mytelegram.ChatActivity;
import com.example.mytelegram.ChatsAdapter;
import com.example.mytelegram.GroupChatActivity;
import com.example.mytelegram.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final int REQUEST_CODE_OPEN_CHAT = 1001;

    private DatabaseReference databaseReference;
    private ChatsAdapter adapter;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private String currentUserId;

    // Все чаты
    private final Map<String, Chat> loadedChats = new HashMap<>();
    // Кэш групп: chatId -> GroupInfo
    private final Map<String, GroupInfo> groupInfoMap = new HashMap<>();
    // Связь chatId -> groupId
    private final Map<String, String> chatIdToGroupId = new HashMap<>();

    private ValueEventListener chatsListener;
    private ValueEventListener groupsListener;

    // Для онлайн-статуса
    private final Map<String, ValueEventListener> onlineListeners = new HashMap<>();
    // Для слушателей сообщений в чатах
    private final Map<String, ValueEventListener> chatMessageListeners = new HashMap<>();

    // Временное хранение chatId для обновления после возврата из чата
    private String pendingRefreshChatId = null;

    // Флаг, что данные уже загружены
    private boolean dataLoaded = false;

    // Кэш уже загруженных пользователей
    private final Map<String, Boolean> userInfoLoaded = new HashMap<>();

    // Кэши для имен и аватарок
    private final Map<String, String> userNameCache = new HashMap<>();
    private final Map<String, String> userAvatarCache = new HashMap<>();

    // Флаг для предотвращения множественных обновлений
    private boolean isUpdating = false;

    private static class GroupInfo {
        String name;
        String avatarUrl;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        } else {
            currentUserId = "vLkUH1cFOrTt63pUHPXtNRfRhbu1";
        }
        databaseReference = FirebaseDatabase.getInstance().getReference();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chats_list, container, false);

        // ✅ Восстанавливаем ссылки на views
        recyclerView = view.findViewById(R.id.recyclerViewChats);
        progressBar = view.findViewById(R.id.progressBar);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recyclerViewChats);
        progressBar = view.findViewById(R.id.progressBar);

        // ✅ ВАЖНО: Пересоздаем адаптер
        if (adapter == null) {
            adapter = new ChatsAdapter(new ArrayList<>(), chat -> openChat(chat));
        }
        setupRecyclerView();
        setupSwipeToDelete();

        // Если данные уже загружены, просто обновляем адаптер
        if (dataLoaded && !loadedChats.isEmpty()) {
            updateAdapter();
            showLoading(false);
        } else if (!dataLoaded) {
            loadGroupsThenChats();
        }
    }

    // --- Swipe to delete ---
    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT) {

            private static final float SWIPE_THRESHOLD_FOR_ICON = 0.3f;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position >= 0 && position < adapter.getChats().size()) {
                    Chat chat = adapter.getChats().get(position);
                    showDeleteDialog(chat, position);
                }
                adapter.notifyItemChanged(position);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;
                if (dX < 0) {
                    float swipeProgress = Math.abs(dX) / itemView.getWidth();
                    Paint p = new Paint();
                    p.setColor(Color.RED);
                    c.drawRect(itemView.getRight() + dX, itemView.getTop(),
                            itemView.getRight(), itemView.getBottom(), p);

                    if (swipeProgress > SWIPE_THRESHOLD_FOR_ICON) {
                        Drawable deleteIcon = ContextCompat.getDrawable(requireContext(),
                                R.drawable.ic_delate);
                        if (deleteIcon != null) {
                            int iconSize = deleteIcon.getIntrinsicWidth();
                            int iconMargin = (itemView.getHeight() - iconSize) / 2;
                            float alphaProgress = (swipeProgress - SWIPE_THRESHOLD_FOR_ICON) /
                                    (1 - SWIPE_THRESHOLD_FOR_ICON);
                            int alpha = (int) (255 * Math.min(1, alphaProgress));
                            deleteIcon.setAlpha(alpha);

                            int iconRight = itemView.getRight() - iconMargin;
                            int iconLeft = iconRight - iconSize;
                            int iconTop = itemView.getTop() + (itemView.getHeight() - iconSize) / 2;
                            int iconBottom = iconTop + iconSize;
                            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            deleteIcon.draw(c);
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.7f;
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChatsAdapter(new ArrayList<>(), chat -> openChat(chat));
        recyclerView.setAdapter(adapter);
    }

    // --- Загрузка данных ---
    private void loadGroupsThenChats() {
        showLoading(true);
        groupsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (getActivity() == null || !isAdded()) return;

                boolean groupInfoChanged = false;

                // Проверяем, изменились ли данные групп
                for (DataSnapshot groupSnap : snapshot.getChildren()) {
                    String groupId = groupSnap.getKey();
                    String chatId = groupSnap.child("chatId").getValue(String.class);
                    String name = groupSnap.child("name").getValue(String.class);
                    String avatarUrl = groupSnap.child("avatarUrl").getValue(String.class);

                    if (!TextUtils.isEmpty(chatId)) {
                        GroupInfo existingInfo = groupInfoMap.get(chatId);
                        String newName = name != null ? name : "Группа";
                        String newAvatarUrl = avatarUrl != null ? avatarUrl : "";

                        if (existingInfo == null ||
                                !TextUtils.equals(existingInfo.name, newName) ||
                                !TextUtils.equals(existingInfo.avatarUrl, newAvatarUrl)) {
                            groupInfoChanged = true;
                        }

                        GroupInfo info = new GroupInfo();
                        info.name = newName;
                        info.avatarUrl = newAvatarUrl;
                        groupInfoMap.put(chatId, info);
                        chatIdToGroupId.put(chatId, groupId);
                    }
                }

                // ОБНОВЛЯЕМ ГРУППОВЫЕ ЧАТЫ ТОЛЬКО ЕСЛИ БЫЛИ ИЗМЕНЕНИЯ
                if (groupInfoChanged) {
                    refreshAllGroupChats();
                }

                if (chatsListener == null) {
                    loadChats();
                }
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Groups load cancelled: " + error.getMessage());
                if (chatsListener == null) {
                    loadChats();
                }
                showLoading(false);
            }
        };
        databaseReference.child("groups").addValueEventListener(groupsListener);
    }

    /**
     * Обновляет все групповые чаты актуальной информацией из groupInfoMap
     * ТОЛЬКО если данные реально изменились
     */
    private void refreshAllGroupChats() {
        if (isUpdating) return;
        isUpdating = true;

        try {
            for (Chat chat : loadedChats.values()) {
                if (chat.isGroupChat()) {
                    GroupInfo info = groupInfoMap.get(chat.getChatId());
                    if (info != null) {
                        boolean changed = false;

                        if (!TextUtils.equals(info.avatarUrl, chat.getParticipantAvatar())) {
                            chat.setParticipantAvatar(info.avatarUrl);
                            changed = true;
                        }

                        if (!TextUtils.equals(info.name, chat.getGroupName())) {
                            chat.setGroupName(info.name);
                            chat.setParticipantName(info.name);
                            changed = true;
                        }

                        if (changed) {
                            updateSingleChat(chat);
                        }
                    }
                }
            }
        } finally {
            isUpdating = false;
        }
    }

    private void loadChats() {
        if (chatsListener != null) {
            databaseReference.child("chats").removeEventListener(chatsListener);
        }

        chatsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (getActivity() == null || !isAdded()) return;

                // Сохраняем ID существующих чатов для отслеживания удаленных
                List<String> existingChatIds = new ArrayList<>(loadedChats.keySet());
                boolean hasNewChats = false;

                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    if (chatId == null) continue;

                    // Удаляем из списка существующих
                    existingChatIds.remove(chatId);

                    // Получаем старую версию чата (если есть)
                    Chat oldChat = loadedChats.get(chatId);
                    boolean isNewChat = (oldChat == null);

                    Chat chat;
                    if (isNewChat) {
                        chat = new Chat();
                        chat.setChatId(chatId);
                        hasNewChats = true;
                    } else {
                        chat = new Chat();
                        chat.setChatId(chatId);
                        chat.setChatType(oldChat.getChatType());
                        chat.setParticipantId(oldChat.getParticipantId());
                        chat.setParticipantName(oldChat.getParticipantName());
                        chat.setParticipantAvatar(oldChat.getParticipantAvatar());
                        chat.setGroupId(oldChat.getGroupId());
                        chat.setGroupName(oldChat.getGroupName());
                        chat.setLastMessage(oldChat.getLastMessage());
                        chat.setTimestamp(oldChat.getTimestamp());
                        chat.setUnreadCount(oldChat.getUnreadCount());
                        chat.setLastMessageSenderId(oldChat.getLastMessageSenderId());
                        chat.setLastMessageMine(oldChat.isLastMessageMine());
                        chat.setLastMessageTime(oldChat.getLastMessageTime());
                        chat.setMessageType(oldChat.getMessageType());
                        chat.setOnline(oldChat.isOnline());
                    }

                    if (isPersonalChat(chatId)) {
                        chat.setChatType("private");
                        String[] users = chatId.split("_");
                        String participantId = users[0].equals(currentUserId) ? users[1] : users[0];
                        chat.setParticipantId(participantId);

                        if (!userInfoLoaded.containsKey(participantId)) {
                            loadParticipantInfo(participantId, chat);
                        } else {
                            loadCachedUserInfo(participantId, chat);
                        }
                        startOnlineListener(participantId, chat);
                    } else {
                        chat.setChatType("group");
                        GroupInfo info = groupInfoMap.get(chatId);
                        if (info != null) {
                            chat.setGroupName(info.name);
                            chat.setParticipantName(info.name);
                            chat.setParticipantAvatar(info.avatarUrl);
                        }
                        String groupId = chatIdToGroupId.get(chatId);
                        chat.setGroupId(groupId != null ? groupId : "");
                    }

                    // Обновляем информацию о последнем сообщении
                    updateLastMessageInfo(chatSnapshot, chat);

                    if (chat.isGroupChat()) {
                        checkGroupMembership(chatId, chat);
                    } else if (isNewChat) {
                        loadedChats.put(chatId, chat);
                        startChatMessageListener(chatId);
                    } else {
                        if (hasChatChanged(oldChat, chat)) {
                            loadedChats.put(chatId, chat);
                            updateSingleChat(chat);
                        }
                    }
                }

                // Удаляем чаты, которых больше нет в Firebase
                for (String removedChatId : existingChatIds) {
                    loadedChats.remove(removedChatId);
                    stopChatMessageListener(removedChatId);
                    if (adapter != null) {
                        adapter.removeChatById(removedChatId);
                    }
                }

                dataLoaded = true;

                // Обновляем адаптер только при реальных изменениях
                if (!existingChatIds.isEmpty() || hasNewChats || loaderNeedsFullUpdate()) {
                    updateAdapter();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                if (getActivity() == null || !isAdded()) return;
                showLoading(false);
                Log.e(TAG, "Error loading chats: " + databaseError.getMessage());
                Toast.makeText(getContext(), "Ошибка загрузки чатов", Toast.LENGTH_SHORT).show();
            }
        };
        databaseReference.child("chats").addValueEventListener(chatsListener);
    }

    private void loadCachedUserInfo(String participantId, Chat chat) {
        final String chatId = chat.getChatId();

        if (userNameCache.containsKey(participantId)) {
            Chat existingChat = loadedChats.get(chatId);
            if (existingChat != null) {
                boolean changed = false;

                String cachedName = userNameCache.get(participantId);
                if (!cachedName.equals(existingChat.getParticipantName())) {
                    existingChat.setParticipantName(cachedName);
                    changed = true;
                }

                if (userAvatarCache.containsKey(participantId)) {
                    String cachedAvatar = userAvatarCache.get(participantId);
                    if (!cachedAvatar.equals(existingChat.getParticipantAvatar())) {
                        existingChat.setParticipantAvatar(cachedAvatar);
                        changed = true;
                    }
                }

                if (changed) {
                    updateSingleChat(existingChat);
                }
            }
            return;
        }

        databaseReference.child("users").child(participantId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (getActivity() == null || !isAdded()) return;

                        Chat existingChat = loadedChats.get(chatId);
                        if (existingChat == null) return;

                        boolean changed = false;

                        if (dataSnapshot.exists()) {
                            String username = dataSnapshot.child("username").getValue(String.class);
                            if (username == null) username = dataSnapshot.child("name").getValue(String.class);
                            String avatarUrl = dataSnapshot.child("avatarUrl").getValue(String.class);

                            if (username != null && !username.equals(existingChat.getParticipantName())) {
                                existingChat.setParticipantName(username);
                                userNameCache.put(participantId, username);
                                changed = true;
                            }
                            if (avatarUrl != null && !avatarUrl.equals(existingChat.getParticipantAvatar())) {
                                existingChat.setParticipantAvatar(avatarUrl);
                                userAvatarCache.put(participantId, avatarUrl);
                                changed = true;
                            }
                        } else if (!existingChat.getParticipantName().equals("Пользователь")) {
                            existingChat.setParticipantName("Пользователь");
                            existingChat.setParticipantAvatar("");
                            userNameCache.put(participantId, "Пользователь");
                            changed = true;
                        }

                        userInfoLoaded.put(participantId, true);

                        if (changed) {
                            updateSingleChat(existingChat);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {}
                });
    }

    private boolean hasChatChanged(Chat oldChat, Chat newChat) {
        if (oldChat == null || newChat == null) return true;

        return !TextUtils.equals(oldChat.getLastMessage(), newChat.getLastMessage())
                || oldChat.getTimestamp() != newChat.getTimestamp()
                || oldChat.getUnreadCount() != newChat.getUnreadCount()
                || !TextUtils.equals(oldChat.getParticipantName(), newChat.getParticipantName())
                || !TextUtils.equals(oldChat.getParticipantAvatar(), newChat.getParticipantAvatar())
                || oldChat.isOnline() != newChat.isOnline();
    }

    private void startChatMessageListener(String chatId) {
        if (chatMessageListeners.containsKey(chatId)) return;

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (getActivity() == null || !isAdded()) return;
                if (isUpdating) return;

                Chat chat = loadedChats.get(chatId);
                if (chat != null) {
                    if (snapshot.getRef() != null && snapshot.getRef().getParent() != null) {
                        snapshot.getRef().getParent().addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot chatSnapshot) {
                                if (getActivity() == null || !isAdded()) return;
                                updateLastMessageInfo(chatSnapshot, chat);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
                    } else {
                        updateLastMessageInfo(snapshot, chat);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        chatMessageListeners.put(chatId, listener);
        databaseReference.child("chats").child(chatId).child("messages")
                .addValueEventListener(listener);
    }

    private void stopChatMessageListener(String chatId) {
        ValueEventListener listener = chatMessageListeners.remove(chatId);
        if (listener != null) {
            databaseReference.child("chats").child(chatId).child("messages")
                    .removeEventListener(listener);
        }
    }

    private boolean loaderNeedsFullUpdate() {
        return adapter == null || adapter.getItemCount() == 0;
    }

    private void checkGroupMembership(String chatId, Chat chat) {
        String groupId = chat.getGroupId();
        if (TextUtils.isEmpty(groupId)) {
            groupId = chatIdToGroupId.get(chatId);
        }

        if (TextUtils.isEmpty(groupId)) {
            if (!loadedChats.containsKey(chatId)) {
                addToLoadedChats(chatId, chat);
                startChatMessageListener(chatId);
            }
            return;
        }

        String finalGroupId = groupId;
        databaseReference.child("groups").child(groupId).child("members").child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (getActivity() == null || !isAdded()) return;

                        Boolean isMember = snapshot.getValue(Boolean.class);
                        if (isMember != null && isMember) {
                            if (!loadedChats.containsKey(chatId)) {
                                addToLoadedChats(chatId, chat);
                                startChatMessageListener(chatId);
                            }
                        } else {
                            Log.d(TAG, "User removed from group: " + finalGroupId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (getActivity() == null || !isAdded()) return;
                        if (!loadedChats.containsKey(chatId)) {
                            addToLoadedChats(chatId, chat);
                            startChatMessageListener(chatId);
                        }
                    }
                });
    }

    private void addToLoadedChats(String chatId, Chat chat) {
        loadedChats.put(chatId, chat);
        updateAdapter();
    }

    private boolean isPersonalChat(String chatId) {
        if (TextUtils.isEmpty(chatId)) return false;
        String[] parts = chatId.split("_");
        return parts.length == 2 && (parts[0].equals(currentUserId) || parts[1].equals(currentUserId));
    }

    private void loadParticipantInfo(String participantId, Chat chat) {
        final String chatId = chat.getChatId();

        if (userInfoLoaded.containsKey(participantId)) {
            loadCachedUserInfo(participantId, chat);
            return;
        }

        databaseReference.child("users").child(participantId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (getActivity() == null || !isAdded()) return;

                        Chat existingChat = loadedChats.get(chatId);
                        if (existingChat == null) return;

                        boolean changed = false;

                        if (dataSnapshot.exists()) {
                            String username = dataSnapshot.child("username").getValue(String.class);
                            if (username == null) username = dataSnapshot.child("name").getValue(String.class);
                            String avatarUrl = dataSnapshot.child("avatarUrl").getValue(String.class);

                            if (username != null && !username.equals(existingChat.getParticipantName())) {
                                existingChat.setParticipantName(username);
                                userNameCache.put(participantId, username);
                                changed = true;
                            }
                            if (avatarUrl != null && !avatarUrl.equals(existingChat.getParticipantAvatar())) {
                                existingChat.setParticipantAvatar(avatarUrl);
                                userAvatarCache.put(participantId, avatarUrl);
                                changed = true;
                            }
                        } else if (!existingChat.getParticipantName().equals("Пользователь")) {
                            existingChat.setParticipantName("Пользователь");
                            existingChat.setParticipantAvatar("");
                            userNameCache.put(participantId, "Пользователь");
                            changed = true;
                        }

                        userInfoLoaded.put(participantId, true);

                        if (changed) {
                            updateSingleChat(existingChat);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {}
                });
    }

    private void startOnlineListener(String userId, Chat chat) {
        if (TextUtils.isEmpty(userId)) return;

        final String chatId = chat.getChatId();

        ValueEventListener old = onlineListeners.remove(userId);
        if (old != null) {
            databaseReference.child("users").child(userId).child("online")
                    .removeEventListener(old);
        }

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (getActivity() == null || !isAdded()) return;

                Boolean online = snapshot.getValue(Boolean.class);
                boolean newStatus = online != null && online;

                Chat existingChat = loadedChats.get(chatId);
                if (existingChat != null && existingChat.isOnline() != newStatus) {
                    existingChat.setOnline(newStatus);
                    updateSingleChat(existingChat);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        onlineListeners.put(userId, listener);
        databaseReference.child("users").child(userId).child("online")
                .addValueEventListener(listener);
    }

    private void updateSingleChat(Chat updatedChat) {
        if (adapter != null && getActivity() != null && !isUpdating) {
            getActivity().runOnUiThread(() -> {
                List<Chat> currentChats = adapter.getChats();
                int position = -1;
                for (int i = 0; i < currentChats.size(); i++) {
                    if (currentChats.get(i).getChatId().equals(updatedChat.getChatId())) {
                        position = i;
                        break;
                    }
                }

                if (position != -1) {
                    adapter.updateChatAtPosition(updatedChat, position);
                }
            });
        }
    }

    private void updateLastMessageInfo(DataSnapshot chatSnapshot, Chat chat) {
        String lastMessage = "Нет сообщений";
        long lastTimestamp = 0;
        String lastSenderId = null;
        String lastMessageType = "text";
        int unreadCount = 0;

        DataSnapshot messagesNode = chatSnapshot.child("messages");
        if (messagesNode.exists()) {
            List<Map<String, Object>> allMessages = new ArrayList<>();
            for (DataSnapshot msgSnap : messagesNode.getChildren()) {
                Map<String, Object> data = getMessageDataSafely(msgSnap);
                if (data != null) {
                    allMessages.add(data);
                }
            }

            allMessages.sort((m1, m2) -> {
                Long ts1 = safeCastToLong(m1.get("timestamp"));
                Long ts2 = safeCastToLong(m2.get("timestamp"));
                if (ts1 == null) return 1;
                if (ts2 == null) return -1;
                return ts1.compareTo(ts2);
            });

            for (Map<String, Object> data : allMessages) {
                String messageText = safeCastToString(data.get("text"));
                Long ts = safeCastToLong(data.get("timestamp"));
                String senderId = safeCastToString(data.get("senderId"));
                String msgType = safeCastToString(data.get("messageType"));

                long timestamp = ts != null ? ts : 0;
                if (timestamp > lastTimestamp) {
                    lastTimestamp = timestamp;
                    lastMessage = getMessageDisplayText(messageText, msgType);
                    lastSenderId = senderId;
                    lastMessageType = msgType != null ? msgType : "text";
                }

                if (senderId != null && !senderId.equals(currentUserId)) {
                    if (!checkMessageReadStatus(data, currentUserId)) {
                        unreadCount++;
                    }
                }
            }
        }

        boolean changed = false;

        if (!TextUtils.equals(chat.getLastMessage(), lastMessage)) {
            chat.setLastMessage(lastMessage);
            changed = true;
        }
        if (chat.getTimestamp() != lastTimestamp) {
            chat.setTimestamp(lastTimestamp > 0 ? lastTimestamp : System.currentTimeMillis());
            changed = true;
        }
        if (chat.getUnreadCount() != unreadCount) {
            chat.setUnreadCount(unreadCount);
            changed = true;
        }
        if (!TextUtils.equals(chat.getLastMessageSenderId(), lastSenderId)) {
            chat.setLastMessageSenderId(lastSenderId);
            changed = true;
        }

        boolean isMine = TextUtils.equals(currentUserId, lastSenderId);
        if (chat.isLastMessageMine() != isMine) {
            chat.setLastMessageMine(isMine);
            changed = true;
        }

        String newTime = formatTimestamp(lastTimestamp);
        if (!TextUtils.equals(chat.getLastMessageTime(), newTime)) {
            chat.setLastMessageTime(newTime);
            changed = true;
        }

        if (!TextUtils.equals(chat.getMessageType(), lastMessageType)) {
            chat.setMessageType(lastMessageType);
            changed = true;
        }

        if (changed) {
            updateSingleChat(chat);
        }
    }

    private Map<String, Object> getMessageDataSafely(DataSnapshot snapshot) {
        try {
            Object value = snapshot.getValue();
            return (value instanceof Map) ? (Map<String, Object>) value : null;
        } catch (ClassCastException e) {
            return null;
        }
    }

    private String safeCastToString(Object obj) {
        return obj instanceof String ? (String) obj : null;
    }

    private Long safeCastToLong(Object obj) {
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        return null;
    }

    private boolean checkMessageReadStatus(Map<String, Object> msgData, String userId) {
        Object readBy = msgData.get("readBy");
        if (readBy instanceof Map) {
            Object status = ((Map) readBy).get(userId);
            return status instanceof Boolean && (Boolean) status;
        }
        Object isRead = msgData.get("isRead");
        return isRead instanceof Boolean && (Boolean) isRead;
    }

    private void showDeleteDialog(Chat chat, int position) {
        String name = chat.isGroupChat() ? chat.getGroupName() : chat.getParticipantName();
        String message = chat.isGroupChat()
                ? "Вы уверены, что хотите скрыть групповой чат \"" + name + "\"? (общие данные не удалятся)"
                : "Вы уверены, что хотите удалить чат с " + name + "?";

        new AlertDialog.Builder(requireContext())
                .setTitle("Удалить чат")
                .setMessage(message)
                .setPositiveButton("Удалить", (dialog, which) -> deleteChat(chat, position))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deleteChat(Chat chat, int position) {
        String chatId = chat.getChatId();
        loadedChats.remove(chatId);
        stopChatMessageListener(chatId);

        if (adapter != null) {
            adapter.removeChatById(chatId);
        }

        if (!chat.isGroupChat()) {
            databaseReference.child("chats").child(chatId).removeValue()
                    .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Чат удалён", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "deleteChat error: " + e.getMessage());
                    });
        } else {
            Toast.makeText(getContext(), "Групповой чат скрыт из списка", Toast.LENGTH_SHORT).show();
        }
    }

    private void openChat(Chat chat) {
        Intent intent;
        if (chat.isGroupChat()) {
            intent = new Intent(getActivity(), GroupChatActivity.class);
            intent.putExtra("chatId", chat.getChatId());
            intent.putExtra("groupId", chat.getGroupId());
            intent.putExtra("groupName", chat.getGroupName());
        } else {
            intent = new Intent(getActivity(), ChatActivity.class);
            intent.putExtra("chatId", chat.getChatId());
            intent.putExtra("recipientId", chat.getParticipantId());
            intent.putExtra("recipientName", chat.getParticipantName());
            intent.putExtra("recipientAvatar", chat.getParticipantAvatar());
        }
        pendingRefreshChatId = chat.getChatId();
        startActivityForResult(intent, REQUEST_CODE_OPEN_CHAT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_CHAT && !TextUtils.isEmpty(pendingRefreshChatId)) {
            Chat chat = loadedChats.get(pendingRefreshChatId);
            if (chat != null) {
                chat.setUnreadCount(0);
                updateSingleChat(chat);
            }
            pendingRefreshChatId = null;
        }
    }

    private String getMessageDisplayText(String messageText, String messageType) {
        if (TextUtils.isEmpty(messageType)) {
            return !TextUtils.isEmpty(messageText) ? messageText : "Сообщение";
        }
        switch (messageType) {
            case "image": return "📷 Фото";
            case "video": return "🎬 Видео";
            case "audio": return "🎵 Аудио";
            case "document": return "📄 Документ";
            case "sticker": return "😀 Стикер";
            case "location": return "📍 Местоположение";
            case "voice": return "🎤 Голосовое сообщение";
            default: return !TextUtils.isEmpty(messageText) ? messageText : "Сообщение";
        }
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp == 0) return "";
        Date date = new Date(timestamp);
        Calendar msgCal = Calendar.getInstance();
        msgCal.setTime(date);
        Calendar nowCal = Calendar.getInstance();

        if (msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
                && msgCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        }

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (msgCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)
                && msgCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)) {
            return "вчера";
        }

        if (msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
            return new SimpleDateFormat("d MMM", Locale.getDefault()).format(date);
        }
        return new SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(date);
    }

    private void updateAdapter() {
        if (!isAdded() || getActivity() == null) return;

        List<Chat> chatList = new ArrayList<>(loadedChats.values());
        Collections.sort(chatList, (c1, c2) -> Long.compare(c2.getTimestamp(), c1.getTimestamp()));

        getActivity().runOnUiThread(() -> {
            if (adapter != null) {
                adapter.setChats(chatList);
            }
        });
    }

    private void showLoading(boolean show) {
        if (getActivity() == null || getActivity().isFinishing()) return;
        getActivity().runOnUiThread(() -> {
            if (progressBar != null && recyclerView != null) {
                progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "onResume - dataLoaded=" + dataLoaded +
                ", adapter=" + (adapter != null) +
                ", loadedChats size=" + loadedChats.size());

        if (dataLoaded && adapter != null) {
            updateAdapter();

            for (Chat chat : loadedChats.values()) {
                if (chat.getUnreadCount() > 0) {
                    updateSingleChat(chat);
                }
            }
        } else if (!dataLoaded) {
            // Если данные не загружены - загружаем
            loadGroupsThenChats();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        Log.d(TAG, "onDestroyView - cleaning up listeners, keeping data");

        // Удаляем слушатели чатов
        if (chatsListener != null) {
            databaseReference.child("chats").removeEventListener(chatsListener);
            chatsListener = null;
        }

        // Удаляем слушатели групп
        if (groupsListener != null) {
            databaseReference.child("groups").removeEventListener(groupsListener);
            groupsListener = null;
        }

        // Удаляем слушатели онлайн-статуса
        for (Map.Entry<String, ValueEventListener> entry : onlineListeners.entrySet()) {
            databaseReference.child("users").child(entry.getKey()).child("online")
                    .removeEventListener(entry.getValue());
        }
        onlineListeners.clear();

        // Удаляем слушатели сообщений
        for (Map.Entry<String, ValueEventListener> entry : chatMessageListeners.entrySet()) {
            databaseReference.child("chats").child(entry.getKey()).child("messages")
                    .removeEventListener(entry.getValue());
        }
        chatMessageListeners.clear();
      
        userInfoLoaded.clear();
        userNameCache.clear();
        userAvatarCache.clear();

        // ✅ НЕ ОЧИЩАЕМ ДАННЫЕ!
        // loadedChats остается
        // userInfoLoaded остается
        // userNameCache остается
        // userAvatarCache остается
        // dataLoaded НЕ МЕНЯЕМ!

        // Обнуляем views
        recyclerView = null;
        progressBar = null;
        adapter = null;
    }
}