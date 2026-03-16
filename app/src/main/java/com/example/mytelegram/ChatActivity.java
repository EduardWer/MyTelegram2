package com.example.mytelegram;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";

    // Константы для выбора файлов
    private static final int REQUEST_IMAGE_PICK = 1001;
    private static final int REQUEST_IMAGE_CAPTURE = 1002;
    private static final int REQUEST_VIDEO_PICK = 1003;
    private static final int REQUEST_DOCUMENT_PICK = 1004;

    // UI элементы
    private RecyclerView messagesRecyclerView;
    private MessageAdapter messagesAdapter;
    private EditText messageEditText;
    private ImageButton sendButton;
    private ImageButton photoButton;
    private ImageButton backButton;
    private ImageView userAvatar;
    private TextView userName;
    private TextView userStatus;
    private ProgressBar progressBar;

    // Элементы загрузки файлов
    private LinearLayout uploadProgressLayout;
    private ProgressBar uploadProgressBar;
    private TextView uploadProgressText;
    private TextView uploadFileName;
    private ImageButton cancelUploadButton;

    // Данные чата
    private String chatId;
    private String recipientId;
    private String recipientName;
    private String currentUserId;

    // Firebase
    private DatabaseReference chatRef;
    private DatabaseReference userChatsRef;
    private FirebaseUser currentUser;

    // Список сообщений
    private List<Message> messagesList;
    private Map<String, Integer> messagePositions;

    // Настройки Яндекс.Облака
    private static final String YANDEX_CLOUD_ACCESS_KEY = "YCAJETFSyLNjaaVZt_qSnMevC";
    private static final String YANDEX_CLOUD_SECRET_KEY = "YCNfeBlLIjDPEhWRcWl14PYmQE9oOI6pXcePO6fu";

    // Для загрузки файлов
    private boolean isUploadCancelled = false;
    private Uri currentFileUri;
    private String currentFileType;

    // Executor для видео превью
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Получаем данные из Intent
        getIntentData();

        // Инициализация Firebase
        initFirebase();

        // Инициализация UI
        initViews();

        // Настройка RecyclerView
        setupRecyclerView();

        // Настройка кликов
        setupClickListeners();

        // Загрузка информации о пользователе
        loadUserInfo();

        // Загрузка сообщений
        loadMessages();
    }

    private void getIntentData() {
        Intent intent = getIntent();
        chatId = intent.getStringExtra("chatId");
        recipientId = intent.getStringExtra("recipientId");
        recipientName = intent.getStringExtra("recipientName");

        if (chatId == null || recipientId == null) {
            Toast.makeText(this, "Ошибка: не переданы данные чата", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "Chat ID: " + chatId);
        Log.d(TAG, "Recipient ID: " + recipientId);
        Log.d(TAG, "Recipient Name: " + recipientName);
    }

    private void initFirebase() {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        // Инициализация ссылок на базу данных
        chatRef = FirebaseDatabase.getInstance().getReference()
                .child("chats")
                .child(chatId)
                .child("messages");

        userChatsRef = FirebaseDatabase.getInstance().getReference()
                .child("userChats");
    }

    private void initViews() {
        // Верхняя панель
        backButton = findViewById(R.id.backButton);
        userAvatar = findViewById(R.id.userAvatar);
        userName = findViewById(R.id.userName);
        userStatus = findViewById(R.id.userStatus);

        // Сообщения
        messagesRecyclerView = findViewById(R.id.recyclerViewMessages);
        progressBar = findViewById(R.id.progressBar);

        // Панель ввода
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        photoButton = findViewById(R.id.photoButton);

        // Элементы загрузки файлов
        uploadProgressLayout = findViewById(R.id.uploadProgressLayout);
        uploadProgressBar = findViewById(R.id.uploadProgressBar);
        uploadProgressText = findViewById(R.id.uploadProgressText);
        uploadFileName = findViewById(R.id.uploadFileName);
        cancelUploadButton = findViewById(R.id.cancelUploadButton);

        // Инициализация списка сообщений и карты позиций
        messagesList = new ArrayList<>();
        messagePositions = new HashMap<>();

        // Устанавливаем имя получателя
        if (recipientName != null) {
            userName.setText(recipientName);
        } else {
            userName.setText("Пользователь");
        }
    }

    private void setupRecyclerView() {
        messagesAdapter = new MessageAdapter(messagesList, currentUserId);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setAdapter(messagesAdapter);

        messagesAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                if (positionStart == messagesList.size() - 1) {
                    scrollToBottom();
                }
            }
        });
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> navigateToHomeFragment());

        View topBar = findViewById(R.id.topBar);
        if (topBar != null) {
            topBar.setOnClickListener(v -> openUserProfile());
        }
        userAvatar.setOnClickListener(v -> openUserProfile());
        userName.setOnClickListener(v -> openUserProfile());

        sendButton.setOnClickListener(v -> sendTextMessage());
        photoButton.setOnClickListener(v -> showAttachmentDialog());
        cancelUploadButton.setOnClickListener(v -> cancelUpload());

        messageEditText.setOnClickListener(v -> scrollToBottom());
    }

    private void showAttachmentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Прикрепить файл");

        String[] options = {"📷 Фото из галереи", "📸 Сделать фото", "🎥 Видео", "📄 Документ"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    pickImageFromGallery();
                    break;
                case 1:
                    takePhoto();
                    break;
                case 2:
                    pickVideo();
                    break;
                case 3:
                    pickDocument();
                    break;
            }
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    private void takePhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = createImageFile();
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        startActivityForResult(intent, REQUEST_VIDEO_PICK);
    }

    private void pickDocument() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_DOCUMENT_PICK);
    }

    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = null;
        try {
            image = File.createTempFile(imageFileName, ".jpg", storageDir);
            currentFileUri = Uri.fromFile(image);
        } catch (IOException e) {
            Log.e(TAG, "Ошибка создания файла: " + e.getMessage());
        }
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_IMAGE_CAPTURE:
                    currentFileType = "image";
                    uploadFile(currentFileUri, currentFileType);
                    break;

                case REQUEST_IMAGE_PICK:
                    if (data != null && data.getData() != null) {
                        currentFileUri = data.getData();
                        currentFileType = "image";
                        uploadFile(currentFileUri, currentFileType);
                    }
                    break;

                case REQUEST_VIDEO_PICK:
                    if (data != null && data.getData() != null) {
                        currentFileUri = data.getData();
                        currentFileType = "video";
                        uploadFile(currentFileUri, currentFileType);
                    }
                    break;

                case REQUEST_DOCUMENT_PICK:
                    if (data != null && data.getData() != null) {
                        currentFileUri = data.getData();
                        currentFileType = "document";
                        uploadFile(currentFileUri, currentFileType);
                    }
                    break;
            }
        }
    }

    private void uploadFile(Uri fileUri, String fileType) {
        isUploadCancelled = false;
        showUploadProgress(true);

        String originalFileName = getFileName(fileUri);
        uploadFileName.setText(originalFileName);

        File tempFile = createTempFileFromUri(fileUri);
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
                            Toast.makeText(ChatActivity.this, "Файл загружен", Toast.LENGTH_SHORT).show();
                            finalTempFile.delete();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            showUploadProgress(false);
                            Log.e(TAG, "Ошибка загрузки: " + error);
                            Toast.makeText(ChatActivity.this, "Ошибка загрузки: " + error, Toast.LENGTH_LONG).show();
                            finalTempFile.delete();
                        });
                    }

                    @Override
                    public void onProgress(int progress) {
                        runOnUiThread(() -> updateUploadProgress(progress, finalFileName));
                    }
                });
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

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot == -1) ? "" : fileName.substring(lastDot + 1).toLowerCase();
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
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
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

    private void cancelUpload() {
        isUploadCancelled = true;
        showUploadProgress(false);
        Toast.makeText(this, "Загрузка отменена", Toast.LENGTH_SHORT).show();
    }

    private void navigateToHomeFragment() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("FRAGMENT_TO_LOAD", "home");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void loadUserInfo() {
        DatabaseReference avatarRef = FirebaseDatabase.getInstance()
                .getReference("avatars")
                .child(recipientId);

        avatarRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String avatarUrl = dataSnapshot.getValue(String.class);
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        Glide.with(ChatActivity.this)
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_person)
                                .error(R.drawable.ic_person)
                                .circleCrop()
                                .into(userAvatar);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Ошибка загрузки аватара: " + databaseError.getMessage());
            }
        });

        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(recipientId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    if (recipientName == null) {
                        String name = dataSnapshot.child("username").getValue(String.class);
                        if (name != null) {
                            userName.setText(name);
                            recipientName = name;
                        }
                    }

                    String status = dataSnapshot.child("status").getValue(String.class);
                    if (status != null) {
                        userStatus.setText(status);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Ошибка загрузки информации о пользователе: " + databaseError.getMessage());
            }
        });
    }

    private void updateLastMessageInfo(String lastMessage, long timestamp, String messageType) {
        userChatsRef.child(currentUserId).child(recipientId).child("lastMessage").setValue(lastMessage);
        userChatsRef.child(currentUserId).child(recipientId).child("timestamp").setValue(timestamp);
        userChatsRef.child(currentUserId).child(recipientId).child("lastMessageSenderId").setValue(currentUserId);
        userChatsRef.child(currentUserId).child(recipientId).child("unreadCount").setValue(0);
        userChatsRef.child(currentUserId).child(recipientId).child("messageType").setValue(messageType);

        userChatsRef.child(recipientId).child(currentUserId).child("lastMessage").setValue(lastMessage);
        userChatsRef.child(recipientId).child(currentUserId).child("timestamp").setValue(timestamp);
        userChatsRef.child(recipientId).child(currentUserId).child("lastMessageSenderId").setValue(currentUserId);
        userChatsRef.child(recipientId).child(currentUserId).child("messageType").setValue(messageType);

        userChatsRef.child(recipientId).child(currentUserId).child("unreadCount")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        int currentUnreadCount = 0;
                        if (dataSnapshot.exists()) {
                            Integer count = dataSnapshot.getValue(Integer.class);
                            if (count != null) {
                                currentUnreadCount = count;
                            }
                        }
                        userChatsRef.child(recipientId).child(currentUserId).child("unreadCount").setValue(currentUnreadCount + 1);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        userChatsRef.child(recipientId).child(currentUserId).child("unreadCount").setValue(1);
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
                markSingleMessageAsRead(message);
            }
        }

        updateUnreadCount();
    }

    private void markSingleMessageAsRead(Message message) {
        if (!message.getSenderId().equals(currentUserId) &&
                !message.isReadByUser(currentUserId)) {

            message.markAsRead(currentUserId);

            Map<String, Object> updates = new HashMap<>();
            updates.put("readBy", message.getReadBy());
            updates.put("isRead", message.isRead());

            chatRef.child(message.getId()).updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Сообщение " + message.getId() + " помечено как прочитанное");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Ошибка пометки сообщения как прочитанного", e);
                    });
        }
    }

    private void updateUnreadCount() {
        userChatsRef.child(currentUserId).child(recipientId).child("unreadCount").setValue(0);
    }

    private void openUserProfile() {
        Intent profileIntent = new Intent(this, UserProfileActivity.class);
        profileIntent.putExtra("chatId", chatId);
        profileIntent.putExtra("user_id", recipientId);
        profileIntent.putExtra("user_name", recipientName);
        startActivity(profileIntent);
    }

    private void scrollToBottom() {
        if (messagesList.size() > 0 && messagesRecyclerView != null) {
            messagesRecyclerView.scrollToPosition(messagesList.size() - 1);
        }
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        markMessagesAsRead();
        executorService.shutdown();
    }

    private void loadMessages() {
        showLoading(true);

        chatRef.orderByKey().addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                messagesList.clear();
                messagePositions.clear();

                List<Message> tempMessages = new ArrayList<>();

                for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                    try {
                        String messageId = messageSnapshot.getKey();

                        if (messageId == null || messageId.equals("empty") || messageSnapshot.getValue() instanceof String) {
                            continue;
                        }

                        Message message = messageSnapshot.getValue(Message.class);
                        if (message != null && message.getId() != null) {
                            if (!message.getId().equals(messageId)) {
                                message.setId(messageId);
                            }

                            if (message.getTimestamp() == 0) {
                                message.setTimestamp(System.currentTimeMillis());
                            }

                            tempMessages.add(message);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка парсинга сообщения: " + e.getMessage());
                    }
                }

                Collections.sort(tempMessages, new Comparator<Message>() {
                    @Override
                    public int compare(Message m1, Message m2) {
                        int timeCompare = Long.compare(m1.getTimestamp(), m2.getTimestamp());
                        if (timeCompare != 0) {
                            return timeCompare;
                        }
                        return m1.getId().compareTo(m2.getId());
                    }
                });

                messagesList.addAll(tempMessages);
                updateMessagePositions();
                messagesAdapter.setMessages(messagesList);
                scrollToBottom();
                markMessagesAsRead();
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                showLoading(false);
                Log.e(TAG, "Ошибка загрузки сообщений: " + databaseError.getMessage());
                Toast.makeText(ChatActivity.this, "Ошибка загрузки сообщений", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendTextMessage() {
        String text = messageEditText.getText().toString().trim();

        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "Введите сообщение", Toast.LENGTH_SHORT).show();
            return;
        }

        String messageId = chatRef.push().getKey();
        if (messageId == null) {
            Toast.makeText(this, "Ошибка создания сообщения", Toast.LENGTH_SHORT).show();
            return;
        }

        long timestamp = System.currentTimeMillis();

        Message message = new Message(
                messageId,
                text,
                currentUserId,
                recipientId,
                timestamp,
                chatId,
                "text"
        );

        addNewMessage(message);

        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("id", messageId);
        messageMap.put("text", text);
        messageMap.put("senderId", currentUserId);
        messageMap.put("recipientId", recipientId);
        messageMap.put("timestamp", timestamp);
        messageMap.put("chatId", chatId);
        messageMap.put("messageType", "text");
        messageMap.put("isRead", false);

        chatRef.child(messageId).setValue(messageMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Сообщение отправлено: " + text + " ID: " + messageId);
                    messageEditText.setText("");
                    updateLastMessageInfo(text, timestamp, "text");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ошибка отправки сообщения: " + e.getMessage());
                    Toast.makeText(ChatActivity.this, "Ошибка отправки сообщения", Toast.LENGTH_SHORT).show();
                    removeMessageById(messageId);
                });
    }

    private void addNewMessage(Message message) {
        if (messagePositions.containsKey(message.getId())) {
            updateMessageById(message.getId(), message);
            return;
        }

        int insertPosition = findCorrectInsertPosition(message);
        messagesList.add(insertPosition, message);
        updateMessagePositions();
        messagesAdapter.notifyItemInserted(insertPosition);
    }

    private int findCorrectInsertPosition(Message newMessage) {
        for (int i = 0; i < messagesList.size(); i++) {
            Message currentMessage = messagesList.get(i);

            if (newMessage.getTimestamp() < currentMessage.getTimestamp()) {
                return i;
            } else if (newMessage.getTimestamp() == currentMessage.getTimestamp() &&
                    newMessage.getId().compareTo(currentMessage.getId()) < 0) {
                return i;
            }
        }
        return messagesList.size();
    }

    private void updateMessagePositions() {
        messagePositions.clear();
        for (int i = 0; i < messagesList.size(); i++) {
            Message message = messagesList.get(i);
            messagePositions.put(message.getId(), i);
        }
    }

    private void updateMessageById(String messageId, Message updatedMessage) {
        Integer position = messagePositions.get(messageId);
        if (position != null && position >= 0 && position < messagesList.size()) {
            messagesList.set(position, updatedMessage);
            messagesAdapter.notifyItemChanged(position);
        }
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

    private void sendFileMessage(String messageType, String fileUrl, String fileName) {
        String messageId = chatRef.push().getKey();
        if (messageId == null) {
            Toast.makeText(this, "Ошибка создания сообщения", Toast.LENGTH_SHORT).show();
            return;
        }

        long timestamp = System.currentTimeMillis();

        String messageText = getMessageTextForType(messageType);

        Message message = new Message(
                messageId,
                messageText,
                currentUserId,
                recipientId,
                timestamp,
                chatId,
                messageType
        );

        message.setFileUrl(fileUrl);
        message.setFileName(fileName);
        message.setFileSize(1024000);

        addNewMessage(message);

        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("id", messageId);
        messageMap.put("text", messageText);
        messageMap.put("senderId", currentUserId);
        messageMap.put("recipientId", recipientId);
        messageMap.put("timestamp", timestamp);
        messageMap.put("chatId", chatId);
        messageMap.put("messageType", messageType);
        messageMap.put("fileUrl", fileUrl);
        messageMap.put("fileName", fileName);
        messageMap.put("fileSize", 1024000);
        messageMap.put("isRead", false);

        chatRef.child(messageId).setValue(messageMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Файл отправлен: " + fileUrl);
                    updateLastMessageInfo(messageText, timestamp, messageType);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ошибка отправки файла: " + e.getMessage());
                    Toast.makeText(ChatActivity.this, "Ошибка отправки файла", Toast.LENGTH_SHORT).show();
                    removeMessageById(messageId);
                });
    }

    private String getMessageTextForType(String messageType) {
        switch (messageType) {
            case "image":
                return "📷 Изображение";
            case "video":
                return "🎥 Видео";
            case "document":
                return "📄 Документ";
            default:
                return "📎 Файл";
        }
    }

    // ================ МЕТОДЫ ДЛЯ ВИДЕО ПРЕДПРОСМОТРА ================

    private void loadVideoThumbnail(String videoUrl, VideoThumbnailCallback callback) {
        executorService.execute(() -> {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();

                // Для онлайн видео
                if (videoUrl.startsWith("http")) {
                    retriever.setDataSource(videoUrl, new HashMap<String, String>());
                } else {
                    retriever.setDataSource(videoUrl);
                }

                // Получаем кадр на 1 секунде
                Bitmap bitmap = retriever.getFrameAtTime(1000000); // 1 секунда в микросекундах

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

    private void getVideoDuration(String videoUrl, VideoDurationCallback callback) {
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
                    duration = Long.parseLong(durationStr) / 1000; // конвертируем в секунды
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

    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds);
    }

    private void playVideo(String videoUrl, String videoTitle) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("video_url", videoUrl);
        intent.putExtra("video_title", videoTitle != null ? videoTitle : "Видео");
        startActivity(intent);
    }

    // Интерфейсы для колбэков
    interface VideoThumbnailCallback {
        void onThumbnailLoaded(Bitmap bitmap);
        void onError();
    }

    interface VideoDurationCallback {
        void onDurationLoaded(long duration);
    }

    // ================ ВНУТРЕННИЙ КЛАСС АДАПТЕРА ================

    public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_SENT_TEXT = 1;
        private static final int TYPE_RECEIVED_TEXT = 2;
        private static final int TYPE_SENT_IMAGE = 3;
        private static final int TYPE_RECEIVED_IMAGE = 4;
        private static final int TYPE_SENT_VIDEO = 5;
        private static final int TYPE_RECEIVED_VIDEO = 6;
        private static final int TYPE_SENT_DOCUMENT = 7;
        private static final int TYPE_RECEIVED_DOCUMENT = 8;

        private List<Message> messagesList;
        private String currentUserId;
        private Context context;

        public MessageAdapter(List<Message> messagesList, String currentUserId) {
            this.messagesList = messagesList;
            this.currentUserId = currentUserId;
        }

        public void setMessages(List<Message> messages) {
            this.messagesList = messages;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Message message = messagesList.get(position);
            boolean isSent = message.getSenderId().equals(currentUserId);

            if (message.isTextMessage()) {
                return isSent ? TYPE_SENT_TEXT : TYPE_RECEIVED_TEXT;
            } else if (message.isImageMessage()) {
                return isSent ? TYPE_SENT_IMAGE : TYPE_RECEIVED_IMAGE;
            } else if (message.isVideoMessage()) {
                return isSent ? TYPE_SENT_VIDEO : TYPE_RECEIVED_VIDEO;
            } else if (message.isDocumentMessage()) {
                return isSent ? TYPE_SENT_DOCUMENT : TYPE_RECEIVED_DOCUMENT;
            }
            return isSent ? TYPE_SENT_TEXT : TYPE_RECEIVED_TEXT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            context = parent.getContext();

            switch (viewType) {
                case TYPE_SENT_IMAGE:
                    View sentImageView = inflater.inflate(R.layout.item_image_sent, parent, false);
                    return new SentImageViewHolder(sentImageView);

                case TYPE_RECEIVED_IMAGE:
                    View receivedImageView = inflater.inflate(R.layout.item_image_received, parent, false);
                    return new ReceivedImageViewHolder(receivedImageView);

                case TYPE_SENT_VIDEO:
                    View sentVideoView = inflater.inflate(R.layout.item_video_sent, parent, false);
                    return new SentVideoViewHolder(sentVideoView);

                case TYPE_RECEIVED_VIDEO:
                    View receivedVideoView = inflater.inflate(R.layout.item_video_received, parent, false);
                    return new ReceivedVideoViewHolder(receivedVideoView);

                case TYPE_SENT_TEXT:
                    View sentTextView = inflater.inflate(R.layout.item_message_send, parent, false);
                    return new SentMessageViewHolder(sentTextView);

                case TYPE_RECEIVED_TEXT:
                    View receivedTextView = inflater.inflate(R.layout.item_message_received, parent, false);
                    return new ReceivedMessageViewHolder(receivedTextView);

                default:
                    View defaultView = inflater.inflate(R.layout.item_message_send, parent, false);
                    return new SentMessageViewHolder(defaultView);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Message message = messagesList.get(position);

            if (holder instanceof SentImageViewHolder) {
                ((SentImageViewHolder) holder).bind(message);
            } else if (holder instanceof ReceivedImageViewHolder) {
                ((ReceivedImageViewHolder) holder).bind(message);
            } else if (holder instanceof SentVideoViewHolder) {
                ((SentVideoViewHolder) holder).bind(message);
            } else if (holder instanceof ReceivedVideoViewHolder) {
                ((ReceivedVideoViewHolder) holder).bind(message);
            } else if (holder instanceof SentMessageViewHolder) {
                ((SentMessageViewHolder) holder).bind(message);
            } else if (holder instanceof ReceivedMessageViewHolder) {
                ((ReceivedMessageViewHolder) holder).bind(message);
            }
        }

        @Override
        public int getItemCount() {
            return messagesList.size();
        }

        // ViewHolder для отправленных изображений
        class SentImageViewHolder extends RecyclerView.ViewHolder {
            private ImageView imageMessage;
            private TextView messageTime;
            private ProgressBar imageProgress;

            public SentImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageMessage = itemView.findViewById(R.id.imageMessage);
                messageTime = itemView.findViewById(R.id.messageTime);
                imageProgress = itemView.findViewById(R.id.imageProgress);
            }

            public void bind(Message message) {
                String imageUrl = message.getFileUrl();
                messageTime.setText(formatTime(message.getTimestamp()));

                if (imageUrl != null && !imageUrl.isEmpty()) {
                    imageProgress.setVisibility(View.VISIBLE);

                    Glide.with(context)
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_broken_image)
                            .centerCrop()
                            .into(new CustomTarget<Drawable>() {
                                @Override
                                public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                    imageProgress.setVisibility(View.GONE);
                                    imageMessage.setImageDrawable(resource);
                                }

                                @Override
                                public void onLoadCleared(@Nullable Drawable placeholder) {
                                    imageProgress.setVisibility(View.GONE);
                                }

                                @Override
                                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                    super.onLoadFailed(errorDrawable);
                                    imageProgress.setVisibility(View.GONE);
                                    imageMessage.setImageDrawable(errorDrawable);
                                }
                            });

                    imageMessage.setOnClickListener(v -> {
                        Intent intent = new Intent(context, FullImageActivity.class);
                        intent.putExtra("image_url", imageUrl);
                        context.startActivity(intent);
                    });
                }
            }
        }

        // ViewHolder для полученных изображений
        class ReceivedImageViewHolder extends RecyclerView.ViewHolder {
            private ImageView imageMessage;
            private TextView messageTime;
            private ProgressBar imageProgress;

            public ReceivedImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageMessage = itemView.findViewById(R.id.imageMessage);
                messageTime = itemView.findViewById(R.id.messageTime);
                imageProgress = itemView.findViewById(R.id.imageProgress);
            }

            public void bind(Message message) {
                String imageUrl = message.getFileUrl();
                String filename = message.getFileName();
                messageTime.setText(formatTime(message.getTimestamp()));

                if (imageUrl != null && !imageUrl.isEmpty()) {
                    imageProgress.setVisibility(View.VISIBLE);

                    Glide.with(context)
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_broken_image)
                            .centerCrop()
                            .into(new CustomTarget<Drawable>() {
                                @Override
                                public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                    imageProgress.setVisibility(View.GONE);
                                    imageMessage.setImageDrawable(resource);
                                }

                                @Override
                                public void onLoadCleared(@Nullable Drawable placeholder) {
                                    imageProgress.setVisibility(View.GONE);
                                }

                                @Override
                                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                    super.onLoadFailed(errorDrawable);
                                    imageProgress.setVisibility(View.GONE);
                                    imageMessage.setImageDrawable(errorDrawable);
                                }
                            });

                    imageMessage.setOnClickListener(v -> {
                        Intent intent = new Intent(context, FullImageActivity.class);
                        intent.putExtra("image_url", imageUrl);
                        intent.putExtra("FileName", filename);
                        context.startActivity(intent);
                    });
                }
            }
        }

        // ViewHolder для отправленных видео
        class SentVideoViewHolder extends RecyclerView.ViewHolder {
            private ImageView videoThumbnail;
            private ImageView playButton;
            private TextView videoDuration;
            private TextView messageTime;
            private ProgressBar videoProgress;

            public SentVideoViewHolder(@NonNull View itemView) {
                super(itemView);
                videoThumbnail = itemView.findViewById(R.id.videoThumbnail);
                playButton = itemView.findViewById(R.id.playButton);
                videoDuration = itemView.findViewById(R.id.videoDuration);
                messageTime = itemView.findViewById(R.id.messageTime);
                videoProgress = itemView.findViewById(R.id.videoProgress);
            }

            public void bind(Message message) {
                String videoUrl = message.getFileUrl();
                String fileName = message.getFileName();
                messageTime.setText(formatTime(message.getTimestamp()));

                if (videoUrl != null && !videoUrl.isEmpty()) {
                    videoProgress.setVisibility(View.VISIBLE);

                    // Загружаем превью видео (первый кадр)
                    loadVideoThumbnail(videoUrl, new VideoThumbnailCallback() {
                        @Override
                        public void onThumbnailLoaded(Bitmap bitmap) {
                            videoProgress.setVisibility(View.GONE);
                            videoThumbnail.setImageBitmap(bitmap);

                            // Получаем длительность видео
                            getVideoDuration(videoUrl, duration -> {
                                if (duration > 0) {
                                    videoDuration.setText(formatDuration(duration));
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
                    playButton.setOnClickListener(v -> {
                        playVideo(videoUrl, fileName);
                    });

                    // Клик по превью
                    videoThumbnail.setOnClickListener(v -> {
                        playVideo(videoUrl, fileName);
                    });
                }
            }
        }

        // ViewHolder для полученных видео
        class ReceivedVideoViewHolder extends RecyclerView.ViewHolder {
            private ImageView videoThumbnail;
            private ImageView playButton;
            private TextView videoDuration;
            private TextView messageTime;
            private ProgressBar videoProgress;

            public ReceivedVideoViewHolder(@NonNull View itemView) {
                super(itemView);
                videoThumbnail = itemView.findViewById(R.id.videoThumbnail);
                playButton = itemView.findViewById(R.id.playButton);
                videoDuration = itemView.findViewById(R.id.videoDuration);
                messageTime = itemView.findViewById(R.id.messageTime);
                videoProgress = itemView.findViewById(R.id.videoProgress);
            }

            public void bind(Message message) {
                String videoUrl = message.getFileUrl();
                String fileName = message.getFileName();
                messageTime.setText(formatTime(message.getTimestamp()));

                if (videoUrl != null && !videoUrl.isEmpty()) {
                    videoProgress.setVisibility(View.VISIBLE);

                    // Загружаем превью видео (первый кадр)
                    loadVideoThumbnail(videoUrl, new VideoThumbnailCallback() {
                        @Override
                        public void onThumbnailLoaded(Bitmap bitmap) {
                            videoProgress.setVisibility(View.GONE);
                            videoThumbnail.setImageBitmap(bitmap);

                            // Получаем длительность видео
                            getVideoDuration(videoUrl, duration -> {
                                if (duration > 0) {
                                    videoDuration.setText(formatDuration(duration));
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
                    playButton.setOnClickListener(v -> {
                        playVideo(videoUrl, fileName);
                    });

                    // Клик по превью
                    videoThumbnail.setOnClickListener(v -> {
                        playVideo(videoUrl, fileName);
                    });
                }
            }
        }

        // ViewHolder для отправленных текстовых сообщений
        class SentMessageViewHolder extends RecyclerView.ViewHolder {
            private TextView messageText;
            private TextView messageTime;
            private LinearLayout messageLayout;

            public SentMessageViewHolder(@NonNull View itemView) {
                super(itemView);
                messageText = itemView.findViewById(R.id.messageText);
                messageTime = itemView.findViewById(R.id.messageTime);
                messageLayout = itemView.findViewById(R.id.messageLayout);

                messageLayout.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        Message message = messagesList.get(position);
                    }
                });
            }

            public void bind(Message message) {
                if (message.isTextMessage()) {
                    messageText.setText(message.getText());
                } else if (message.isImageMessage()) {
                    messageText.setText("🖼️ Изображение");
                } else if (message.isVideoMessage()) {
                    messageText.setText("🎥 Видео");
                } else if (message.isDocumentMessage()) {
                    String fileName = message.getFileName();
                    messageText.setText("📄 " + (fileName != null ? fileName : "Документ"));
                }
                messageTime.setText(formatTime(message.getTimestamp()));
            }
        }

        // ViewHolder для полученных текстовых сообщений
        class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
            private TextView messageText;
            private TextView messageTime;
            private LinearLayout messageLayout;

            public ReceivedMessageViewHolder(@NonNull View itemView) {
                super(itemView);
                messageText = itemView.findViewById(R.id.messageText);
                messageTime = itemView.findViewById(R.id.messageTime);
                messageLayout = itemView.findViewById(R.id.messageLayout);

                messageLayout.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && position < messagesList.size()) {
                        Message message = messagesList.get(position);

                        if (message.isImageMessage() || message.isVideoMessage() || message.isDocumentMessage()) {
                            String fileUrl = message.getFileUrl();
                            String fileName = message.getFileName();
                            String messageType = message.getMessageType();

                            if (fileUrl != null && !fileUrl.isEmpty()) {
                                downloadFile(fileUrl, fileName, messageType);
                            } else {
                                Toast.makeText(itemView.getContext(), "Файл не найден", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
            }

            public void bind(Message message) {
                if (message.isTextMessage()) {
                    messageText.setText(message.getText());
                } else if (message.isImageMessage()) {
                    messageText.setText("🖼️ Изображение");
                } else if (message.isVideoMessage()) {
                    messageText.setText("🎥 Видео");
                } else if (message.isDocumentMessage()) {
                    String fileName = message.getFileName();
                    messageText.setText("📄 " + (fileName != null ? fileName : "Документ"));
                }
                messageTime.setText(formatTime(message.getTimestamp()));
            }
        }

        // Функция для форматирования даты
        private String formatTime(long timestamp) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            } catch (Exception e) {
                return "";
            }
        }
    }

    // ================ МЕТОДЫ ДЛЯ СКАЧИВАНИЯ ФАЙЛОВ ================

    private String getAlbumFolderForFileType(String fileType) {
        switch (fileType) {
            case "image":
                return "Фото";
            case "video":
                return "Видео";
            case "document":
                return "Документы";
            default:
                return "Другие файлы";
        }
    }

    private void downloadFile(String fileUrl, String fileName, String fileType) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            Toast.makeText(this, "Ошибка: неверная ссылка на файл", Toast.LENGTH_SHORT).show();
            return;
        }

        if (fileName == null || fileName.isEmpty()) {
            String extension = getFileExtensionFromUrl(fileUrl);
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            fileName = "file_" + timeStamp + (extension.isEmpty() ? "" : "." + extension);
        }

        String albumFolder = getAlbumFolderForFileType(fileType);

        showDownloadProgress(true, fileName);

        final String finalFileName = fileName;
        final String finalAlbumFolder = albumFolder;
        final String finalFileUrl = fileUrl;
        final boolean isVideo = "video".equals(fileType) || isVideoFile(getFileExtension(fileName));

        new Thread(() -> {
            try {
                URL url = new URL(finalFileUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                int fileLength = connection.getContentLength();

                File albumDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "Telegram/" + finalAlbumFolder);
                if (!albumDir.exists()) {
                    albumDir.mkdirs();
                }

                File outputFile = new File(albumDir, finalFileName);

                if (outputFile.exists()) {
                    runOnUiThread(() -> {
                        showDownloadProgress(false, null);
                        Toast.makeText(ChatActivity.this, "Файл уже существует: " + outputFile.getName(), Toast.LENGTH_LONG).show();

                        // Если это видео, предлагаем открыть
                        if (isVideo) {
                            openDownloadedFile(outputFile);
                        }
                    });
                    return;
                }

                InputStream input = connection.getInputStream();
                FileOutputStream output = new FileOutputStream(outputFile);

                byte[] buffer = new byte[4096];
                int read;
                long total = 0;
                int progress = 0;

                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    total += read;

                    if (fileLength > 0) {
                        int newProgress = (int) (total * 100 / fileLength);
                        if (newProgress > progress) {
                            progress = newProgress;
                            final int finalProgress = progress;
                            runOnUiThread(() -> updateDownloadProgress(finalProgress, finalFileName));
                        }
                    }
                }

                output.flush();
                output.close();
                input.close();

                scanFileToGallery(outputFile);

                runOnUiThread(() -> {
                    showDownloadProgress(false, null);
                    String message = String.format("Файл сохранен в:\nTelegram/%s/\n%s",
                            finalAlbumFolder, outputFile.getName());

                    if (isVideo) {
                        // Для видео показываем диалог с возможностью открыть в плеере
                        showVideoDownloadSuccessDialog(outputFile, finalAlbumFolder, message);
                    } else {
                        showDownloadSuccessNotification(outputFile, finalAlbumFolder, message);
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    showDownloadProgress(false, null);
                    Toast.makeText(ChatActivity.this, "Ошибка скачивания: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // Добавьте диалог для видео
    private void showVideoDownloadSuccessDialog(File file, String albumName, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("✅ Видео загружено")
                .setMessage(message)
                .setPositiveButton("▶ Воспроизвести", (dialog, which) -> {
                    playVideo(Uri.fromFile(file).toString(), file.getName());
                })
                .setNeutralButton("📁 Открыть папку", (dialog, which) -> {
                    openAlbumFolder(albumName);
                })
                .setNegativeButton("OK", null)
                .show();
    }

    private void showDownloadSuccessNotification(File file, String albumName, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("✅ Скачивание завершено")
                .setMessage(message)
                .setPositiveButton("Открыть", (dialog, which) -> openDownloadedFile(file))
                .setNegativeButton("OK", null)
                .show();
    }

    private void openAlbumFolder(String albumName) {
        try {
            File albumDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "MyTelegram/" + albumName);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(albumDir.getAbsolutePath());
            intent.setDataAndType(uri, "resource/folder");

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "Не найдено приложение для просмотра папок", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка открытия папки", Toast.LENGTH_SHORT).show();
        }
    }

    private void scanFileToGallery(File file) {
        try {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(file);
            mediaScanIntent.setData(contentUri);
            sendBroadcast(mediaScanIntent);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка сканирования файла в галерею: " + e.getMessage());
        }
    }

    private void showDownloadProgress(boolean show, String fileName) {
        if (uploadProgressLayout != null) {
            if (show) {
                uploadProgressLayout.setVisibility(View.VISIBLE);
                uploadFileName.setText(fileName != null ? fileName : "Скачивание...");
                uploadProgressText.setText("0%");
                uploadProgressBar.setProgress(0);
            } else {
                uploadProgressLayout.setVisibility(View.GONE);
            }
        }
    }

    private void updateDownloadProgress(int progress, String fileName) {
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

        // Проверяем, является ли файл видео
        if (isVideoFile(extension)) {
            // Для видео открываем VideoPlayerActivity
            playVideo(Uri.fromFile(file).toString(), fileName);
        } else {
            // Для остальных файлов используем Intent.ACTION_VIEW
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String mimeType = getMimeType(fileName);

            Uri fileUri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    file);

            intent.setDataAndType(fileUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Добавьте метод для проверки видеофайлов
    private boolean isVideoFile(String extension) {
        switch (extension) {
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
            // Изображения
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

            // Видео
            case "mp4":
                return "video/mp4";
            case "avi":
                return "video/x-msvideo";
            case "mkv":
                return "video/x-matroska";
            case "mov":
                return "video/quicktime";
            case "wmv":
                return "video/x-ms-wmv";
            case "flv":
                return "video/x-flv";
            case "webm":
                return "video/webm";
            case "3gp":
                return "video/3gpp";
            case "m4v":
                return "video/x-m4v";

            // Аудио
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "ogg":
                return "audio/ogg";
            case "aac":
                return "audio/aac";
            case "m4a":
                return "audio/mp4";

            // Документы
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
            case "rtf":
                return "application/rtf";
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";

            default:
                return "*/*";
        }
    }
}