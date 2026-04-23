package com.example.mytelegram;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.view.inputmethod.InputMethodManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import android.webkit.MimeTypeMap;
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

        getIntentData();
        initFirebase();
        initViews();
        setupRecyclerView();
        setupClickListeners();
        loadUserInfo();
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

        chatRef = FirebaseDatabase.getInstance().getReference()
                .child("chats")
                .child(chatId)
                .child("messages");

        userChatsRef = FirebaseDatabase.getInstance().getReference()
                .child("userChats");

        Log.d(TAG, "Current User ID: " + currentUserId);
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        userAvatar = findViewById(R.id.userAvatar);
        userName = findViewById(R.id.userName);
        userStatus = findViewById(R.id.userStatus);

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

        // Инициализация BottomSheet
        bottomSheet = findViewById(R.id.bottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        // ВАЖНО: Настройка для скрытия панели
        bottomSheetBehavior.setHideable(true);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        // Устанавливаем высоту в свернутом состоянии
        bottomSheetBehavior.setPeekHeight(1000);

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        Log.d(TAG, "BottomSheet: скрыта");
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED:
                        Log.d(TAG, "BottomSheet: развернута");
                        break;
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        Log.d(TAG, "BottomSheet: свернута");
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // Анимация слайда (можно использовать для затемнения фона)
            }
        });

        // Инициализация элементов медиапанели
        mediaPanelLayout = findViewById(R.id.mediaPanelLayout);
        mediaTabs = findViewById(R.id.mediaTabs);
        mediaViewPager = findViewById(R.id.mediaViewPager);
        closeMediaPanelButton = findViewById(R.id.closeMediaPanelButton);

        // Настройка ViewPager с табами
        if (mediaViewPager != null && mediaTabs != null) {
            MediaPagerAdapter pagerAdapter = new MediaPagerAdapter(this);
            mediaViewPager.setAdapter(pagerAdapter);

            new com.google.android.material.tabs.TabLayoutMediator(
                    mediaTabs,
                    mediaViewPager,
                    (tab, position) -> tab.setText(position == 0 ? "Галерея" : "Документы")
            ).attach();
        }

        messagesList = new ArrayList<>();
        messagePositions = new HashMap<>();

        if (recipientName != null) {
            userName.setText(recipientName);
        } else {
            userName.setText("Пользователь");
        }
    }


// Длок для эксперементов





    private void toggleMediaPanel() {
        if (mediaPanelLayout.getVisibility() == View.VISIBLE) {
            closeMediaPanel();
        } else {
            openMediaPanel();
        }
    }

    private void openMediaPanel() {
        mediaPanelLayout.setVisibility(View.VISIBLE);
        // Скрываем клавиатуру если открыта
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(messageEditText.getWindowToken(), 0);
    }

    public void closeMediaPanel() {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            // Сбрасываем peekHeight на случай если он изменился
            bottomSheetBehavior.setPeekHeight(1000);
        }
    }

    // Метод для отправки медиа из галереи
    public void sendMediaFromGallery(Uri uri, String type) {
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

    // Метод для отправки документа
    public void sendDocumentFromPicker(Uri uri) {
        closeMediaPanel();
        currentFileUri = uri;
        currentFileType = "document";
        uploadFile(uri, "document");
    }




    private File createCompressedImageFile(Uri uri) {
        try {
            // Сначала получаем размеры изображения без загрузки всего bitmap
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            InputStream inputStream = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            // Вычисляем коэффициент сжатия
            int maxSize = 1920;
            int scale = 1;

            if (options.outWidth > maxSize || options.outHeight > maxSize) {
                scale = (int) Math.pow(2, (int) Math.round(
                        Math.log(maxSize / (double) Math.max(options.outWidth, options.outHeight)) / Math.log(0.5)));
            }

            // Загружаем сжатое изображение
            options.inJustDecodeBounds = false;
            options.inSampleSize = scale;

            inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            if (bitmap == null) return null;

            // Дополнительно сжимаем если всё ещё большое
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

            // Сохраняем сжатое изображение
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










// _______________________________________________________________________



















    private void updateEditedMessage() {
    if (editingMessageId == null || editingMessage == null) {
        return;
    }

    String newText = messageEditText.getText().toString().trim();

    if (TextUtils.isEmpty(newText)) {
        Toast.makeText(ChatActivity.this, "Сообщение не может быть пустым", Toast.LENGTH_SHORT).show();
        return;
    }

    if (newText.equals(editingMessage.getText())) {
        // Текст не изменился - просто выходим из режима редактирования
        cancelEditing();
        return;
    }

    // Показываем прогресс
    showLoading(true);

    Map<String, Object> updates = new HashMap<>();
    updates.put("text", newText);
    updates.put("edited", true);
    updates.put("editedAt", ServerValue.TIMESTAMP);

    chatRef.child(editingMessageId).updateChildren(updates)
            .addOnSuccessListener(aVoid -> {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(ChatActivity.this, "Сообщение изменено", Toast.LENGTH_SHORT).show();
                    cancelEditing();
                });
            })
            .addOnFailureListener(e -> {
                runOnUiThread(() -> {
                    showLoading(false);
                    Log.e(TAG, "Ошибка изменения сообщения: " + e.getMessage());
                    Toast.makeText(ChatActivity.this, "Ошибка изменения сообщения", Toast.LENGTH_SHORT).show();
                });
            });
}

    private void cancelEditing() {
        editingMessageId = null;
        editingMessage = null;

        // Очищаем поле ввода
        messageEditText.setText("");

        // Скрываем панель редактирования
        if (editMessageLayout != null) {
            editMessageLayout.setVisibility(View.GONE);
        }

        // Возвращаем обычную иконку отправки
        if (sendButton != null) {
            sendButton.setImageResource(R.drawable.ic_send);
        }

        // Убираем фокус с поля ввода
        messageEditText.clearFocus();
    }

    private void scrollToMessage(Message message) {
        Integer position = messagePositions.get(message.getId());
        if (position != null && position >= 0) {
            messagesRecyclerView.scrollToPosition(position);
        }
    }

    private void setupClickListeners() {
        // Кнопка назад
        backButton.setOnClickListener(v -> navigateToHomeFragment());

        // Открытие профиля
        View topBar = findViewById(R.id.topBar);
        if (topBar != null) {
            topBar.setOnClickListener(v -> openUserProfile());
        }
        userAvatar.setOnClickListener(v -> openUserProfile());
        userName.setOnClickListener(v -> openUserProfile());

        // Кнопка прикрепления (скрепка) - открывает/закрывает BottomSheet
        photoButton.setOnClickListener(v -> {
            Log.d(TAG, "Photo button clicked, bottomSheet state: " + bottomSheetBehavior.getState());

            // Проверяем состояние панели
            int currentState = bottomSheetBehavior.getState();

            if (currentState == BottomSheetBehavior.STATE_HIDDEN) {
                // Убеждаемся, что панель можно показать
                bottomSheetBehavior.setHideable(false); // Временно запрещаем скрытие
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                bottomSheetBehavior.setHideable(true); // Возвращаем возможность скрытия

                // Скрываем клавиатуру
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(messageEditText.getWindowToken(), 0);
                }
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });

        // Кнопка отправки сообщения
        sendButton.setOnClickListener(v -> {
            if (editingMessageId != null && !editingMessageId.isEmpty()) {
                updateEditedMessage();
            } else {
                sendTextMessage();
            }
        });

        // Кнопка отмены загрузки
        cancelUploadButton.setOnClickListener(v -> cancelUpload());

        // Кнопка отмены редактирования
        if (cancelEditButton != null) {
            cancelEditButton.setOnClickListener(v -> cancelEditing());
        }

        // Кнопка закрытия медиапанели
        if (closeMediaPanelButton != null) {
            closeMediaPanelButton.setOnClickListener(v ->
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
            );
        }

        // Клик по полю ввода - прокрутка вниз
        messageEditText.setOnClickListener(v -> scrollToBottom());
    }


    private void toggleBottomSheet() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
            showBottomSheet();
        } else {
            hideBottomSheet();
        }
    }

    private void showBottomSheet() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // Скрываем клавиатуру
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(messageEditText.getWindowToken(), 0);
        }
    }

    private void hideBottomSheet() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }



    // Остальные методы...





    private void setupMediaPanel() {
        // Проверяем что View инициализированы
        if (mediaViewPager == null || mediaTabs == null) {
            Log.e(TAG, "mediaViewPager or mediaTabs is null");
            return;
        }

        MediaPagerAdapter pagerAdapter = new MediaPagerAdapter(this);
        mediaViewPager.setAdapter(pagerAdapter);

        new com.google.android.material.tabs.TabLayoutMediator(
                mediaTabs,
                mediaViewPager,
                (tab, position) -> {
                    if (position == 0) {
                        tab.setText("Галерея");
                    } else {
                        tab.setText("Документы");
                    }
                }
        ).attach();
    }

    // Диалог удаления сообщения
    private void showDeleteMessageDialog(Message message) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить сообщение")
                .setMessage("Вы уверены, что хотите удалить это сообщение?")
                .setPositiveButton("Удалить", (dialog, which) -> deleteMessage(message))
                .setNegativeButton("Отмена", null)
                .show();
    }

    // Удаление сообщения
    private void deleteMessage(Message message) {
        String messageId = message.getId();

        chatRef.child(messageId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Сообщение удалено", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ошибка удаления сообщения: " + e.getMessage());
                    Toast.makeText(this, "Ошибка удаления сообщения", Toast.LENGTH_SHORT).show();
                });
    }

    // Диалог редактирования сообщения
    private void showEditMessageDialog(Message message) {
        // Вместо диалога используем режим редактирования в поле ввода
        editingMessageId = message.getId();
        editingMessage = message;

        // Устанавливаем текст в поле ввода
        messageEditText.setText(message.getText());
        messageEditText.setSelection(message.getText().length());

        // Показываем панель редактирования
        editMessageLayout.setVisibility(View.VISIBLE);
        editMessageLabel.setText("Редактирование сообщения");

        // Меняем иконку кнопки отправки (опционально)
        sendButton.setImageResource(R.drawable.ic_check);

        // Фокусируемся на поле ввода
        messageEditText.requestFocus();

        // Прокручиваем к сообщению, которое редактируем
        scrollToMessage(message);
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

    public void pickDocument() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_DOCUMENT_PICK);
    }

    private void resetBottomSheet() {
        if (bottomSheetBehavior != null) {
            // Сбрасываем состояние
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            bottomSheetBehavior.setHideable(true);

            // Небольшая задержка для гарантии
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                bottomSheetBehavior.setPeekHeight(1000);
            }, 100);
        }
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

            resetBottomSheet();
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
                            Toast.makeText(ChatActivity.this, "Файл загружен", Toast.LENGTH_SHORT).show();
                            // Удаляем временный файл
                            if (finalTempFile.exists()) {
                                finalTempFile.delete();
                                resetBottomSheet();
                            }

                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            showUploadProgress(false);
                            Log.e(TAG, "Ошибка загрузки: " + error);
                            Toast.makeText(ChatActivity.this, "Ошибка загрузки: " + error, Toast.LENGTH_LONG).show();
                            // Удаляем временный файл
                            if (finalTempFile.exists()) {
                                finalTempFile.delete();
                                resetBottomSheet();

                            }
                        });
                    }

                    @Override
                    public void onProgress(int progress) {
                        runOnUiThread(() -> {
                            if (!isUploadCancelled) {
                                updateUploadProgress(progress, finalFileName);
                                resetBottomSheet();
                            }
                        });
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

    private void updateLastMessageInfo(String lastMessage, String messageType) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", lastMessage);
        updates.put("timestamp", ServerValue.TIMESTAMP);
        updates.put("lastMessageSenderId", currentUserId);
        updates.put("messageType", messageType);

        userChatsRef.child(currentUserId).child(recipientId).updateChildren(updates);

        // Для получателя увеличиваем unreadCount
        userChatsRef.child(recipientId).child(currentUserId).child("lastMessage").setValue(lastMessage);
        userChatsRef.child(recipientId).child(currentUserId).child("timestamp").setValue(ServerValue.TIMESTAMP);
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
                        userChatsRef.child(recipientId).child(currentUserId).child("unreadCount")
                                .setValue(currentUnreadCount + 1);
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

        chatRef.orderByChild("timestamp").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                messagesList.clear();
                messagePositions.clear();

                for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                    try {
                        String messageId = messageSnapshot.getKey();
                        if (messageId == null || messageId.equals("empty")) {
                            continue;
                        }

                        Message message = new Message();
                        message.setId(messageId);

                        // Используем getValue() для автоматического маппинга
                        Map<String, Object> messageData = (Map<String, Object>) messageSnapshot.getValue();
                        if (messageData == null) continue;

                        if (messageData.containsKey("text")) {
                            message.setText((String) messageData.get("text"));
                        }
                        if (messageData.containsKey("senderId")) {
                            message.setSenderId((String) messageData.get("senderId"));
                        }
                        if (messageData.containsKey("recipientId")) {
                            message.setRecipientId((String) messageData.get("recipientId"));
                        }
                        if (messageData.containsKey("timestamp")) {
                            Object timestampObj = messageData.get("timestamp");
                            if (timestampObj instanceof Long) {
                                message.setTimestamp((Long) timestampObj);
                            } else if (timestampObj instanceof Integer) {
                                message.setTimestamp(((Integer) timestampObj).longValue());
                            } else {
                                // Если timestamp не число, используем текущее время
                                message.setTimestamp(System.currentTimeMillis());
                            }
                        }
                        if (messageData.containsKey("chatId")) {
                            message.setChatId((String) messageData.get("chatId"));
                        }
                        if (messageData.containsKey("messageType")) {
                            message.setMessageType((String) messageData.get("messageType"));
                        } else {
                            message.setMessageType("text");
                        }
                        if (messageData.containsKey("fileUrl")) {
                            message.setFileUrl((String) messageData.get("fileUrl"));
                        }
                        if (messageData.containsKey("fileName")) {
                            message.setFileName((String) messageData.get("fileName"));
                        }
                        if (messageData.containsKey("edited")) {
                            Object editedObj = messageData.get("edited");
                            message.setEdited(editedObj instanceof Boolean ? (Boolean) editedObj : false);
                        }

                        messagesList.add(message);

                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка парсинга сообщения: " + e.getMessage(), e);
                    }
                }

                // Сортируем по timestamp (старые -> новые)
                Collections.sort(messagesList, (m1, m2) -> Long.compare(m1.getTimestamp(), m2.getTimestamp()));

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

        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("id", messageId);
        messageMap.put("text", text);
        messageMap.put("senderId", currentUserId);
        messageMap.put("recipientId", recipientId);
        messageMap.put("timestamp", ServerValue.TIMESTAMP);
        messageMap.put("chatId", chatId);
        messageMap.put("messageType", "text");
        messageMap.put("isRead", false);
        messageMap.put("readBy", new HashMap<String, Boolean>());
        messageMap.put("edited", false);

        long tempTimestamp = System.currentTimeMillis();
        Message message = new Message(
                messageId,
                text,
                currentUserId,
                recipientId,
                tempTimestamp,
                chatId,
                "text"
        );

        addNewMessage(message);

        chatRef.child(messageId).setValue(messageMap)
                .addOnSuccessListener(aVoid -> {
                    messageEditText.setText("");
                    updateLastMessageInfo(text, "text");
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
        messageMap.put("timestamp", ServerValue.TIMESTAMP);
        messageMap.put("chatId", chatId);
        messageMap.put("messageType", messageType);
        messageMap.put("fileUrl", fileUrl);
        messageMap.put("fileName", fileName);
        messageMap.put("fileSize", 1024000);
        messageMap.put("isRead", false);
        messageMap.put("edited", false);

        chatRef.child(messageId).setValue(messageMap)
                .addOnSuccessListener(aVoid -> {
                    updateLastMessageInfo(messageText,  messageType);
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

    private Bitmap getRoundedCornerBitmap(Bitmap bitmap, int radius) {
        if (bitmap == null) return null;

        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        RectF rect = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        canvas.drawRoundRect(rect, radius, radius, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, 0, 0, paint);

        return output;
    }

    interface VideoThumbnailCallback {
        void onThumbnailLoaded(Bitmap bitmap);
        void onError();
    }

    interface VideoDurationCallback {
        void onDurationLoaded(long duration);
    }

    // ================ МЕТОДЫ ДЛЯ СКАЧИВАНИЯ ФАЙЛОВ ================

    private void downloadDocument(String fileUrl, String fileName) {
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

        showDocumentDownloadProgress(true, finalFileName);

        String objectKey = extractObjectKeyFromUrl(fileUrl);

        YandexCloudDownloader downloader = new YandexCloudDownloader(this);

        downloader.setDownloadListener(new YandexCloudDownloader.DownloadListener() {
            @Override
            public void onProgress(int progress) {}

            @Override
            public void onSuccess(File file) {
                runOnUiThread(() -> {
                    showDocumentDownloadProgress(false, null);
                    Toast.makeText(ChatActivity.this,
                            "✅ Документ сохранен: " + file.getName(),
                            Toast.LENGTH_LONG).show();
                    showOpenDocumentDialog(file);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showDocumentDownloadProgress(false, null);
                    Toast.makeText(ChatActivity.this,
                            "❌ Ошибка скачивания: " + error,
                            Toast.LENGTH_LONG).show();
                });
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

    private void showDocumentDownloadProgress(boolean show, String fileName) {
        if (uploadProgressLayout != null) {
            if (show) {
                uploadProgressLayout.setVisibility(View.VISIBLE);
                uploadFileName.setText(fileName != null ? fileName : "Скачивание документа...");
                uploadProgressText.setText("0%");
                uploadProgressBar.setProgress(0);
                cancelUploadButton.setVisibility(View.VISIBLE);
                cancelUploadButton.setOnClickListener(v -> cancelDocumentDownload());
            } else {
                uploadProgressLayout.setVisibility(View.GONE);
            }
        }
    }

    private void cancelDocumentDownload() {
        Toast.makeText(this, "Отмена скачивания...", Toast.LENGTH_SHORT).show();
        showDocumentDownloadProgress(false, null);
    }

    private void showOpenDocumentDialog(File file) {
        new AlertDialog.Builder(this)
                .setTitle("📄 Документ скачан")
                .setMessage("Файл: " + file.getName() + "\n\nХотите открыть?")
                .setPositiveButton("Открыть", (dialog, which) -> openDownloadedFile(file))
                .setNeutralButton("📁 Показать в папке", (dialog, which) -> showFileInFolder(file))
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showFileInFolder(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    file);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                intent.setDataAndType(uri, "*/*");
            } else {
                intent.setDataAndType(uri, "resource/folder");
            }

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "Не найдено приложение для просмотра файлов", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка открытия папки: " + e.getMessage());
            Toast.makeText(this, "Не удалось открыть папку", Toast.LENGTH_SHORT).show();
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

        if (isVideoFile(extension)) {
            playVideo(Uri.fromFile(file).toString(), fileName);
        } else {
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
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "mp4":
                return "video/mp4";
            case "pdf":
                return "application/pdf";
            default:
                return "*/*";
        }
    }

    interface OnMessageActionListener {
        void onMessageEdit(Message message);
        void onMessageDelete(Message message);
    }


    public void setupRecyclerView() {
        messagesAdapter = new MessageAdapter(messagesList, currentUserId);

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

    private class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
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

        // Интерфейс для обработки действий с сообщениями (без public)


        private OnMessageActionListener actionListener;

        public MessageAdapter(List<Message> messagesList, String currentUserId) {
            this.messagesList = messagesList;
            this.currentUserId = currentUserId;
        }

        public void setOnMessageActionListener(OnMessageActionListener listener) {
            this.actionListener = listener;
        }

        public void setMessages(List<Message> messages) {
            this.messagesList = messages;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Message message = messagesList.get(position);

            if (currentUserId == null || message.getSenderId() == null) {
                return TYPE_RECEIVED_TEXT;
            }

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
                    return new SentImageViewHolder(inflater.inflate(R.layout.item_image_sent, parent, false));
                case TYPE_RECEIVED_IMAGE:
                    return new ReceivedImageViewHolder(inflater.inflate(R.layout.item_image_received, parent, false));
                case TYPE_SENT_VIDEO:
                    return new SentVideoViewHolder(inflater.inflate(R.layout.item_video_sent, parent, false));
                case TYPE_RECEIVED_VIDEO:
                    return new ReceivedVideoViewHolder(inflater.inflate(R.layout.item_video_received, parent, false));
                case TYPE_SENT_TEXT:
                    return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_send, parent, false));
                case TYPE_RECEIVED_TEXT:
                    return new ReceivedMessageViewHolder(inflater.inflate(R.layout.item_message_received, parent, false));
                case TYPE_SENT_DOCUMENT:
                    return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_send, parent, false));
                case TYPE_RECEIVED_DOCUMENT:
                    return new ReceivedMessageViewHolder(inflater.inflate(R.layout.item_message_received, parent, false));
                default:
                    return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_send, parent, false));
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

        // Общий метод для показа меню
        private void showMessageOptionsDialog(Message message, View anchorView, boolean canEdit) {
            PopupMenu popup = new PopupMenu(context, anchorView, Gravity.END);
            popup.inflate(R.menu.message_context_menu);

            boolean isMyMessage = message.getSenderId().equals(currentUserId);

            popup.getMenu().findItem(R.id.action_edit).setVisible(canEdit && isMyMessage);
            popup.getMenu().findItem(R.id.action_delete).setVisible(isMyMessage);

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_edit) {
                    if (actionListener != null) {
                        actionListener.onMessageEdit(message);
                    }
                    return true;
                } else if (itemId == R.id.action_delete) {
                    if (actionListener != null) {
                        actionListener.onMessageDelete(message);
                    }
                    return true;
                }
                return false;
            });

            popup.show();
        }

        private String formatTime(long timestamp) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            } catch (Exception e) {
                return "";
            }
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

                // Очищаем ImageView
                imageMessage.setImageDrawable(null);

                itemView.setOnLongClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && actionListener != null) {
                        showMessageOptionsDialog(message, v, false);
                        return true;
                    }
                    return false;
                });

                if (imageUrl != null && !imageUrl.isEmpty()) {
                    imageProgress.setVisibility(View.VISIBLE);

                    Glide.with(context)
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_broken_image)
                            .override(800, 800) // ВАЖНО: Ограничиваем размер изображения!
                            .centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .skipMemoryCache(false)
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

                // Очищаем ImageView
                imageMessage.setImageDrawable(null);

                itemView.setOnLongClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && actionListener != null) {
                        showMessageOptionsDialog(message, v, false);
                        return true;
                    }
                    return false;
                });

                if (imageUrl != null && !imageUrl.isEmpty()) {
                    imageProgress.setVisibility(View.VISIBLE);

                    Glide.with(context)
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_broken_image)
                            .override(800, 800) // ВАЖНО: Ограничиваем размер изображения!
                            .centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .skipMemoryCache(false)
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

                itemView.setOnLongClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && actionListener != null) {
                        showMessageOptionsDialog(message, v, false);
                        return true;
                    }
                    return false;
                });

                if (videoUrl != null && !videoUrl.isEmpty()) {
                    videoProgress.setVisibility(View.VISIBLE);

                    // Используем методы внешнего класса ChatActivity
                    ChatActivity.this.loadVideoThumbnail(videoUrl, new VideoThumbnailCallback() {
                        @Override
                        public void onThumbnailLoaded(Bitmap bitmap) {
                            videoProgress.setVisibility(View.GONE);

                            int radius = 48;
                            Bitmap roundedBitmap = getRoundedCornerBitmap(bitmap, radius);

                            if (roundedBitmap != null) {
                                videoThumbnail.setImageBitmap(roundedBitmap);
                            } else {
                                videoThumbnail.setImageBitmap(bitmap);
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                videoThumbnail.setClipToOutline(true);
                                videoThumbnail.setOutlineProvider(new ViewOutlineProvider() {
                                    @Override
                                    public void getOutline(View view, Outline outline) {
                                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                                    }
                                });
                            }

                            ChatActivity.this.getVideoDuration(videoUrl, duration -> {
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

                    playButton.setOnClickListener(v -> ChatActivity.this.playVideo(videoUrl, fileName));
                    videoThumbnail.setOnClickListener(v -> ChatActivity.this.playVideo(videoUrl, fileName));
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

                itemView.setOnLongClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && actionListener != null) {
                        showMessageOptionsDialog(message, v, false);
                        return true;
                    }
                    return false;
                });

                if (videoUrl != null && !videoUrl.isEmpty()) {
                    videoProgress.setVisibility(View.VISIBLE);

                    ChatActivity.this.loadVideoThumbnail(videoUrl, new VideoThumbnailCallback() {
                        @Override
                        public void onThumbnailLoaded(Bitmap bitmap) {
                            videoProgress.setVisibility(View.GONE);

                            int radius = 48;
                            Bitmap roundedBitmap = getRoundedCornerBitmap(bitmap, radius);

                            if (roundedBitmap != null) {
                                videoThumbnail.setImageBitmap(roundedBitmap);
                            } else {
                                videoThumbnail.setImageBitmap(bitmap);
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                videoThumbnail.setClipToOutline(true);
                                videoThumbnail.setOutlineProvider(new ViewOutlineProvider() {
                                    @Override
                                    public void getOutline(View view, Outline outline) {
                                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                                    }
                                });
                            }

                            ChatActivity.this.getVideoDuration(videoUrl, duration -> {
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

                    playButton.setOnClickListener(v -> ChatActivity.this.playVideo(videoUrl, fileName));
                    videoThumbnail.setOnClickListener(v -> ChatActivity.this.playVideo(videoUrl, fileName));
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
            }

            public void bind(Message message) {
                itemView.setOnLongClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && actionListener != null) {
                        boolean canEdit = message.isTextMessage();
                        showMessageOptionsDialog(message, v, canEdit);
                        return true;
                    }
                    return false;
                });

                messageLayout.setOnLongClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && actionListener != null) {
                        boolean canEdit = message.isTextMessage();
                        showMessageOptionsDialog(message, v, canEdit);
                        return true;
                    }
                    return false;
                });

                if (message.isTextMessage()) {
                    String text = message.getText();
                    if (message.isEdited()) {
                        text = text + " (изм.)";
                    }
                    messageText.setText(text);
                    messageLayout.setOnClickListener(null);
                } else if (message.isImageMessage()) {
                    messageText.setText("🖼️ Изображение");
                    setupImageClick(message);
                } else if (message.isVideoMessage()) {
                    messageText.setText("🎥 Видео");
                    setupVideoClick(message);
                } else if (message.isDocumentMessage()) {
                    String fileName = message.getFileName();
                    messageText.setText("📄 " + (fileName != null ? fileName : "Документ"));
                    setupDocumentClick(message);
                }
                messageTime.setText(formatTime(message.getTimestamp()));
            }

            private void setupImageClick(Message message) {
                messageLayout.setOnClickListener(v -> {
                    String imageUrl = message.getFileUrl();
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Intent intent = new Intent(context, FullImageActivity.class);
                        intent.putExtra("image_url", imageUrl);
                        context.startActivity(intent);
                    }
                });
            }

            private void setupVideoClick(Message message) {
                messageLayout.setOnClickListener(v -> {
                    String videoUrl = message.getFileUrl();
                    String fileName = message.getFileName();
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        ChatActivity.this.playVideo(videoUrl, fileName);
                    } else {
                        Toast.makeText(context, "Видео не найдено", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            private void setupDocumentClick(Message message) {
                messageLayout.setOnClickListener(v -> {
                    String fileUrl = message.getFileUrl();
                    String fileName = message.getFileName();
                    if (fileUrl != null && !fileUrl.isEmpty()) {
                        ChatActivity.this.downloadDocument(fileUrl, fileName);
                    } else {
                        Toast.makeText(context, "Документ не найден", Toast.LENGTH_SHORT).show();
                    }
                });
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
            }

            public void bind(Message message) {
                itemView.setOnLongClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && actionListener != null) {
                        showMessageOptionsDialog(message, v, false);
                        return true;
                    }
                    return false;
                });

                messageLayout.setOnLongClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && actionListener != null) {
                        showMessageOptionsDialog(message, v, false);
                        return true;
                    }
                    return false;
                });

                if (message.isTextMessage()) {
                    String text = message.getText();
                    if (message.isEdited()) {
                        text = text + " (изм.)";
                    }
                    messageText.setText(text);
                    messageLayout.setOnClickListener(null);
                } else if (message.isImageMessage()) {
                    messageText.setText("🖼️ Изображение");
                    setupImageClick(message);
                } else if (message.isVideoMessage()) {
                    messageText.setText("🎥 Видео");
                    setupVideoClick(message);
                } else if (message.isDocumentMessage()) {
                    String fileName = message.getFileName();
                    messageText.setText("📄 " + (fileName != null ? fileName : "Документ"));
                    setupDocumentClick(message);
                }
                messageTime.setText(formatTime(message.getTimestamp()));
            }

            private void setupImageClick(Message message) {
                messageLayout.setOnClickListener(v -> {
                    String imageUrl = message.getFileUrl();
                    String filename = message.getFileName();
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Intent intent = new Intent(context, FullImageActivity.class);
                        intent.putExtra("image_url", imageUrl);
                        intent.putExtra("FileName", filename);
                        context.startActivity(intent);
                    }
                });
            }

            private void setupVideoClick(Message message) {
                messageLayout.setOnClickListener(v -> {
                    String videoUrl = message.getFileUrl();
                    String fileName = message.getFileName();
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        ChatActivity.this.playVideo(videoUrl, fileName);
                    } else {
                        Toast.makeText(context, "Видео не найдено", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            private void setupDocumentClick(Message message) {
                messageLayout.setOnClickListener(v -> {
                    String fileUrl = message.getFileUrl();
                    String fileName = message.getFileName();
                    if (fileUrl != null && !fileUrl.isEmpty()) {
                        ChatActivity.this.downloadDocument(fileUrl, fileName);
                    } else {
                        Toast.makeText(context, "Документ не найден", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
}