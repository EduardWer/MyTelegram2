package com.example.mytelegram;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private ImageView profileAvatar;
    private EditText profileNameEdit;
    private EditText profileEmailEdit;
    private EditText profileBioEdit;
    private EditText profileDepartEdit;
    private MaterialButton editSaveButton;

    private User currentUser;
    private YandexCloudUploader uploader;
    private FirebaseAuth firebaseAuth;
    private DatabaseReference databaseReference;

    // Константы для разрешений
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;
    private static final int REQUEST_PERMISSION_CODE = 100;

    // Переменные для работы с камерой
    private String currentPhotoPath;
    private ProgressDialog progressDialog;

    // Настройки Яндекс.Облака
    private static final String YANDEX_CLOUD_BUCKET = "server21";
    private static final String YANDEX_CLOUD_ACCESS_KEY = "YCAJETFSyLNjaaVZt_qSnMevC";
    private static final String YANDEX_CLOUD_SECRET_KEY = "YCNfeBlLIjDPEhWRcWl14PYmQE9oOI6pXcePO6fu";

    // Переменная для хранения выбранного действия
    private int pendingAction = -1;
    private static final int ACTION_TAKE_PHOTO = 1;
    private static final int ACTION_PICK_PHOTO = 2;

    // Переменная для режима редактирования
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Получаем пользователя из Intent
        currentUser = getIntent().getParcelableExtra("user_data");

        initViews();
        initFirebase();
        initYandexCloudUploader();
        setupAvatarClick();
        setupEditSaveButton();
        loadAvatarUrlFromFirebase();
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadAvatarUrlFromFirebase();
    }

    private void initViews() {
        profileAvatar = findViewById(R.id.profile_avatar);
        profileNameEdit = findViewById(R.id.profile_name_edit);
        profileEmailEdit = findViewById(R.id.profile_email_edit);
        profileDepartEdit = findViewById(R.id.profile_department_edit);
        profileBioEdit = findViewById(R.id.profile_bio_edit);
        editSaveButton = findViewById(R.id.edit_save_button);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Загрузка фото...");
        progressDialog.setCancelable(false);

        populateUserData();
        disableEditMode(); // Начинаем в режиме просмотра
    }

    private void populateUserData() {
        if (currentUser != null) {
            runOnUiThread(() -> {
                if (profileNameEdit != null) {
                    profileNameEdit.setText(currentUser.getUsername() != null ?
                            currentUser.getUsername() : "Имя не указано");
                }
                if (profileEmailEdit != null) {
                    profileEmailEdit.setText(currentUser.getEmail() != null ?
                            currentUser.getEmail() : "Email не указан");
                }
                if (profileDepartEdit != null) {
                    profileDepartEdit.setText(currentUser.getDepartment() != null ?
                            currentUser.getDepartment() : "Отдел не указан");
                }
                if (profileBioEdit != null) {
                    profileBioEdit.setText(currentUser.getBio() != null ?
                            currentUser.getBio() : "Биография не указана");
                }
            });
        } else {
            Log.e("ProfileActivity", "currentUser is null - данные пользователя не получены");
        }
    }

    private void setupEditSaveButton() {
        editSaveButton.setOnClickListener(v -> {
            if (isEditMode) {
                // Сохраняем изменения
                saveProfile();
            } else {
                // Включаем режим редактирования
                enableEditMode();
            }
        });
    }

    private void enableEditMode() {
        isEditMode = true;

        // Включаем редактирование полей
        profileNameEdit.setEnabled(true);
        profileEmailEdit.setEnabled(true);
        profileDepartEdit.setEnabled(true);
        profileBioEdit.setEnabled(true);

        // Добавляем подчеркивание для визуального выделения
        setEditTextBackground(true);

        // Меняем текст кнопки
        editSaveButton.setText("Сохранить");

        // Фокусируемся на первом поле
        profileNameEdit.requestFocus();

        // Показываем клавиатуру
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(profileNameEdit, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void disableEditMode() {
        isEditMode = false;

        // Выключаем редактирование полей
        profileNameEdit.setEnabled(false);
        profileEmailEdit.setEnabled(false);
        profileDepartEdit.setEnabled(false);
        profileBioEdit.setEnabled(false);

        // Убираем подчеркивание
        setEditTextBackground(false);

        // Возвращаем текст кнопки
        editSaveButton.setText("Редактировать профиль");

        // Скрываем клавиатуру
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(profileNameEdit.getWindowToken(), 0);
        }
    }

    private void setEditTextBackground(boolean showBackground) {
        int backgroundRes = showBackground ? R.drawable.edit_text_underline : android.R.color.transparent;

        profileNameEdit.setBackgroundResource(backgroundRes);
        profileEmailEdit.setBackgroundResource(backgroundRes);
        profileDepartEdit.setBackgroundResource(backgroundRes);
        profileBioEdit.setBackgroundResource(backgroundRes);
    }

    private void saveProfile() {
        // Получаем новые значения
        String newName = profileNameEdit.getText().toString().trim();
        String newEmail = profileEmailEdit.getText().toString().trim();
        String newDepartment = profileDepartEdit.getText().toString().trim();
        String newBio = profileBioEdit.getText().toString().trim();

        // Валидация
        if (newName.isEmpty()) {
            Toast.makeText(this, "Имя не может быть пустым", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newEmail.isEmpty()) {
            Toast.makeText(this, "Email не может быть пустым", Toast.LENGTH_SHORT).show();
            return;
        }

        // Сохраняем данные в Firebase
        saveProfileToFirebase(newName, newEmail, newDepartment, newBio);
    }

    private void saveProfileToFirebase(String name, String email, String department, String bio) {
        FirebaseUser currentFirebaseUser = firebaseAuth.getCurrentUser();
        if (currentFirebaseUser == null) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.setMessage("Сохранение профиля...");
        progressDialog.show();

        String userId = currentFirebaseUser.getUid();
        DatabaseReference userRef = databaseReference.child("users").child(userId);

        // Обновляем данные пользователя
        userRef.child("username").setValue(name);
        userRef.child("email").setValue(email);
        userRef.child("department").setValue(department);
        userRef.child("bio").setValue(bio)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();

                    // Обновляем локальный объект пользователя
                    if (currentUser != null) {
                        currentUser.setUsername(name);
                        currentUser.setEmail(email);
                        currentUser.setDepartment(department);
                        currentUser.setBio(bio);
                    }

                    Toast.makeText(ProfileActivity.this, "Профиль сохранен", Toast.LENGTH_SHORT).show();
                    disableEditMode();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(ProfileActivity.this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("ProfileActivity", "Error saving profile: " + e.getMessage());
                });
    }

    private void initFirebase() {
        firebaseAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference();
    }

    private void initYandexCloudUploader() {
        uploader = new YandexCloudUploader(YANDEX_CLOUD_ACCESS_KEY, YANDEX_CLOUD_SECRET_KEY);
    }

    private void openAvatarChangeDialog() {
        String[] options = {"Сделать фото", "Выбрать из галереи", "Отмена"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выберите источник фото");
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Сделать фото
                    pendingAction = ACTION_TAKE_PHOTO;
                    checkAndRequestPermissions();
                    break;
                case 1: // Выбрать из галереи
                    pendingAction = ACTION_PICK_PHOTO;
                    checkAndRequestPermissions();
                    break;
                case 2: // Отмена
                    dialog.dismiss();
                    break;
            }
        });
        builder.show();
    }

    private void setupAvatarClick() {
        if (profileAvatar != null) {
            profileAvatar.setOnClickListener(v -> openAvatarChangeDialog());
        }
    }


    private void loadAvatarUrlFromFirebase() {
        FirebaseUser currentFirebaseUser = firebaseAuth.getCurrentUser();
        if (currentFirebaseUser == null) {
            Log.e("ProfileActivity", "Пользователь не авторизован");
            setDefaultAvatar();
            return;
        }

        String userId = currentFirebaseUser.getUid();
        DatabaseReference avatarRef = databaseReference.child("avatars").child(userId);

        Log.d("ProfileActivity", "Загрузка ссылки на аватар для пользователя: " + userId);

        avatarRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String avatarUrl = dataSnapshot.getValue(String.class);
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        Log.d("ProfileActivity", "Ссылка на аватар найдена: " + avatarUrl);
                        loadAvatarFromYandexCloud(avatarUrl);
                    } else {
                        Log.d("ProfileActivity", "Ссылка на аватар пустая");
                        setDefaultAvatar();
                    }
                } else {
                    Log.d("ProfileActivity", "Ссылка на аватар не найдена в Firebase");
                    setDefaultAvatar();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("ProfileActivity", "Ошибка загрузки ссылки: " + databaseError.getMessage());
                setDefaultAvatar();
            }
        });
    }

    private void loadAvatarFromYandexCloud(String avatarUrl) {
        runOnUiThread(() -> {
            Log.d("ProfileActivity", "Загрузка аватара из Яндекс Облака: " + avatarUrl);

            Glide.with(ProfileActivity.this)
                    .load(avatarUrl)
                    .placeholder(R.mipmap.ic_launcher_round)
                    .error(R.mipmap.ic_launcher_round)
                    .into(profileAvatar);
        });
    }


    private void saveAvatarUrlToFirebase(String avatarUrl) {
        FirebaseUser currentFirebaseUser = firebaseAuth.getCurrentUser();
        if (currentFirebaseUser == null) {
            Log.e("ProfileActivity", "Пользователь не авторизован для сохранения");
            return;
        }

        String userId = currentFirebaseUser.getUid();
        DatabaseReference avatarRef = databaseReference.child("avatars").child(userId);

        // Сохраняем ТОЛЬКО ссылку в Firebase Database
        avatarRef.setValue(avatarUrl)
                .addOnSuccessListener(aVoid -> {
                    Log.d("ProfileActivity", "Ссылка успешно сохранена в Firebase: " + avatarUrl);
                })
                .addOnFailureListener(e -> {
                    Log.e("ProfileActivity", "Ошибка сохранения ссылки: " + e.getMessage());
                    Toast.makeText(ProfileActivity.this, "Ошибка сохранения аватара", Toast.LENGTH_SHORT).show();
                });
    }

    private void setDefaultAvatar() {
        runOnUiThread(() -> {
            if (profileAvatar != null) {
                profileAvatar.setImageResource(R.mipmap.ic_launcher_round);
            }
        });
    }

    // ДОБАВЛЕНЫ ОСТАЛЬНЫЕ НЕОБХОДИМЫЕ МЕТОДЫ

    private void checkAndRequestPermissions() {
        List<String> requiredPermissions = new ArrayList<>();

        if (pendingAction == ACTION_TAKE_PHOTO) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.CAMERA);
            }

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
            }

        } else if (pendingAction == ACTION_PICK_PHOTO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                        != PackageManager.PERMISSION_GRANTED) {
                    requiredPermissions.add(Manifest.permission.READ_MEDIA_IMAGES);
                }
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
            }
        }

        if (requiredPermissions.isEmpty()) {
            executePendingAction();
        } else {
            ActivityCompat.requestPermissions(this,
                    requiredPermissions.toArray(new String[0]),
                    REQUEST_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                executePendingAction();
            } else {
                handlePermissionDenied();
            }
        }
    }

    private void executePendingAction() {
        switch (pendingAction) {
            case ACTION_TAKE_PHOTO:
                dispatchTakePictureIntent();
                break;
            case ACTION_PICK_PHOTO:
                openGallery();
                break;
        }
    }

    private void handlePermissionDenied() {
        String message = pendingAction == ACTION_TAKE_PHOTO ?
                "Для съемки фото необходимо предоставить разрешение на использование камеры" :
                "Для выбора фото из галереи необходимо предоставить разрешение на доступ к фото";

        new AlertDialog.Builder(this)
                .setTitle("Необходимы разрешения")
                .setMessage(message)
                .setPositiveButton("Повторить", (dialog, which) -> checkAndRequestPermissions())
                .setNegativeButton("Отмена", (dialog, which) ->
                        Toast.makeText(this, "Действие отменено", Toast.LENGTH_SHORT).show())
                .show();
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Ошибка создания файла", Toast.LENGTH_SHORT).show();
                Log.e("ProfileActivity", "Error creating image file", ex);
                return;
            }

            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        } else {
            Toast.makeText(this, "Не найдено приложение для съемки фото", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");

        try {
            startActivityForResult(intent, REQUEST_IMAGE_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть галерею", Toast.LENGTH_SHORT).show();
            Log.e("ProfileActivity", "Error opening gallery", e);
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(null);

        if (storageDir == null) {
            storageDir = getFilesDir();
        }

        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_IMAGE_CAPTURE:
                    if (currentPhotoPath != null) {
                        File photoFile = new File(currentPhotoPath);
                        if (photoFile.exists()) {
                            uploadPhotoToYandexCloud(photoFile);
                        } else {
                            Toast.makeText(this, "Фото не найдено", Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;

                case REQUEST_IMAGE_PICK:
                    if (data != null && data.getData() != null) {
                        Uri selectedImageUri = data.getData();
                        processSelectedImage(selectedImageUri);
                    }
                    break;
            }
        }
    }

    private void processSelectedImage(Uri imageUri) {
        try {
            String filePath = getPathFromUri(imageUri);
            if (filePath != null) {
                File photoFile = new File(filePath);
                uploadPhotoToYandexCloud(photoFile);
            } else {
                copyUriToTempFile(imageUri);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка обработки изображения", Toast.LENGTH_SHORT).show();
            Log.e("ProfileActivity", "Error processing image", e);
        }
    }

    private void copyUriToTempFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_SHORT).show();
                return;
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "selected_image_" + timeStamp + ".jpg";
            File tempFile = new File(getCacheDir(), fileName);

            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            outputStream.close();

            uploadPhotoToYandexCloud(tempFile);

        } catch (IOException e) {
            Toast.makeText(this, "Ошибка копирования файла", Toast.LENGTH_SHORT).show();
            Log.e("ProfileActivity", "Error copying file", e);
        }
    }

    private String getPathFromUri(Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(contentUri, proj, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(column_index);
            }
        } catch (Exception e) {
            Log.e("ProfileActivity", "Error getting path from URI", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private void uploadPhotoToYandexCloud(File photoFile) {
        if (photoFile == null || !photoFile.exists()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uploader == null) {
            Toast.makeText(this, "Ошибка инициализации загрузчика", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.show();
        progressDialog.setMessage("Загрузка фото... 0%");

        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show();
            progressDialog.dismiss();
            return;
        }

        String userId = firebaseUser.getUid();
        String fileName = "avatars/user_" + userId + "_" + System.currentTimeMillis() + ".jpg";

        uploader.uploadFile(photoFile, YANDEX_CLOUD_BUCKET, fileName,
                new YandexCloudUploader.UploadCallback() {
                    @Override
                    public void onSuccess(String fileUrl) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();

                            // Сохраняем ссылку в Firebase Database
                            saveAvatarUrlToFirebase(fileUrl);

                            // Обновляем аватар в интерфейсе
                            loadAvatarFromYandexCloud(fileUrl);

                            Toast.makeText(ProfileActivity.this,
                                    "Аватар успешно обновлен", Toast.LENGTH_SHORT).show();

                            // Удаляем временный файл
                            if (photoFile.getName().startsWith("selected_image_") ||
                                    photoFile.getName().startsWith("JPEG_")) {
                                photoFile.delete();
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(ProfileActivity.this,
                                    "Ошибка загрузки: " + error, Toast.LENGTH_LONG).show();
                            Log.e("ProfileActivity", "Upload error: " + error);
                        });
                    }

                    @Override
                    public void onProgress(int progress) {
                        runOnUiThread(() -> {
                            progressDialog.setMessage("Загрузка фото... " + progress + "%");
                        });
                    }
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}