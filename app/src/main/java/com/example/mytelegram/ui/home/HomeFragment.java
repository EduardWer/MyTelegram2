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

    private DatabaseReference databaseReference;
    private ChatsAdapter adapter;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private String currentUserId;

    private final Map<String, Chat> loadedChats = new HashMap<>();
    private ValueEventListener chatsListener;

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




        loadUserChats();
    }
    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

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
                adapter.notifyItemChanged(position); // Возвращаем элемент на место
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {

                View itemView = viewHolder.itemView;
                Paint p = new Paint();

                if (dX > 0) {
                    // Свайп вправо - удаление (красный фон)
                    p.setColor(Color.RED);
                    c.drawRect(itemView.getLeft(), itemView.getTop(), dX,
                            itemView.getBottom(), p);

                    // Иконка корзины
                    Drawable deleteIcon = ContextCompat.getDrawable(requireContext(),
                            R.drawable.ic_delate);
                    if (deleteIcon != null) {
                        int iconMargin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                        int iconTop = itemView.getTop() + iconMargin;
                        int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
                        int iconLeft = itemView.getLeft() + iconMargin;
                        int iconRight = iconLeft + deleteIcon.getIntrinsicWidth();
                        deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                        deleteIcon.draw(c);
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
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

    private void loadUserChats() {
        showLoading(true);

        DatabaseReference chatsRef = databaseReference.child("chats");

        chatsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<Chat> newChats = new ArrayList<>();

                for (DataSnapshot chatSnapshot : dataSnapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();

                    if (isUserChat(chatId)) {
                        Chat chat = loadedChats.get(chatId);
                        if (chat == null) {
                            chat = new Chat();
                            chat.setChatId(chatId);

                            String[] users = chatId.split("_");
                            String participantId = users[0].equals(currentUserId) ? users[1] : users[0];
                            chat.setParticipantId(participantId);

                            loadParticipantInfo(participantId, chat);
                            loadedChats.put(chatId, chat);
                        }

                        loadLastMessageInfo(chatSnapshot, chat);
                        newChats.add(chat);
                    }
                }

                // Сортируем по времени последнего сообщения
                Collections.sort(newChats, (c1, c2) -> Long.compare(c2.getTimestamp(), c1.getTimestamp()));

                adapter.setChats(newChats);
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                showLoading(false);
                Log.e(TAG, "Error loading chats: " + databaseError.getMessage());
                Toast.makeText(getContext(), "Ошибка загрузки чатов", Toast.LENGTH_SHORT).show();
            }
        };

        chatsRef.addValueEventListener(chatsListener);
    }

    private boolean isUserChat(String chatId) {
        if (TextUtils.isEmpty(chatId) || TextUtils.isEmpty(currentUserId)) {
            return false;
        }
        String[] users = chatId.split("_");
        return users.length == 2 && (users[0].equals(currentUserId) || users[1].equals(currentUserId));
    }

    private void loadParticipantInfo(String participantId, Chat chat) {
        DatabaseReference userRef = databaseReference.child("users").child(participantId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String username = dataSnapshot.child("username").getValue(String.class);
                    String avatarUrl = dataSnapshot.child("avatarUrl").getValue(String.class);

                    chat.setParticipantName(username != null ? username : "Пользователь");
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

    private void loadLastMessageInfo(DataSnapshot chatSnapshot, Chat chat) {
        String lastMessage = "Нет сообщений";
        long lastTimestamp = 0;
        String lastSenderId = null;
        int unreadCount = 0;

        DataSnapshot messagesNode = chatSnapshot.child("messages");

        if (messagesNode.exists()) {
            for (DataSnapshot messageSnapshot : messagesNode.getChildren()) {
                // Безопасный способ получить данные
                Map<String, Object> messageData = getMessageDataSafely(messageSnapshot);
                if (messageData == null) continue;

                // Извлекаем данные с проверкой типов
                String messageText = safeCastToString(messageData.get("text"));
                Long timestampObj = safeCastToLong(messageData.get("timestamp"));
                String senderId = safeCastToString(messageData.get("senderId"));
                String messageType = safeCastToString(messageData.get("messageType"));

                long timestamp = timestampObj != null ? timestampObj : 0;

                // Находим последнее сообщение
                if (timestamp > lastTimestamp) {
                    lastTimestamp = timestamp;
                    lastMessage = getMessageDisplayText(messageText, messageType);
                    lastSenderId = senderId;
                }

                // Подсчет непрочитанных
                if (senderId != null && !senderId.equals(currentUserId)) {
                    boolean isReadByMe = checkMessageReadStatus(messageData, currentUserId);
                    if (!isReadByMe) {
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

        updateAdapter();
    }

    private Map<String, Object> getMessageDataSafely(DataSnapshot snapshot) {
        try {
            Object value = snapshot.getValue();
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
            return null;
        } catch (ClassCastException e) {
            Log.e(TAG, "Не удалось преобразовать в Map: " + snapshot.getKey(), e);
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

    private boolean checkMessageReadStatus(Map<String, Object> messageData, String userId) {
        // Проверяем readBy
        Object readBy = messageData.get("readBy");
        if (readBy instanceof Map) {
            Object status = ((Map) readBy).get(userId);
            return status instanceof Boolean && (Boolean) status;
        }

        // Проверяем isRead
        Object isRead = messageData.get("isRead");
        return isRead instanceof Boolean && (Boolean) isRead;
    }


    private void showDeleteDialog(Chat chat, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Удалить чат")
                .setMessage("Вы уверены, что хотите удалить чат с " + chat.getParticipantName() + "?")
                .setPositiveButton("Удалить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteChat(chat, position);
                    }
                })
                .setNegativeButton("Отмена", null)

                .show();
    }

    // Метод удаления чата
    private void deleteChat(Chat chat, int position) {
        String chatId = chat.getChatId();

        // 1. Удаляем из локального списка
        loadedChats.remove(chatId);
        List<Chat> currentChats = adapter.getChats();
        if (position < currentChats.size()) {
            currentChats.remove(position);
            adapter.setChats(currentChats);
        }

        // 2. Удаляем из Firebase
        databaseReference.child("chats").child(chatId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Чат удален", Toast.LENGTH_SHORT).show();

                    // Также можно удалить сообщения
                    deleteChatMessages(chatId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Ошибка удаления чата", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error deleting chat: " + e.getMessage());
                });
    }

    private void deleteChatMessages(String chatId) {
        databaseReference.child("chats").child(chatId).child("messages").removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Messages deleted for chat: " + chatId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting messages: " + e.getMessage());
                });
    }

    private boolean isMessageReadByMe(Map<String, Object> messageData, String myId) {
        // ВАРИАНТ 1: Проверяем поле readBy
        if (messageData.containsKey("readBy")) {
            Map<String, Object> readBy = (Map<String, Object>) messageData.get("readBy");
            if (readBy != null && readBy.containsKey(myId)) {
                Object readStatus = readBy.get(myId);
                return readStatus instanceof Boolean && (Boolean) readStatus;
            }
            return false;
        }

        // ВАРИАНТ 2: Проверяем поле isRead (старая структура)
        if (messageData.containsKey("isRead")) {
            Object isRead = messageData.get("isRead");
            if (isRead instanceof Boolean) {
                // Если isRead = true, значит сообщение прочитано всеми
                // Если isRead = false, значит непрочитано никем
                return (Boolean) isRead;
            }
        }

        // По умолчанию считаем непрочитанным
        return false;
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
        if (timestamp == 0) {
            return "";
        }

        Date date = new Date(timestamp);
        Date now = new Date();

        Calendar messageCal = Calendar.getInstance();
        messageCal.setTime(date);

        Calendar todayCal = Calendar.getInstance();
        todayCal.setTime(now);

        SimpleDateFormat sdf;

        // Сегодня
        if (messageCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                messageCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)) {
            sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return sdf.format(date);
        }

        // Вчера
        Calendar yesterdayCal = Calendar.getInstance();
        yesterdayCal.add(Calendar.DAY_OF_YEAR, -1);
        if (messageCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                messageCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)) {
            return "вчера";
        }

        // В этом году
        if (messageCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)) {
            sdf = new SimpleDateFormat("d MMM", Locale.getDefault());
            return sdf.format(date);
        }

        // В прошлом году
        sdf = new SimpleDateFormat("dd.MM.yy", Locale.getDefault());
        return sdf.format(date);
    }

    private void updateAdapter() {
        List<Chat> chatList = new ArrayList<>(loadedChats.values());
        Collections.sort(chatList, (c1, c2) -> Long.compare(c2.getTimestamp(), c1.getTimestamp()));
        adapter.setChats(chatList);
    }

    private void showLoading(boolean show) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }

        getActivity().runOnUiThread(() -> {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        });
    }

    private void openChat(Chat chat) {
        Intent intent = new Intent(getActivity(), ChatActivity.class);
        intent.putExtra("chatId", chat.getChatId());
        intent.putExtra("recipientId", chat.getParticipantId());
        intent.putExtra("recipientName", chat.getParticipantName());
        intent.putExtra("recipientAvatar", chat.getParticipantAvatar());
        startActivity(intent);
    }
}