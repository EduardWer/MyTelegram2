package com.example.mytelegram;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GroupChatActivity extends AppCompatActivity {
    private static final String TAG = "GroupChatActivity";

    // Константы для выбора файлов
    private static final int REQUEST_IMAGE_PICK = 1001;
    private static final int REQUEST_IMAGE_CAPTURE = 1002;
    private static final int REQUEST_VIDEO_PICK = 1003;
    private static final int REQUEST_DOCUMENT_PICK = 1004;

    // UI элементы
    private LinearLayout editMessageLayout;
    private TextView editMessageLabel;
    private ImageButton cancelEditButton;
    private String editingMessageId = null;
    private Message editingMessage = null;

    private FrameLayout bottomSheet;
    private BottomSheetBehavior<FrameLayout> bottomSheetBehavior;
    private LinearLayout mediaPanelLayout;
    private com.google.android.material.tabs.TabLayout mediaTabs;
    private androidx.viewpager2.widget.ViewPager2 mediaViewPager;
    private ImageButton closeMediaPanelButton;

    private RecyclerView messagesRecyclerView;
    private GroupMessageAdapter messagesAdapter;
    private EditText messageEditText;
    private ImageButton sendButton;
    private ImageButton photoButton;
    private ImageButton backButton;
    private ImageView groupAvatar;
    private TextView groupName;
    private TextView groupStatus;
    private ProgressBar progressBar;

    // Элементы загрузки файлов
    private LinearLayout uploadProgressLayout;
    private ProgressBar uploadProgressBar;
    private TextView uploadProgressText;
    private TextView uploadFileName;
    private ImageButton cancelUploadButton;

    // Данные чата
    private String chatId;
    private String groupId;
    private String groupNameStr;
    private String currentUserId;

    // Firebase
    private DatabaseReference chatRef;
    private DatabaseReference groupRef;
    private DatabaseReference userChatsRef;
    private FirebaseUser currentUser;

    // Список сообщений и кэш участников
    private List<Message> messagesList;
    private Map<String, Integer> messagePositions;
    private Map<String, String> userNamesCache;
    private Map<String, String> userAvatarCache;

    // Настройки Яндекс.Облака
    private static final String YANDEX_CLOUD_ACCESS_KEY = "YCAJETFSyLNjaaVZt_qSnMevC";
    private static final String YANDEX_CLOUD_SECRET_KEY = "YCNfeBlLIjDPEhWRcWl14PYmQE9oOI6pXcePO6fu";

    // Для загрузки файлов
    private boolean isUploadCancelled = false;
    private Uri currentFileUri;
    private String currentFileType;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        getIntentData();
        initFirebase();
        initViews();
        setupRecyclerView();
        setupClickListeners();
        loadGroupInfo();
        loadMessages();
    }

    private void getIntentData() {
        Intent intent = getIntent();
        chatId = intent.getStringExtra("chatId");
        groupId = intent.getStringExtra("groupId");
        groupNameStr = intent.getStringExtra("groupName");

        if (chatId == null || groupId == null) {
            Toast.makeText(this, "Ошибка: не переданы данные чата", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Log.d(TAG, "Chat ID: " + chatId + ", Group ID: " + groupId);
    }

    private void initFirebase() {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        chatRef = FirebaseDatabase.getInstance().getReference()
                .child("chats")
                .child(chatId)
                .child("messages");

        groupRef = FirebaseDatabase.getInstance().getReference()
                .child("groups")
                .child(groupId);

        userChatsRef = FirebaseDatabase.getInstance().getReference()
                .child("userChats");
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        groupAvatar = findViewById(R.id.groupAvatar);
        groupName = findViewById(R.id.groupName);
        groupStatus = findViewById(R.id.groupStatus);

        messagesRecyclerView = findViewById(R.id.recyclerViewMessages);
        progressBar = findViewById(R.id.progressBar);

        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        photoButton = findViewById(R.id.photoButton);

        uploadProgressLayout = findViewById(R.id.uploadProgressLayout);
        uploadProgressBar = findViewById(R.id.uploadProgressBar);
        uploadProgressText = findViewById(R.id.uploadProgressText);
        uploadFileName = findViewById(R.id.uploadFileName);
        cancelUploadButton = findViewById(R.id.cancelUploadButton);

        editMessageLayout = findViewById(R.id.editMessageLayout);
        editMessageLabel = findViewById(R.id.editMessageLabel);
        cancelEditButton = findViewById(R.id.cancelEditButton);

        bottomSheet = findViewById(R.id.bottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setHideable(true);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        bottomSheetBehavior.setPeekHeight(1000);

        mediaPanelLayout = findViewById(R.id.mediaPanelLayout);
        mediaTabs = findViewById(R.id.mediaTabs);
        mediaViewPager = findViewById(R.id.mediaViewPager);
        closeMediaPanelButton = findViewById(R.id.closeMediaPanelButton);

        if (mediaViewPager != null && mediaTabs != null) {
            MediaPagerAdapter pagerAdapter = new MediaPagerAdapter(this);
            mediaViewPager.setAdapter(pagerAdapter);
            new TabLayoutMediator(mediaTabs, mediaViewPager,
                    (tab, position) -> tab.setText(position == 0 ? "Галерея" : "Документы")
            ).attach();
        }

        messagesList = new ArrayList<>();
        messagePositions = new HashMap<>();
        userNamesCache = new HashMap<>();
        userAvatarCache = new HashMap<>();

        if (groupNameStr != null) {
            groupName.setText(groupNameStr);
        } else {
            groupName.setText("Группа");
        }
    }

    private void loadGroupInfo() {
        groupRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    String avatar = snapshot.child("avatarUrl").getValue(String.class);
                    if (name != null) {
                        groupName.setText(name);
                        groupNameStr = name;
                    }
                    if (avatar != null && !avatar.isEmpty()) {
                        Glide.with(GroupChatActivity.this)
                                .load(avatar)
                                .placeholder(R.drawable.ic_person)
                                .circleCrop()
                                .into(groupAvatar);
                    }
                    DataSnapshot membersSnap = snapshot.child("members");
                    if (membersSnap.exists()) {
                        long count = membersSnap.getChildrenCount();
                        groupStatus.setText(count + " участников");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Ошибка загрузки группы: " + error.getMessage());
            }
        });
    }

    private void cacheUserInfo(String userId) {
        if (userNamesCache.containsKey(userId)) return;

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("username").getValue(String.class);
                    if (name != null) {
                        userNamesCache.put(userId, name);
                        messagesAdapter.notifyDataSetChanged();
                    }
                }
                loadAvatarUrl(userId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadAvatarUrl(String userId) {
        DatabaseReference avatarRef = FirebaseDatabase.getInstance().getReference("avatars").child(userId);
        avatarRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String avatarUrl = snapshot.getValue(String.class);
                    userAvatarCache.put(userId, avatarUrl);
                    messagesAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        View topBar = findViewById(R.id.topBar);
        if (topBar != null) {
            topBar.setOnClickListener(v -> openGroupInfo());
        }

        photoButton.setOnClickListener(v -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(messageEditText.getWindowToken(), 0);
                }
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });

        sendButton.setOnClickListener(v -> {
            if (editingMessageId != null) {
                updateEditedMessage();
            } else {
                sendTextMessage();
            }
        });

        cancelUploadButton.setOnClickListener(v -> cancelUpload());

        if (cancelEditButton != null) {
            cancelEditButton.setOnClickListener(v -> cancelEditing());
        }

        if (closeMediaPanelButton != null) {
            closeMediaPanelButton.setOnClickListener(v ->
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN));
        }
    }

    private void sendTextMessage() {
        String text = messageEditText.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "Введите сообщение", Toast.LENGTH_SHORT).show();
            return;
        }

        String messageId = chatRef.push().getKey();
        if (messageId == null) return;

        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("id", messageId);
        messageMap.put("text", text);
        messageMap.put("senderId", currentUserId);
        messageMap.put("timestamp", ServerValue.TIMESTAMP);
        messageMap.put("chatId", chatId);
        messageMap.put("chatType", "group");
        messageMap.put("messageType", "text");
        messageMap.put("isRead", false);
        messageMap.put("readBy", new HashMap<String, Boolean>());
        messageMap.put("edited", false);
        messageMap.put("senderName", userNamesCache.containsKey(currentUserId) ?
                userNamesCache.get(currentUserId) : "Участник");

        Message message = new Message();
        message.setId(messageId);
        message.setText(text);
        message.setSenderId(currentUserId);
        message.setTimestamp(System.currentTimeMillis());
        message.setChatId(chatId);
        message.setMessageType("text");
        if (messageMap.containsKey("senderName")) {
            message.setSenderName((String) messageMap.get("senderName"));
        }

        addNewMessage(message);

        chatRef.child(messageId).setValue(messageMap)
                .addOnSuccessListener(aVoid -> {
                    messageEditText.setText("");
                    updateLastMessageInfo(text, "text");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ошибка отправки: " + e.getMessage());
                    removeMessageById(messageId);
                });
    }

    private void addNewMessage(Message message) {
        messagesList.add(message);
        updateMessagePositions();
        messagesAdapter.notifyItemInserted(messagesList.size() - 1);
        scrollToBottom();
    }

    private void removeMessageById(String messageId) {
        Integer position = messagePositions.get(messageId);
        if (position != null && position >= 0 && position < messagesList.size()) {
            messagesList.remove(position.intValue());
            messagePositions.remove(messageId);
            updateMessagePositions();
            messagesAdapter.notifyItemRemoved(position);
        }
    }

    private void updateMessagePositions() {
        messagePositions.clear();
        for (int i = 0; i < messagesList.size(); i++) {
            messagePositions.put(messagesList.get(i).getId(), i);
        }
    }

    private void scrollToBottom() {
        if (!messagesList.isEmpty() && messagesRecyclerView != null) {
            messagesRecyclerView.scrollToPosition(messagesList.size() - 1);
        }
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void updateLastMessageInfo(String lastMessage, String messageType) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", lastMessage);
        updates.put("timestamp", ServerValue.TIMESTAMP);
        updates.put("lastMessageSenderId", currentUserId);
        updates.put("messageType", messageType);
        userChatsRef.child(currentUserId).child(chatId).updateChildren(updates);

        groupRef.child("members").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot memberSnap : snapshot.getChildren()) {
                    String memberId = memberSnap.getKey();
                    if (memberId == null || memberId.equals(currentUserId)) continue;

                    userChatsRef.child(memberId).child(chatId).child("lastMessage").setValue(lastMessage);
                    userChatsRef.child(memberId).child(chatId).child("timestamp").setValue(ServerValue.TIMESTAMP);
                    userChatsRef.child(memberId).child(chatId).child("lastMessageSenderId").setValue(currentUserId);
                    userChatsRef.child(memberId).child(chatId).child("messageType").setValue(messageType);

                    userChatsRef.child(memberId).child(chatId).child("unreadCount")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot countSnap) {
                                    int cur = 0;
                                    if (countSnap.exists()) {
                                        Integer count = countSnap.getValue(Integer.class);
                                        if (count != null) cur = count;
                                    }
                                    userChatsRef.child(memberId).child(chatId).child("unreadCount").setValue(cur + 1);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    userChatsRef.child(memberId).child(chatId).child("unreadCount").setValue(1);
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadMessages() {
        showLoading(true);
        chatRef.orderByChild("timestamp").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                messagesList.clear();
                messagePositions.clear();

                for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                    try {
                        String messageId = messageSnapshot.getKey();
                        if (messageId == null || messageId.equals("empty")) continue;

                        Map<String, Object> data = (Map<String, Object>) messageSnapshot.getValue();
                        if (data == null) continue;

                        Message msg = new Message();
                        msg.setId(messageId);

                        if (data.containsKey("text")) msg.setText((String) data.get("text"));
                        if (data.containsKey("senderId")) msg.setSenderId((String) data.get("senderId"));
                        if (data.containsKey("messageType")) msg.setMessageType((String) data.get("messageType"));
                        else msg.setMessageType("text");
                        if (data.containsKey("fileUrl")) msg.setFileUrl((String) data.get("fileUrl"));
                        if (data.containsKey("fileName")) msg.setFileName((String) data.get("fileName"));
                        if (data.containsKey("edited")) {
                            msg.setEdited(data.get("edited") instanceof Boolean ? (Boolean) data.get("edited") : false);
                        }
                        if (data.containsKey("senderName")) {
                            msg.setSenderName((String) data.get("senderName"));
                        } else {
                            msg.setSenderName("Участник");
                        }

                        if (data.containsKey("timestamp")) {
                            Object ts = data.get("timestamp");
                            if (ts instanceof Long) msg.setTimestamp((Long) ts);
                            else if (ts instanceof Integer) msg.setTimestamp(((Integer) ts).longValue());
                            else msg.setTimestamp(System.currentTimeMillis());
                        }

                        if (msg.getSenderId() != null && !msg.getSenderId().equals(currentUserId)) {
                            cacheUserInfo(msg.getSenderId());
                        }

                        messagesList.add(msg);
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка парсинга: " + e.getMessage());
                    }
                }

                Collections.sort(messagesList, (m1, m2) -> Long.compare(m1.getTimestamp(), m2.getTimestamp()));
                updateMessagePositions();
                messagesAdapter.setMessages(messagesList);
                scrollToBottom();
                markMessagesAsRead();
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Ошибка загрузки сообщений: " + error.getMessage());
            }
        });
    }

    private void markMessagesAsRead() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) messagesRecyclerView.getLayoutManager();
        if (layoutManager == null) return;

        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();

        if (firstVisible < 0 || lastVisible < 0) return;

        for (int i = firstVisible; i <= lastVisible; i++) {
            if (i < messagesList.size()) {
                Message message = messagesList.get(i);
                if (!message.getSenderId().equals(currentUserId) && !message.isReadByUser(currentUserId)) {
                    message.markAsRead(currentUserId);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("readBy", message.getReadBy());
                    updates.put("isRead", message.isRead());

                    chatRef.child(message.getId()).updateChildren(updates);
                }
            }
        }

        userChatsRef.child(currentUserId).child(chatId).child("unreadCount").setValue(0);
    }

    private void cancelUpload() {
        isUploadCancelled = true;
        showUploadProgress(false);
        Toast.makeText(this, "Загрузка отменена", Toast.LENGTH_SHORT).show();
    }

    private void showUploadProgress(boolean show) {
        if (uploadProgressLayout != null) {
            uploadProgressLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (!show) {
            uploadProgressBar.setProgress(0);
            uploadProgressText.setText("0%");
            uploadFileName.setText("Загрузка файла...");
        }
    }

    private void updateEditedMessage() {
        if (editingMessageId == null || editingMessage == null) return;

        String newText = messageEditText.getText().toString().trim();
        if (TextUtils.isEmpty(newText)) {
            Toast.makeText(this, "Сообщение не может быть пустым", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newText.equals(editingMessage.getText())) {
            cancelEditing();
            return;
        }

        showLoading(true);

        Map<String, Object> updates = new HashMap<>();
        updates.put("text", newText);
        updates.put("edited", true);
        updates.put("editedAt", ServerValue.TIMESTAMP);

        chatRef.child(editingMessageId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Сообщение изменено", Toast.LENGTH_SHORT).show();
                    cancelEditing();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Ошибка изменения сообщения: " + e.getMessage());
                    Toast.makeText(this, "Ошибка изменения сообщения", Toast.LENGTH_SHORT).show();
                });
    }

    private void cancelEditing() {
        editingMessageId = null;
        editingMessage = null;
        messageEditText.setText("");

        if (editMessageLayout != null) {
            editMessageLayout.setVisibility(View.GONE);
        }

        if (sendButton != null) {
            sendButton.setImageResource(R.drawable.ic_send);
        }

        messageEditText.clearFocus();
    }

    private void showEditMessageDialog(Message message) {
        editingMessageId = message.getId();
        editingMessage = message;

        messageEditText.setText(message.getText());
        messageEditText.setSelection(message.getText().length());

        editMessageLayout.setVisibility(View.VISIBLE);
        editMessageLabel.setText("Редактирование сообщения");

        sendButton.setImageResource(R.drawable.ic_check);
        messageEditText.requestFocus();
    }

    private void showDeleteMessageDialog(Message message) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить сообщение")
                .setMessage("Вы уверены, что хотите удалить это сообщение?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    chatRef.child(message.getId()).removeValue()
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(this, "Сообщение удалено", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Ошибка удаления", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void openGroupInfo() {
        Intent intent = new Intent(this, GroupInfoActivity.class);
        intent.putExtra("groupId", groupId);
        intent.putExtra("chatId", chatId);
        startActivity(intent);
    }

    public void closeMediaPanel() {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    private String formatTime(long timestamp) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            return "";
        }
    }

    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds);
    }

    // Интерфейс для действий с сообщениями
    interface OnMessageActionListener {
        void onMessageEdit(Message message);
        void onMessageDelete(Message message);
    }

    private void setupRecyclerView() {
        messagesAdapter = new GroupMessageAdapter(messagesList, currentUserId, userNamesCache, userAvatarCache);
        messagesAdapter.setOnMessageActionListener(new OnMessageActionListener() {
            @Override
            public void onMessageEdit(Message message) {
                showEditMessageDialog(message);
            }

            @Override
            public void onMessageDelete(Message message) {
                showDeleteMessageDialog(message);
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setAdapter(messagesAdapter);
    }

    // Адаптер сообщений для группового чата
    private class GroupMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<Message> messages;
        private String myUserId;
        private Map<String, String> nameCache;
        private Map<String, String> avatarCache;
        private OnMessageActionListener listener;

        public GroupMessageAdapter(List<Message> messages, String myUserId,
                                   Map<String, String> nameCache, Map<String, String> avatarCache) {
            this.messages = messages;
            this.myUserId = myUserId;
            this.nameCache = nameCache;
            this.avatarCache = avatarCache;
        }

        public void setOnMessageActionListener(OnMessageActionListener l) { this.listener = l; }

        public void setMessages(List<Message> msgs) {
            this.messages = msgs;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Message msg = messages.get(position);
            if (msg.getSenderId().equals(myUserId)) {
                return 0; // Свои сообщения
            } else {
                return 1; // Чужие сообщения
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == 0) {
                return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_send, parent, false));
            } else {
                return new ReceivedMessageViewHolder(inflater.inflate(R.layout.item_group_message_received, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Message msg = messages.get(position);
            if (holder instanceof SentMessageViewHolder) {
                ((SentMessageViewHolder) holder).bind(msg);
            } else if (holder instanceof ReceivedMessageViewHolder) {
                ((ReceivedMessageViewHolder) holder).bind(msg);
            }
        }

        @Override
        public int getItemCount() { return messages.size(); }

        // ViewHolder для своих сообщений
        class SentMessageViewHolder extends RecyclerView.ViewHolder {
            TextView messageText, messageTime;
            LinearLayout messageLayout;

            public SentMessageViewHolder(@NonNull View itemView) {
                super(itemView);
                messageText = itemView.findViewById(R.id.messageText);
                messageTime = itemView.findViewById(R.id.messageTime);
                messageLayout = itemView.findViewById(R.id.messageLayout);
            }

            public void bind(Message msg) {
                messageText.setText(msg.isEdited() ? msg.getText() + " (изм.)" : msg.getText());
                messageTime.setText(formatTime(msg.getTimestamp()));

                itemView.setOnLongClickListener(v -> {
                    if (listener != null) {
                        showMessageOptionsDialog(msg, v, msg.isTextMessage());
                        return true;
                    }
                    return false;
                });
            }
        }

        // ViewHolder для чужих сообщений (с аватаром и именем)
        class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
            ImageView senderAvatar;
            TextView senderName, messageText, messageTime;
            LinearLayout messageLayout;

            public ReceivedMessageViewHolder(@NonNull View itemView) {
                super(itemView);
                senderAvatar = itemView.findViewById(R.id.senderAvatar);
                senderName = itemView.findViewById(R.id.senderName);
                messageText = itemView.findViewById(R.id.messageText);
                messageTime = itemView.findViewById(R.id.messageTime);
                messageLayout = itemView.findViewById(R.id.messageLayout);
            }

            public void bind(Message msg) {
                messageText.setText(msg.isEdited() ? msg.getText() + " (изм.)" : msg.getText());
                messageTime.setText(formatTime(msg.getTimestamp()));

                String userId = msg.getSenderId();
                String cachedName = nameCache.get(userId);
                String cachedAvatar = avatarCache.get(userId);

                senderName.setText(cachedName != null ? cachedName : "Загрузка...");

                if (cachedAvatar != null) {
                    Glide.with(itemView.getContext())
                            .load(cachedAvatar)
                            .circleCrop()
                            .into(senderAvatar);
                } else {
                    senderAvatar.setImageResource(R.drawable.ic_person);
                }

                itemView.setOnLongClickListener(v -> {
                    if (listener != null) {
                        showMessageOptionsDialog(msg, v, false);
                        return true;
                    }
                    return false;
                });
            }
        }

        private void showMessageOptionsDialog(Message message, View anchorView, boolean canEdit) {
            PopupMenu popup = new PopupMenu(anchorView.getContext(), anchorView, Gravity.END);
            popup.inflate(R.menu.message_context_menu);

            boolean isMyMessage = message.getSenderId().equals(myUserId);
            popup.getMenu().findItem(R.id.action_edit).setVisible(canEdit && isMyMessage);
            popup.getMenu().findItem(R.id.action_delete).setVisible(isMyMessage);

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_edit) {
                    if (listener != null) listener.onMessageEdit(message);
                    return true;
                } else if (itemId == R.id.action_delete) {
                    if (listener != null) listener.onMessageDelete(message);
                    return true;
                }
                return false;
            });

            popup.show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        markMessagesAsRead();
        executorService.shutdown();
    }
}