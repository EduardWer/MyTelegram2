package com.example.mytelegram.ui.home;

import android.app.AlertDialog;
import android.content.DialogInterface;
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


    // В начало класса HomeFragment
    private final Map<String, ValueEventListener> onlineListeners = new HashMap<>();
    private DatabaseReference databaseReference;
    private ChatsAdapter adapter;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private String currentUserId;

    // Единое хранилище всех чатов (ключ = chatId)
    private final Map<String, Chat> loadedChats = new HashMap<>();
    // Кэш информации о группах (groupId -> GroupInfo)
    private final Map<String, GroupInfo> groupInfoMap = new HashMap<>();
    // Связь chatId -> groupId для групповых чатов
    private final Map<String, String> chatIdToGroupId = new HashMap<>();

    private ValueEventListener chatsListener;
    private ValueEventListener groupsListener;
    private String pendingRefreshChatId = null;

    // Простая структура для хранения названия и аватара группы
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
        return inflater.inflate(R.layout.fragment_chats_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerViewChats);
        progressBar = view.findViewById(R.id.progressBar);

        setupRecyclerView();
        setupSwipeToDelete();

        loadGroupsThenChats();
    }

    // ==================== Swipe to delete ====================
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

    // ==================== RecyclerView ====================
    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChatsAdapter(new ArrayList<>(), chat -> openChat(chat));
        recyclerView.setAdapter(adapter);
    }

    // ==================== Загрузка групп и чатов ====================
    private void loadGroupsThenChats() {
        showLoading(true);

        // Загружаем все группы один раз (и подписываемся на изменения)
        groupsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                groupInfoMap.clear();
                chatIdToGroupId.clear();
                for (DataSnapshot groupSnap : snapshot.getChildren()) {
                    String groupId = groupSnap.getKey();
                    String chatId = groupSnap.child("chatId").getValue(String.class);
                    String name = groupSnap.child("name").getValue(String.class);
                    String avatarUrl = groupSnap.child("avatarUrl").getValue(String.class);

                    if (!TextUtils.isEmpty(chatId)) {
                        GroupInfo info = new GroupInfo();
                        info.name = name != null ? name : "Группа";
                        info.avatarUrl = avatarUrl != null ? avatarUrl : "";
                        groupInfoMap.put(chatId, info);
                        chatIdToGroupId.put(chatId, groupId);
                    }
                }
                // После загрузки групп запускаем слушатель чатов
                loadChats();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Groups load cancelled: " + error.getMessage());
                loadChats(); // пробуем загрузить чаты даже без групп
            }
        };
        databaseReference.child("groups").addValueEventListener(groupsListener);
    }

    private void loadChats() {
        if (chatsListener != null) {
            databaseReference.child("chats").removeEventListener(chatsListener);
        }

        chatsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                loadedChats.clear();

                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    if (chatId == null) continue;

                    Chat chat = new Chat();
                    chat.setChatId(chatId);

                    if (isPersonalChat(chatId)) {
                        // Личный чат
                        chat.setChatType("private");
                        String[] users = chatId.split("_");
                        String participantId = users[0].equals(currentUserId) ? users[1] : users[0];
                        chat.setParticipantId(participantId);
                        loadParticipantInfo(participantId, chat);
                    } else {
                        // Групповой чат
                        chat.setChatType("group");
                        GroupInfo info = groupInfoMap.get(chatId);
                        if (info != null) {
                            chat.setGroupName(info.name);
                            chat.setParticipantName(info.name); // для отображения
                            chat.setParticipantAvatar(info.avatarUrl);
                        } else {
                            chat.setGroupName("Группа");
                            chat.setParticipantName("Группа");
                            chat.setParticipantAvatar("");
                        }
                        String groupId = chatIdToGroupId.get(chatId);
                        chat.setGroupId(groupId != null ? groupId : "");
                    }

                    // Загружаем последнее сообщение и счётчик непрочитанных
                    loadLastMessageInfo(chatSnapshot, chat);

                    loadedChats.put(chatId, chat);
                }

                updateAdapter();
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                showLoading(false);
                Log.e(TAG, "Error loading chats: " + databaseError.getMessage());
                Toast.makeText(getContext(), "Ошибка загрузки чатов", Toast.LENGTH_SHORT).show();
            }
        };
        databaseReference.child("chats").addValueEventListener(chatsListener);
    }

    private boolean isPersonalChat(String chatId) {
        if (TextUtils.isEmpty(chatId)) return false;
        String[] parts = chatId.split("_");
        return parts.length == 2 && (parts[0].equals(currentUserId) || parts[1].equals(currentUserId));
    }

    private void loadParticipantInfo(String participantId, Chat chat) {
        databaseReference.child("users").child(participantId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            String username = dataSnapshot.child("username").getValue(String.class);
                            if (username == null) username = dataSnapshot.child("name").getValue(String.class);
                            chat.setParticipantName(username != null ? username : "Пользователь");

                            String avatarUrl = dataSnapshot.child("avatarUrl").getValue(String.class);
                            chat.setParticipantAvatar(avatarUrl != null ? avatarUrl : "");
                        } else {
                            chat.setParticipantName("Пользователь");
                            chat.setParticipantAvatar("");
                        }
                        updateAdapter();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        chat.setParticipantName("Пользователь");
                        chat.setParticipantAvatar("");
                        updateAdapter();
                    }
                });
    }

    // ==================== Последнее сообщение и непрочитанные ====================
    private void loadLastMessageInfo(DataSnapshot chatSnapshot, Chat chat) {
        String lastMessage = "Нет сообщений";
        long lastTimestamp = 0;
        String lastSenderId = null;
        String lastMessageType = "text";
        int unreadCount = 0;

        DataSnapshot messagesNode = chatSnapshot.child("messages");
        if (messagesNode.exists()) {
            for (DataSnapshot msgSnap : messagesNode.getChildren()) {
                Map<String, Object> data = getMessageDataSafely(msgSnap);
                if (data == null) continue;

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

                // Считаем непрочитанные (только чужие сообщения, не прочитанные мной)
                if (senderId != null && !senderId.equals(currentUserId)) {
                    if (!checkMessageReadStatus(data, currentUserId)) {
                        unreadCount++;
                    }
                }
            }
        }

        chat.setLastMessage(lastMessage);
        chat.setTimestamp(lastTimestamp > 0 ? lastTimestamp : System.currentTimeMillis());
        chat.setUnreadCount(unreadCount);
        chat.setLastMessageSenderId(lastSenderId);
        chat.setLastMessageMine(TextUtils.equals(currentUserId, lastSenderId));
        chat.setLastMessageTime(formatTimestamp(lastTimestamp));
        if (chat.getMessageType() == null) {
            chat.setMessageType(lastMessageType);
        }
    }

    private Map<String, Object> getMessageDataSafely(DataSnapshot snapshot) {
        try {
            Object value = snapshot.getValue();
            return (value instanceof Map) ? (Map<String, Object>) value : null;
        } catch (ClassCastException e) {
            Log.e(TAG, "Message cast error: " + snapshot.getKey(), e);
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

    // ==================== Удаление ====================
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

        // Удаляем из локального списка сразу
        loadedChats.remove(chatId);
        List<Chat> currentChats = adapter.getChats();
        if (position < currentChats.size()) {
            currentChats.remove(position);
            adapter.setChats(currentChats);
        }

        // Личный чат – удаляем полностью из Firebase
        if (!chat.isGroupChat()) {
            databaseReference.child("chats").child(chatId).removeValue()
                    .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Чат удалён", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "deleteChat error: " + e.getMessage());
                    });
        } else {
            // Групповой чат – только убираем из интерфейса (можно добавить удаление из userChats, если нужно)
            Toast.makeText(getContext(), "Групповой чат скрыт из списка", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== Открытие чата и сброс счётчика ====================
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
                // МГНОВЕННЫЙ сброс счётчика (без задержки)
                chat.setUnreadCount(0);
                updateAdapter();
            }
            pendingRefreshChatId = null;
        }
    }

    // ==================== Форматирование ====================
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
        if (msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                msgCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        }

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (msgCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                msgCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)) {
            return "вчера";
        }

        if (msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
            return new SimpleDateFormat("d MMM", Locale.getDefault()).format(date);
        }
        return new SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(date);
    }

    private void updateAdapter() {
        List<Chat> chatList = new ArrayList<>(loadedChats.values());
        Collections.sort(chatList, (c1, c2) -> Long.compare(c2.getTimestamp(), c1.getTimestamp()));
        adapter.setChats(chatList);
    }

    private void showLoading(boolean show) {
        if (getActivity() == null || getActivity().isFinishing()) return;
        getActivity().runOnUiThread(() -> {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (chatsListener != null) {
            databaseReference.child("chats").removeEventListener(chatsListener);
        }
        if (groupsListener != null) {
            databaseReference.child("groups").removeEventListener(groupsListener);
        }
    }
}