package com.example.mytelegram;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.bumptech.glide.Glide;
import com.example.mytelegram.databinding.ActivityMainBinding;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_NOTIFICATION_REQUEST_CODE = 101;
    private static final int PERMISSION_REQUEST_CODE = 100;

    private User currentUser;
    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private DatabaseReference databaseReference;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);

        // Инициализация Firebase
        initFirebase();

        // Проверка и запрос разрешений
        checkAndRequestPermissions();

        binding.appBarMain.fab.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, ChouseContactActivity.class);
            startActivity(intent);
        });

        // Инициализация навигации с задержкой
        setupNavigationWithDelay();

        // Обработка данных пользователя
        handleUserData();

        setupAvatarClick();

        // Настройка Firebase Messaging
        setupFirebaseMessaging();
    }

    private void initFirebase() {
        firebaseAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference();
    }

    private void checkAndRequestPermissions() {
        // Проверяем разрешения для Android 6.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Проверяем разрешения для файлов
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        PERMISSION_REQUEST_CODE
                );
            }

            // Для Android 13+ (API 33) проверяем разрешение на уведомления
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkAndRequestNotificationPermission();
            }
        }
    }

    private void checkAndRequestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            // Разрешение уже есть
            Log.d(TAG, "Разрешение на уведомления уже предоставлено");
        } else {
            // Показываем объяснение, если нужно
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.POST_NOTIFICATIONS)) {

                showNotificationPermissionExplanation();
            } else {
                // Сразу запрашиваем разрешение
                requestNotificationPermission();
            }
        }
    }

    private void showNotificationPermissionExplanation() {
        new AlertDialog.Builder(this)
                .setTitle("Разрешение на уведомления")
                .setMessage("Для получения уведомлений о новых сообщениях необходимо предоставить разрешение.")
                .setPositiveButton("Разрешить", (dialog, which) -> {
                    requestNotificationPermission();
                })
                .setNegativeButton("Позже", null)
                .show();
    }

    private void requestNotificationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                PERMISSION_NOTIFICATION_REQUEST_CODE);
    }

    private void setupFirebaseMessaging() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String token = task.getResult();
                        Log.d(TAG, "FCM Token получен: " + token);

                        // Сохраняем токен в базу данных
                        saveFcmTokenToDatabase(token);
                    } else {
                        Log.e(TAG, "Не удалось получить FCM токен", task.getException());
                    }
                });

        // Подписываемся на тему для отладки
        FirebaseMessaging.getInstance().subscribeToTopic("test")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Подписан на тему 'test'");
                    }
                });
    }

    private void saveFcmTokenToDatabase(String token) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            databaseReference.child("users").child(userId)
                    .child("fcmToken").setValue(token)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "FCM токен сохранен в базу данных");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Ошибка сохранения FCM токена", e);
                    });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешения предоставлены", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Разрешения необходимы для работы с файлами", Toast.LENGTH_LONG).show();
            }
        }
        else if (requestCode == PERMISSION_NOTIFICATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Разрешение предоставлено
                Log.d(TAG, "Разрешение на уведомления предоставлено");
                Toast.makeText(this, "Уведомления включены", Toast.LENGTH_SHORT).show();

                // Теперь можно настроить FCM
                setupFirebaseMessaging();
            } else {
                // Разрешение отклонено
                Log.w(TAG, "Разрешение на уведомления отклонено");
                Toast.makeText(this,
                        "Вы не будете получать уведомления о новых сообщениях",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadAvatarForNavHeader();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAvatarForNavHeader();
    }

    private void setupNavigationWithDelay() {
        binding.getRoot().post(() -> {
            try {
                setupNavigation();
            } catch (Exception e) {
                Log.e(TAG, "Navigation setup failed: " + e.getMessage());
            }
        });
    }

    private void setupNavigation() {
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();

        NavController navController = Navigation.findNavController(this,
                R.id.nav_host_fragment_content_main);

        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (drawer != null) {
                drawer.closeDrawer(GravityCompat.START);
            }

            if (id == R.id.userProfileActivity2){
                openProfileActivity();
                return true;
            }
            if (id == R.id.Saves){
                openSavedMessages();
                return true;
            }

            return NavigationUI.onNavDestinationSelected(item, navController);
        });
    }

    private void openProfileActivity() {
        Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
        if (currentUser != null) {
            intent.putExtra("user_data", currentUser);
        }
        startActivity(intent);
    }

    private void openSavedMessages() {
        Intent intent = new Intent(MainActivity.this, ChatActivity.class);
        String userId = currentUser != null ? currentUser.getId() : "unknown";
        intent.putExtra("chatId", userId + "_aj2Cg0QyVDdyJbkbod0wvz6mGNe2");
        intent.putExtra("recipientId", "aj2Cg0QyVDdyJbkbod0wvz6mGNe2");
        intent.putExtra("recipientName", "Избранное");
        startActivity(intent);
    }

    private void setupAvatarClick() {
        View headerView = binding.navView.getHeaderView(0);
        if (headerView == null) return;

        ImageView navAvatar = headerView.findViewById(R.id.imageView);
        if (navAvatar != null) {
            navAvatar.setOnClickListener(v -> openProfileActivity());
        }
    }

    private void loadAvatarForNavHeader() {
        FirebaseUser currentFirebaseUser = firebaseAuth.getCurrentUser();
        if (currentFirebaseUser == null) {
            Log.e(TAG, "Пользователь не авторизован");
            setDefaultAvatarInNavHeader();
            return;
        }

        String userId = currentFirebaseUser.getUid();
        DatabaseReference avatarRef = databaseReference.child("avatars").child(userId);

        avatarRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String avatarUrl = dataSnapshot.getValue(String.class);
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        updateAvatarInNavHeader(avatarUrl);
                    } else {
                        setDefaultAvatarInNavHeader();
                    }
                } else {
                    setDefaultAvatarInNavHeader();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Ошибка загрузки аватара: " + databaseError.getMessage());
                setDefaultAvatarInNavHeader();
            }
        });
    }

    private void updateAvatarInNavHeader(String avatarUrl) {
        runOnUiThread(() -> {
            if (isDestroyed()) return;

            View headerView = binding.navView.getHeaderView(0);
            if (headerView == null) return;

            ImageView navAvatar = headerView.findViewById(R.id.imageView);
            if (navAvatar != null) {
                Glide.with(MainActivity.this)
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .into(navAvatar);
            }
        });
    }

    private void setDefaultAvatarInNavHeader() {
        runOnUiThread(() -> {
            if (isDestroyed()) return;

            View headerView = binding.navView.getHeaderView(0);
            if (headerView == null) return;

            ImageView navAvatar = headerView.findViewById(R.id.imageView);
            if (navAvatar != null) {
                navAvatar.setImageResource(R.drawable.ic_person);
            }
        });
    }

    private void handleUserData() {
        currentUser = getIntent().getParcelableExtra("user_data");
        updateNavHeader();
    }

    private void updateNavHeader() {
        View headerView = binding.navView.getHeaderView(0);
        if (headerView == null) return;

        TextView navUsername = headerView.findViewById(R.id.etUsername);
        TextView navEmail = headerView.findViewById(R.id.etEmail);

        if (navUsername != null) {
            navUsername.setText(currentUser != null && currentUser.getUsername() != null
                    ? currentUser.getUsername() : "Гость");
        }

        if (navEmail != null) {
            navEmail.setText(currentUser != null && currentUser.getEmail() != null
                    ? currentUser.getEmail() : "Email не указан");
        }

        loadAvatarForNavHeader();
    }

    @Override
    public boolean onSupportNavigateUp() {
        try {
            NavController navController = Navigation.findNavController(this,
                    R.id.nav_host_fragment_content_main);
            return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                    || super.onSupportNavigateUp();
        } catch (Exception e) {
            Log.e(TAG, "Navigate up error: " + e.getMessage());
            onBackPressed();
            return true;
        }
    }
}