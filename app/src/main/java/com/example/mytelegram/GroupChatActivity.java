package com.example.mytelegram;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
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
import android.provider.OpenableColumns;
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
import java.io.IOException;
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




    void loadVideoThumbnail(String videoUrl, ChatActivity.VideoThumbnailCallback callback) {
        executorService.execute(() -> {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                if (videoUrl.startsWith("http")) {
                    retriever.setDataSource(videoUrl, new HashMap<String, String>());
                } else {
                    retriever.setDataSource(videoUrl);
                }
                Bitmap bitmap = retriever.getFrameAtTime(1000000);
                if (bitmap != null) {
                    mainHandler.post(() -> callback.onThumbnailLoaded(bitmap));
                } else {
                    mainHandler.post(callback::onError);
                }
                retriever.release();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки превью видео: " + e.getMessage());
                mainHandler.post(callback::onError);
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

    void getVideoDuration(String videoUrl, ChatActivity.VideoDurationCallback callback) {
        executorService.execute(() -> {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                if (videoUrl.startsWith("http")) {
                    retriever.setDataSource(videoUrl, new HashMap<String, String>());
                } else {
                    retriever.setDataSource(videoUrl);
                }
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long duration = 0;
                if (durationStr != null) {
                    duration = Long.parseLong(durationStr) / 1000;
                }
                final long finalDuration = duration;
                mainHandler.post(() -> callback.onDurationLoaded(finalDuration));
                retriever.release();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения длительности видео: " + e.getMessage());
                mainHandler.post(() -> callback.onDurationLoaded(0));
            }
        });
    }

    void playVideo(String videoUrl, String videoTitle) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("video_url", videoUrl);
        intent.putExtra("video_title", videoTitle != null ? videoTitle : "Видео");
        startActivity(intent);
    }

    Bitmap getRoundedCornerBitmap(Bitmap bitmap, int radius) {
        if (bitmap == null) return null;
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        RectF rect = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return output;
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





    void downloadDocument(String fileUrl, String fileName) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            Toast.makeText(this, "Ошибка: неверная ссылка на документ", Toast.LENGTH_SHORT).show();
            return;
        }

        String finalFileName = fileName;
        if (finalFileName == null || finalFileName.isEmpty()) {
            String extension = getFileExtensionFromUrl(fileUrl);
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            finalFileName = "document_" + timeStamp + (extension.isEmpty() ? "" : "." + extension);
        }

        String objectKey = extractObjectKeyFromUrl(fileUrl);

        YandexCloudDownloader downloader = new YandexCloudDownloader(this);
        downloader.setDownloadListener(new YandexCloudDownloader.DownloadListener() {
            @Override
            public void onProgress(int progress) {}

            @Override
            public void onSuccess(File file) {
                runOnUiThread(() -> {
                    Toast.makeText(GroupChatActivity.this,
                            "✅ Документ сохранен: " + file.getName(),
                            Toast.LENGTH_LONG).show();
                    openDownloadedFile(file);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() ->
                        Toast.makeText(GroupChatActivity.this,
                                "❌ Ошибка скачивания: " + error,
                                Toast.LENGTH_LONG).show()
                );
            }
        });

        downloader.downloadPublicDocument("server21", objectKey, finalFileName);
    }


    private String extractObjectKeyFromUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath();
            if (path != null && path.startsWith("/")) {
                String withoutFirstSlash = path.substring(1);
                int firstSlashIndex = withoutFirstSlash.indexOf('/');
                if (firstSlashIndex != -1) {
                    return withoutFirstSlash.substring(firstSlashIndex + 1);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка парсинга URL: " + e.getMessage());
        }
        return "documents/" + System.currentTimeMillis() + ".pdf";
    }

    private String getFileExtensionFromUrl(String url) {
        if (url == null) return "";
        int lastDot = url.lastIndexOf('.');
        int lastSlash = url.lastIndexOf('/');
        if (lastDot != -1 && lastDot > lastSlash) {
            return url.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    private void openDownloadedFile(File file) {
        String fileName = file.getName();
        String extension = getFileExtension(fileName).toLowerCase();

        if (isVideoFile(extension)) {
            playVideo(Uri.fromFile(file).toString(), fileName);
        } else {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                String mimeType = getMimeType(fileName);

                Uri fileUri;

                // Пробуем через FileProvider
                try {
                    fileUri = FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".fileprovider",
                            file
                    );
                } catch (IllegalArgumentException e) {
                    // Если FileProvider не работает — копируем файл в кэш приложения
                    Log.w(TAG, "FileProvider failed, copying to cache: " + e.getMessage());
                    File cacheFile = new File(getCacheDir(), fileName);
                    try {
                        copyFile(file, cacheFile);
                        fileUri = FileProvider.getUriForFile(
                                this,
                                getPackageName() + ".fileprovider",
                                cacheFile
                        );
                    } catch (IOException ex) {
                        Log.e(TAG, "Failed to copy file: " + ex.getMessage());
                        Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                intent.setDataAndType(fileUri, mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Intent chooser = Intent.createChooser(intent, "Открыть файл с помощью");
                startActivity(chooser);

            } catch (Exception e) {
                Log.e(TAG, "Ошибка открытия файла: " + e.getMessage());
                Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Вспомогательный метод для копирования файла
    private void copyFile(File source, File dest) throws IOException {
        InputStream is = null;
        FileOutputStream os = null;
        try {
            is = new java.io.FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException e) {}
            }
            if (os != null) {
                try { os.close(); } catch (IOException e) {}
            }
        }
    }

    private boolean isVideoFile(String extension) {
        if (extension == null) return false;
        switch (extension.toLowerCase()) {
            case "mp4":
            case "avi":
            case "mkv":
            case "mov":
            case "wmv":
            case "flv":
            case "webm":
            case "3gp":
            case "m4v":
                return true;
            default:
                return false;
        }
    }

    private String getMimeType(String fileName) {
        if (fileName == null) return "*/*";
        String extension = getFileExtension(fileName).toLowerCase();
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "webp":
                return "image/webp";
            case "mp4":
                return "video/mp4";
            case "mkv":
                return "video/x-matroska";
            case "mov":
                return "video/quicktime";
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt":
                return "text/plain";
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";
            default:
                return "application/*";
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1);
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

        // Обновляем для отправителя (счетчик не увеличиваем)
        userChatsRef.child(currentUserId).child(chatId).updateChildren(updates);

        // Обновляем для всех участников группы
        groupRef.child("members").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot memberSnap : snapshot.getChildren()) {
                    String memberId = memberSnap.getKey();
                    if (memberId == null) continue;

                    // Для каждого участника обновляем информацию о чате
                    DatabaseReference memberChatRef = userChatsRef.child(memberId).child(chatId);

                    memberChatRef.child("lastMessage").setValue(lastMessage);
                    memberChatRef.child("timestamp").setValue(ServerValue.TIMESTAMP);
                    memberChatRef.child("lastMessageSenderId").setValue(currentUserId);
                    memberChatRef.child("messageType").setValue(messageType);

                    // Увеличиваем счетчик непрочитанных ТОЛЬКО для других участников (не для отправителя)
                    if (!memberId.equals(currentUserId)) {
                        memberChatRef.child("unreadCount").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot countSnap) {
                                int cur = 0;
                                if (countSnap.exists()) {
                                    Integer count = countSnap.getValue(Integer.class);
                                    if (count != null) cur = count;
                                }
                                memberChatRef.child("unreadCount").setValue(cur + 1);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                memberChatRef.child("unreadCount").setValue(1);
                            }
                        });
                    } else {
                        // Для отправителя счетчик = 0
                        memberChatRef.child("unreadCount").setValue(0);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Ошибка обновления счетчиков: " + error.getMessage());
            }
        });
    }


    private void markAllMessagesAsRead() {
        // Обнуляем счетчик непрочитанных для этого чата
        userChatsRef.child(currentUserId).child(chatId).child("unreadCount").setValue(0)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Unread count reset to 0"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to reset unread count: " + e.getMessage()));

        // Отмечаем все сообщения как прочитанные в Firebase
        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot msgSnap : snapshot.getChildren()) {
                    String senderId = msgSnap.child("senderId").getValue(String.class);

                    if (senderId != null && !senderId.equals(currentUserId)) {
                        // Проверяем, прочитано ли уже сообщение
                        boolean alreadyRead = false;
                        DataSnapshot readBySnapshot = msgSnap.child("readBy").child(currentUserId);
                        if (readBySnapshot.exists()) {
                            Boolean read = readBySnapshot.getValue(Boolean.class);
                            alreadyRead = read != null && read;
                        }

                        if (!alreadyRead) {
                            // Отмечаем как прочитанное
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("readBy/" + currentUserId, true);
                            updates.put("isRead", true);

                            msgSnap.getRef().updateChildren(updates)
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to mark message as read: " + e.getMessage()));
                        }
                    }
                }
                Log.d(TAG, "All messages marked as read");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error marking messages as read: " + error.getMessage());
            }
        });
    }



    private void markVisibleMessagesAsRead() {
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
    }

    // Вызовите в onCreate после setupRecyclerView


    // Вызовите этот метод в onResume
    @Override
    protected void onResume() {
        super.onResume();
        markAllMessagesAsRead();
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
                        if (data.containsKey("messageType")) {
                            msg.setMessageType((String) data.get("messageType"));
                        } else {
                            msg.setMessageType("text");
                        }
                        if (data.containsKey("fileUrl")) msg.setFileUrl((String) data.get("fileUrl"));
                        if (data.containsKey("fileName")) msg.setFileName((String) data.get("fileName"));
                        if (data.containsKey("fileSize")) {
                            Object sizeObj = data.get("fileSize");
                            if (sizeObj instanceof Long) msg.setFileSize((Long) sizeObj);
                            else if (sizeObj instanceof Integer) msg.setFileSize(((Integer) sizeObj).longValue());
                        }
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

    public void sendDocumentFromPicker(Uri uri) {
        Log.d(TAG, "sendDocumentFromPicker: uri=" + uri);
        if (uri == null) {
            Toast.makeText(this, "Ошибка: файл не выбран", Toast.LENGTH_SHORT).show();
            return;
        }

        currentFileUri = uri;
        currentFileType = "document";
        uploadFile(uri, "document");
        closeMediaPanel();
    }
    private void uploadFile(Uri fileUri, String fileType) {
        isUploadCancelled = false;
        showUploadProgress(true);

        String originalFileName = getFileName(fileUri);
        uploadFileName.setText(originalFileName);

        // Создаем временный файл (сжимаем если изображение)
        File tempFile;
        if ("image".equals(fileType)) {
            tempFile = createCompressedImageFile(fileUri);
        } else {
            tempFile = createTempFileFromUri(fileUri);
        }

        if (tempFile == null) {
            showUploadProgress(false);
            Toast.makeText(this, "Не удалось создать временный файл", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalFileName = originalFileName;
        final File finalTempFile = tempFile;

        YandexCloudUploader uploader = new YandexCloudUploader(
                YANDEX_CLOUD_ACCESS_KEY,
                YANDEX_CLOUD_SECRET_KEY
        );

        String fileName = generateYandexFileName(fileType, originalFileName);
        String bucketName = "server21";

        uploader.uploadFile(tempFile, bucketName, fileName,
                new YandexCloudUploader.UploadCallback() {
                    @Override
                    public void onSuccess(String fileUrl) {
                        runOnUiThread(() -> {
                            showUploadProgress(false);
                            sendFileMessage(fileType, fileUrl, finalFileName);
                            Toast.makeText(GroupChatActivity.this, "Файл загружен", Toast.LENGTH_SHORT).show();
                            if (finalTempFile.exists()) {
                                finalTempFile.delete();
                            }
                            resetBottomSheet();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            showUploadProgress(false);
                            Log.e(TAG, "Ошибка загрузки: " + error);
                            Toast.makeText(GroupChatActivity.this, "Ошибка загрузки: " + error, Toast.LENGTH_LONG).show();
                            if (finalTempFile.exists()) {
                                finalTempFile.delete();
                            }
                            resetBottomSheet();
                        });
                    }

                    @Override
                    public void onProgress(int progress) {
                        runOnUiThread(() -> {
                            if (!isUploadCancelled) {
                                updateUploadProgress(progress, finalFileName);
                            }
                        });
                    }
                });
    }

// Вспомогательные методы для uploadFile:

    private File createCompressedImageFile(Uri uri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            InputStream inputStream = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            int maxSize = 1920;
            int scale = 1;

            if (options.outWidth > maxSize || options.outHeight > maxSize) {
                scale = (int) Math.pow(2, (int) Math.round(
                        Math.log(maxSize / (double) Math.max(options.outWidth, options.outHeight)) / Math.log(0.5)));
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = scale;

            inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            if (bitmap == null) return null;

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            if (width > maxSize || height > maxSize) {
                float ratio = (float) width / height;
                if (ratio > 1) {
                    width = maxSize;
                    height = (int) (maxSize / ratio);
                } else {
                    height = maxSize;
                    width = (int) (maxSize * ratio);
                }
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }

            File tempFile = new File(getCacheDir(), "compressed_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream out = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
            out.flush();
            out.close();
            bitmap.recycle();

            return tempFile;
        } catch (Exception e) {
            Log.e(TAG, "Ошибка сжатия изображения: " + e.getMessage());
            return createTempFileFromUri(uri);
        }
    }

    private File createTempFileFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            String fileName = getFileName(uri);
            File tempFile = new File(getCacheDir(), "upload_" + System.currentTimeMillis() + "_" + fileName);

            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            outputStream.close();
            inputStream.close();
            return tempFile;
        } catch (Exception e) {
            Log.e(TAG, "Ошибка создания временного файла", e);
            return null;
        }
    }

    private String generateYandexFileName(String fileType, String originalFileName) {
        String extension = getFileExtension(originalFileName);
        String uuid = UUID.randomUUID().toString();

        if (extension.isEmpty()) {
            extension = getDefaultExtension(fileType);
        }

        switch (fileType) {
            case "image":
                return "chat_images/" + uuid + "." + extension;
            case "video":
                return "chat_videos/" + uuid + "." + extension;
            case "document":
                return "chat_documents/" + uuid + (extension.isEmpty() ? "" : "." + extension);
            default:
                return "chat_files/" + uuid + (extension.isEmpty() ? "" : "." + extension);
        }
    }



    private String getDefaultExtension(String fileType) {
        switch (fileType) {
            case "image": return "jpg";
            case "video": return "mp4";
            default: return "";
        }
    }

    private String getFileName(Uri uri) {
        String result = null;

        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения имени файла: " + e.getMessage());
            }
        }

        if (result == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    result = path.substring(cut + 1);
                } else {
                    result = path;
                }
            }
        }

        if (result == null) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            result = "file_" + timeStamp;
        }

        return result;
    }

    private void sendFileMessage(String messageType, String fileUrl, String fileName) {
        String messageId = chatRef.push().getKey();
        if (messageId == null) {
            Toast.makeText(this, "Ошибка создания сообщения", Toast.LENGTH_SHORT).show();
            return;
        }

        long timestamp = System.currentTimeMillis();
        String messageText = getMessageTextForType(messageType);

        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("id", messageId);
        messageMap.put("text", messageText);
        messageMap.put("senderId", currentUserId);
        messageMap.put("timestamp", ServerValue.TIMESTAMP);
        messageMap.put("chatId", chatId);
        messageMap.put("chatType", "group");
        messageMap.put("messageType", messageType);
        messageMap.put("fileUrl", fileUrl);
        messageMap.put("fileName", fileName);
        messageMap.put("fileSize", 1024000);
        messageMap.put("isRead", false);
        messageMap.put("readBy", new HashMap<String, Boolean>());
        messageMap.put("edited", false);
        messageMap.put("senderName", userNamesCache.containsKey(currentUserId) ?
                userNamesCache.get(currentUserId) : "Участник");

        Message message = new Message();
        message.setId(messageId);
        message.setText(messageText);
        message.setSenderId(currentUserId);
        message.setTimestamp(timestamp);
        message.setChatId(chatId);
        message.setMessageType(messageType);
        message.setFileUrl(fileUrl);
        message.setFileName(fileName);
        message.setSenderName((String) messageMap.get("senderName"));

        addNewMessage(message);

        chatRef.child(messageId).setValue(messageMap)
                .addOnSuccessListener(aVoid -> {
                    updateLastMessageInfo(messageText, messageType);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ошибка отправки файла: " + e.getMessage());
                    Toast.makeText(GroupChatActivity.this, "Ошибка отправки файла", Toast.LENGTH_SHORT).show();
                    removeMessageById(messageId);
                });
    }

    private String getMessageTextForType(String messageType) {
        switch (messageType) {
            case "image": return "📷 Изображение";
            case "video": return "🎥 Видео";
            case "document": return "📄 Документ";
            default: return "📎 Файл";
        }
    }



    private void updateUploadProgress(int progress, String fileName) {
        if (uploadProgressBar != null) {
            uploadProgressBar.setProgress(progress);
        }
        if (uploadProgressText != null) {
            uploadProgressText.setText(progress + "%");
        }
        if (uploadFileName != null && fileName != null) {
            uploadFileName.setText(fileName);
        }
    }

    private void resetBottomSheet() {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            bottomSheetBehavior.setPeekHeight(1000);
        }
    }

    public void sendMediaFromGallery(Uri uri, String type) {
        Log.d(TAG, "sendMediaFromGallery: type=" + type + ", uri=" + uri);
        if (uri == null) {
            Toast.makeText(this, "Ошибка: файл не выбран", Toast.LENGTH_SHORT).show();
            return;
        }

        closeMediaPanel();
        currentFileUri = uri;

        if ("image".equals(type)) {
            currentFileType = "image";
            uploadFile(uri, "image");
        } else if ("video".equals(type)) {
            currentFileType = "video";
            uploadFile(uri, "video");
        }
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

        private static final int TYPE_SENT_TEXT = 0;
        private static final int TYPE_RECEIVED_TEXT = 1;
        private static final int TYPE_SENT_IMAGE = 2;
        private static final int TYPE_RECEIVED_IMAGE = 3;
        private static final int TYPE_SENT_VIDEO = 4;
        private static final int TYPE_RECEIVED_VIDEO = 5;
        private static final int TYPE_SENT_DOCUMENT = 6;
        private static final int TYPE_RECEIVED_DOCUMENT = 7;

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
            boolean isSent = msg.getSenderId() != null && msg.getSenderId().equals(myUserId);

            switch (msg.getMessageType()) {
                case "image":
                    return isSent ? TYPE_SENT_IMAGE : TYPE_RECEIVED_IMAGE;
                case "video":
                    return isSent ? TYPE_SENT_VIDEO : TYPE_RECEIVED_VIDEO;
                case "document":
                    return isSent ? TYPE_SENT_DOCUMENT : TYPE_RECEIVED_DOCUMENT;
                default:
                    return isSent ? TYPE_SENT_TEXT : TYPE_RECEIVED_TEXT;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            switch (viewType) {
                case TYPE_SENT_IMAGE:
                    return new SentImageViewHolder(inflater.inflate(R.layout.item_image_sent, parent, false));
                case TYPE_RECEIVED_IMAGE:
                    return new ReceivedImageViewHolder(inflater.inflate(R.layout.item_image_received, parent, false));
                case TYPE_SENT_VIDEO:
                    return new SentVideoViewHolder(inflater.inflate(R.layout.item_video_sent, parent, false));
                case TYPE_RECEIVED_VIDEO:
                    return new ReceivedVideoViewHolder(inflater.inflate(R.layout.item_video_received, parent, false));
                case TYPE_SENT_DOCUMENT:
                    return new SentDocumentViewHolder(inflater.inflate(R.layout.item_message_send, parent, false));
                case TYPE_RECEIVED_DOCUMENT:
                    return new ReceivedDocumentViewHolder(inflater.inflate(R.layout.item_group_message_received, parent, false));
                case TYPE_SENT_TEXT:
                    return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_send, parent, false));
                case TYPE_RECEIVED_TEXT:
                    return new ReceivedMessageViewHolder(inflater.inflate(R.layout.item_group_message_received, parent, false));
                default:
                    return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_send, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Message msg = messages.get(position);

            if (holder instanceof SentImageViewHolder) {
                ((SentImageViewHolder) holder).bind(msg);
            } else if (holder instanceof ReceivedImageViewHolder) {
                ((ReceivedImageViewHolder) holder).bind(msg);
            } else if (holder instanceof SentVideoViewHolder) {
                ((SentVideoViewHolder) holder).bind(msg);
            } else if (holder instanceof ReceivedVideoViewHolder) {
                ((ReceivedVideoViewHolder) holder).bind(msg);
            } else if (holder instanceof SentDocumentViewHolder) {
                ((SentDocumentViewHolder) holder).bind(msg);
            } else if (holder instanceof ReceivedDocumentViewHolder) {
                ((ReceivedDocumentViewHolder) holder).bind(msg);
            } else if (holder instanceof SentMessageViewHolder) {
                ((SentMessageViewHolder) holder).bind(msg);
            } else if (holder instanceof ReceivedMessageViewHolder) {
                ((ReceivedMessageViewHolder) holder).bind(msg);
            }
        }

        @Override
        public int getItemCount() { return messages.size(); }

        // ==================== TEXT MESSAGE VIEWHOLDERS ====================

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
                String text = msg.getText();
                if (msg.isEdited()) text += " (изм.)";
                messageText.setText(text);
                messageTime.setText(formatTime(msg.getTimestamp()));

                itemView.setOnLongClickListener(v -> {
                    if (listener != null) {
                        showMessageOptionsDialog(msg, v, true);
                        return true;
                    }
                    return false;
                });
            }
        }

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
                String text = msg.getText();
                if (msg.isEdited()) text += " (изм.)";
                messageText.setText(text);
                messageTime.setText(formatTime(msg.getTimestamp()));

                String userId = msg.getSenderId();
                senderName.setText(nameCache.getOrDefault(userId, "Загрузка..."));

                if (avatarCache.containsKey(userId)) {
                    Glide.with(itemView.getContext())
                            .load(avatarCache.get(userId))
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

        // ==================== IMAGE MESSAGE VIEWHOLDERS ====================

        class SentImageViewHolder extends RecyclerView.ViewHolder {
            ImageView imageMessage;
            TextView messageTime;
            ProgressBar imageProgress;

            public SentImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageMessage = itemView.findViewById(R.id.imageMessage);
                messageTime = itemView.findViewById(R.id.messageTime);
                imageProgress = itemView.findViewById(R.id.imageProgress);
            }

            public void bind(Message msg) {
                String imageUrl = msg.getFileUrl();
                messageTime.setText(formatTime(msg.getTimestamp()));
                imageProgress.setVisibility(View.VISIBLE);
                imageMessage.setImageDrawable(null);

                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Glide.with(itemView.getContext())
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_broken_image)
                            .override(800, 800)
                            .centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(new CustomTarget<Drawable>() {
                                @Override
                                public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                    imageProgress.setVisibility(View.GONE);
                                    imageMessage.setImageDrawable(resource);
                                }
                                @Override
                                public void onLoadCleared(@Nullable Drawable placeholder) {}
                                @Override
                                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                    imageProgress.setVisibility(View.GONE);
                                    imageMessage.setImageDrawable(errorDrawable);
                                }
                            });

                    imageMessage.setOnClickListener(v -> {
                        Intent intent = new Intent(GroupChatActivity.this, FullImageActivity.class);
                        intent.putExtra("image_url", imageUrl);
                        startActivity(intent);
                    });
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

        class ReceivedImageViewHolder extends RecyclerView.ViewHolder {
            ImageView imageMessage, senderAvatar;
            TextView senderName, messageTime;
            ProgressBar imageProgress;

            public ReceivedImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageMessage = itemView.findViewById(R.id.imageMessage);
                senderAvatar = itemView.findViewById(R.id.senderAvatar);
                senderName = itemView.findViewById(R.id.senderName);
                messageTime = itemView.findViewById(R.id.messageTime);
                imageProgress = itemView.findViewById(R.id.imageProgress);
            }

            public void bind(Message msg) {
                String imageUrl = msg.getFileUrl();
                if (messageTime != null) {
                    messageTime.setText(formatTime(msg.getTimestamp()));
                }
                if (imageProgress != null) {
                    imageProgress.setVisibility(View.VISIBLE);
                }
                if (imageMessage != null) {
                    imageMessage.setImageDrawable(null);
                }

                String userId = msg.getSenderId();
                if (senderName != null) {
                    senderName.setText(nameCache.getOrDefault(userId, "Загрузка..."));
                }

                if (senderAvatar != null) {
                    if (avatarCache.containsKey(userId)) {
                        Glide.with(itemView.getContext())
                                .load(avatarCache.get(userId))
                                .circleCrop()
                                .into(senderAvatar);
                    } else {
                        senderAvatar.setImageResource(R.drawable.ic_person);
                    }
                }

                if (imageUrl != null && !imageUrl.isEmpty() && imageMessage != null) {
                    Glide.with(itemView.getContext())
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_broken_image)
                            .override(800, 800)
                            .centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(new CustomTarget<Drawable>() {
                                @Override
                                public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                    if (imageProgress != null) imageProgress.setVisibility(View.GONE);
                                    if (imageMessage != null) imageMessage.setImageDrawable(resource);
                                }
                                @Override
                                public void onLoadCleared(@Nullable Drawable placeholder) {
                                    if (imageProgress != null) imageProgress.setVisibility(View.GONE);
                                }
                                @Override
                                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                    super.onLoadFailed(errorDrawable);
                                    if (imageProgress != null) imageProgress.setVisibility(View.GONE);
                                    if (imageMessage != null) imageMessage.setImageDrawable(errorDrawable);
                                }
                            });

                    imageMessage.setOnClickListener(v -> {
                        Intent intent = new Intent(GroupChatActivity.this, FullImageActivity.class);
                        intent.putExtra("image_url", imageUrl);
                        intent.putExtra("FileName", msg.getFileName());
                        startActivity(intent);
                    });
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




        class SentVideoViewHolder extends RecyclerView.ViewHolder {
            ImageView videoThumbnail, playButton;
            TextView videoDuration, messageTime;
            ProgressBar videoProgress;

            public SentVideoViewHolder(@NonNull View itemView) {
                super(itemView);
                videoThumbnail = itemView.findViewById(R.id.videoThumbnail);
                playButton = itemView.findViewById(R.id.playButton);
                videoDuration = itemView.findViewById(R.id.videoDuration);
                messageTime = itemView.findViewById(R.id.messageTime);
                videoProgress = itemView.findViewById(R.id.videoProgress);
            }

            public void bind(Message msg) {
                String videoUrl = msg.getFileUrl();
                messageTime.setText(formatTime(msg.getTimestamp()));
                videoProgress.setVisibility(View.VISIBLE);

                if (videoUrl != null && !videoUrl.isEmpty()) {
                    // Загружаем превью видео
                    GroupChatActivity.this.loadVideoThumbnail(videoUrl, new ChatActivity.VideoThumbnailCallback() {
                        @Override
                        public void onThumbnailLoaded(Bitmap bitmap) {
                            videoProgress.setVisibility(View.GONE);

                            // Скругляем углы превью
                            Bitmap rounded = GroupChatActivity.this.getRoundedCornerBitmap(bitmap, 48);
                            videoThumbnail.setImageBitmap(rounded != null ? rounded : bitmap);

                            // Применяем закругление через Outline (Android 5.0+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                videoThumbnail.setClipToOutline(true);
                                videoThumbnail.setOutlineProvider(new ViewOutlineProvider() {
                                    @Override
                                    public void getOutline(View view, Outline outline) {
                                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 48);
                                    }
                                });
                            }

                            // Получаем длительность видео
                            GroupChatActivity.this.getVideoDuration(videoUrl, duration -> {
                                if (duration > 0) {
                                    videoDuration.setText(GroupChatActivity.this.formatDuration(duration));
                                    videoDuration.setVisibility(View.VISIBLE);
                                }
                            });
                        }

                        @Override
                        public void onError() {
                            videoProgress.setVisibility(View.GONE);
                            videoThumbnail.setImageResource(R.drawable.ic_video_placeholder);
                        }
                    });

                    // Кнопка воспроизведения
                    View.OnClickListener playListener = v ->
                            GroupChatActivity.this.playVideo(videoUrl, msg.getFileName());
                    playButton.setOnClickListener(playListener);
                    videoThumbnail.setOnClickListener(playListener);
                }

                // Длинное нажатие для меню
                itemView.setOnLongClickListener(v -> {
                    if (listener != null) {
                        showMessageOptionsDialog(msg, v, false);
                        return true;
                    }
                    return false;
                });
            }
        }

        class ReceivedVideoViewHolder extends RecyclerView.ViewHolder {
            ImageView videoThumbnail, playButton, senderAvatar;
            TextView videoDuration, messageTime, senderName;
            ProgressBar videoProgress;

            public ReceivedVideoViewHolder(@NonNull View itemView) {
                super(itemView);
                videoThumbnail = itemView.findViewById(R.id.videoThumbnail);
                playButton = itemView.findViewById(R.id.playButton);
                videoDuration = itemView.findViewById(R.id.videoDuration);
                messageTime = itemView.findViewById(R.id.messageTime);
                videoProgress = itemView.findViewById(R.id.videoProgress);
                senderAvatar = itemView.findViewById(R.id.senderAvatar);
                senderName = itemView.findViewById(R.id.senderName);
            }

            public void bind(Message msg) {
                String videoUrl = msg.getFileUrl();
                messageTime.setText(formatTime(msg.getTimestamp()));
                videoProgress.setVisibility(View.VISIBLE);

                String userId = msg.getSenderId();
                senderName.setText(nameCache.getOrDefault(userId, "Загрузка..."));

                if (avatarCache.containsKey(userId)) {
                    Glide.with(itemView.getContext())
                            .load(avatarCache.get(userId))
                            .circleCrop()
                            .into(senderAvatar);
                } else {
                    senderAvatar.setImageResource(R.drawable.ic_person);
                }

                if (videoUrl != null && !videoUrl.isEmpty()) {
                    GroupChatActivity.this.loadVideoThumbnail(videoUrl, new ChatActivity.VideoThumbnailCallback() {
                        @Override
                        public void onThumbnailLoaded(Bitmap bitmap) {
                            videoProgress.setVisibility(View.GONE);
                            Bitmap rounded = GroupChatActivity.this.getRoundedCornerBitmap(bitmap, 48);
                            videoThumbnail.setImageBitmap(rounded != null ? rounded : bitmap);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                videoThumbnail.setClipToOutline(true);
                                videoThumbnail.setOutlineProvider(new ViewOutlineProvider() {
                                    @Override
                                    public void getOutline(View view, Outline outline) {
                                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 48);
                                    }
                                });
                            }
                            GroupChatActivity.this.getVideoDuration(videoUrl, new ChatActivity.VideoDurationCallback() {
                                @Override
                                public void onDurationLoaded(long duration) {
                                    if (duration > 0) {
                                        videoDuration.setText(GroupChatActivity.this.formatDuration(duration));
                                        videoDuration.setVisibility(View.VISIBLE);
                                    }
                                }
                            });
                        }
                        @Override
                        public void onError() {
                            videoProgress.setVisibility(View.GONE);
                            videoThumbnail.setImageResource(R.drawable.ic_video_placeholder);
                        }
                    });

                    View.OnClickListener playListener = v -> GroupChatActivity.this.playVideo(videoUrl, msg.getFileName());
                    playButton.setOnClickListener(playListener);
                    videoThumbnail.setOnClickListener(playListener);
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

// ==================== DOCUMENT MESSAGE VIEWHOLDERS ====================

        class SentDocumentViewHolder extends RecyclerView.ViewHolder {
            TextView messageText, messageTime;
            LinearLayout messageLayout;

            public SentDocumentViewHolder(@NonNull View itemView) {
                super(itemView);
                messageText = itemView.findViewById(R.id.messageText);
                messageTime = itemView.findViewById(R.id.messageTime);
                messageLayout = itemView.findViewById(R.id.messageLayout);
            }

            public void bind(Message msg) {
                String fileName = msg.getFileName();
                messageText.setText("📄 " + (fileName != null ? fileName : "Документ"));
                messageTime.setText(formatTime(msg.getTimestamp()));

                messageLayout.setOnClickListener(v -> {
                    if (msg.getFileUrl() != null && !msg.getFileUrl().isEmpty()) {
                        GroupChatActivity.this.downloadDocument(msg.getFileUrl(), msg.getFileName());
                    }
                });

                itemView.setOnLongClickListener(v -> {
                    if (listener != null) {
                        showMessageOptionsDialog(msg, v, false);
                        return true;
                    }
                    return false;
                });
            }
        }

        class ReceivedDocumentViewHolder extends RecyclerView.ViewHolder {
            ImageView senderAvatar;
            TextView senderName, messageText, messageTime;
            LinearLayout messageLayout;

            public ReceivedDocumentViewHolder(@NonNull View itemView) {
                super(itemView);
                senderAvatar = itemView.findViewById(R.id.senderAvatar);
                senderName = itemView.findViewById(R.id.senderName);
                messageText = itemView.findViewById(R.id.messageText);
                messageTime = itemView.findViewById(R.id.messageTime);
                messageLayout = itemView.findViewById(R.id.messageLayout);
            }

            public void bind(Message msg) {
                String fileName = msg.getFileName();
                messageText.setText("📄 " + (fileName != null ? fileName : "Документ"));
                messageTime.setText(formatTime(msg.getTimestamp()));

                String userId = msg.getSenderId();
                senderName.setText(nameCache.getOrDefault(userId, "Загрузка..."));

                if (avatarCache.containsKey(userId)) {
                    Glide.with(itemView.getContext())
                            .load(avatarCache.get(userId))
                            .circleCrop()
                            .into(senderAvatar);
                } else {
                    senderAvatar.setImageResource(R.drawable.ic_person);
                }

                messageLayout.setOnClickListener(v -> {
                    if (msg.getFileUrl() != null && !msg.getFileUrl().isEmpty()) {
                        GroupChatActivity.this.downloadDocument(msg.getFileUrl(), msg.getFileName());
                    }
                });

                itemView.setOnLongClickListener(v -> {
                    if (listener != null) {
                        showMessageOptionsDialog(msg, v, false);
                        return true;
                    }
                    return false;
                });
            }
        }

// ==================== CONTEXT MENU ====================

        private void showMessageOptionsDialog(Message message, View anchorView, boolean canEdit) {
            PopupMenu popup = new PopupMenu(anchorView.getContext(), anchorView, Gravity.END);
            popup.inflate(R.menu.message_context_menu);

            boolean isMyMessage = message.getSenderId() != null && message.getSenderId().equals(myUserId);
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
}