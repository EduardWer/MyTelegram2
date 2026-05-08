package com.example.mytelegram;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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
    private String currentUserId;
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



        // Создаём каналы уведомлений (включая канал для звонков с рингтоном)
        CallNotificationManager callNotificationManager = new CallNotificationManager(this);
        callNotificationManager.createNotificationChannels();

        // Инициализация навигации с задержкой
        setupNavigationWithDelay();

        // Обработка данных пользователя
        handleUserData();

        setupAvatarClick();

        // Настройка FCM (получение и отправка токена)
        setupFirebaseMessaging();
    }

    private void initFirebase() {
        firebaseAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference();

        FirebaseUser currentFirebaseUser = firebaseAuth.getCurrentUser();
        if (currentFirebaseUser != null) {
            currentUserId = currentFirebaseUser.getUid();
        } else {
            currentUserId = "vLkUH1cFOrTt63pUHPXtNRfRhbu1";
        }

        // Автоматический офлайн при обрыве соединения
        DatabaseReference onlineRef = databaseReference
                .child("users").child(currentUserId).child("online");
        onlineRef.onDisconnect().setValue(false);
        onlineRef.setValue(true).addOnFailureListener(e ->
                Log.e(TAG, "Ошибка установки онлайн-статуса", e));
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkAndRequestNotificationPermission();
            }
        }
    }

    private void checkAndRequestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Разрешение на уведомления уже предоставлено");
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.POST_NOTIFICATIONS)) {
                showNotificationPermissionExplanation();
            } else {
                requestNotificationPermission();
            }
        }
    }

    private void showNotificationPermissionExplanation() {
        new AlertDialog.Builder(this)
                .setTitle("Разрешение на уведомления")
                .setMessage("Для получения уведомлений о новых сообщениях и звонках необходимо предоставить разрешение.")
                .setPositiveButton("Разрешить", (dialog, which) -> requestNotificationPermission())
                .setNegativeButton("Позже", null)
                .show();
    }

    private void requestNotificationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                PERMISSION_NOTIFICATION_REQUEST_CODE);
    }



    private AlertDialog currentCallDialog;

    private void showIncomingCallDialog(String callId, String callerId, String callerName,
                                        String roomName, boolean isVideo) {
        if (currentCallDialog != null && currentCallDialog.isShowing()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📞 Входящий звонок");
        builder.setMessage(callerName + (isVideo ? " (Видео)" : " (Аудио)"));
        builder.setPositiveButton("Ответить", (dialog, which) -> {
            acceptCall(callId, callerId, callerName, roomName, isVideo);
        });
        builder.setNegativeButton("Отклонить", (dialog, which) -> {
            rejectCall(callId, callerId);
        });
        builder.setCancelable(false);

        currentCallDialog = builder.create();
        currentCallDialog.show();
    }

    private void acceptCall(String callId, String callerId, String callerName,
                            String roomName, boolean isVideo) {
        // Обновляем статус звонка
        FirebaseDatabase.getInstance().getReference("calls")
                .child(callId)
                .child("status")
                .setValue("accepted");

        // Открываем CallActivity
        Intent intent = new Intent(this, CallActivity.class);
        intent.putExtra("call_id", callId);
        intent.putExtra("room_name", roomName);
        intent.putExtra("caller_id", callerId);
        intent.putExtra("caller_name", callerName);
        intent.putExtra("is_video", isVideo);
        intent.putExtra("is_outgoing", false);
        startActivity(intent);
    }

    private void rejectCall(String callId, String callerId) {
        FirebaseDatabase.getInstance().getReference("calls")
                .child(callId)
                .child("status")
                .setValue("rejected");
    }

    private DatabaseReference callsRef;
    private ValueEventListener incomingCallListener;

    private void listenForIncomingCalls() {
        callsRef = FirebaseDatabase.getInstance().getReference("calls");

        incomingCallListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot callSnapshot : snapshot.getChildren()) {
                    String callId = callSnapshot.getKey();
                    String calleeId = callSnapshot.child("calleeId").getValue(String.class);
                    String callerId = callSnapshot.child("callerId").getValue(String.class);
                    String callerName = callSnapshot.child("callerName").getValue(String.class);
                    String roomName = callSnapshot.child("roomName").getValue(String.class);
                    Boolean isVideo = callSnapshot.child("isVideo").getValue(Boolean.class);
                    String status = callSnapshot.child("status").getValue(String.class);

                    // Проверяем, что звонок адресован текущему пользователю и еще не обработан
                    if (calleeId != null && calleeId.equals(currentUserId) &&
                            "calling".equals(status) && !ChatActivity.isVisible()) {

                        showIncomingCallDialog(callId, callerId, callerName, roomName, isVideo != null && isVideo);
                        break;
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error listening for calls: " + error.getMessage());
            }
        };

        callsRef.addValueEventListener(incomingCallListener);
    }

    // ==================== FCM ====================

    private void setupFirebaseMessaging() {
        // Получаем FCM-токен
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String token = task.getResult();
                        Log.d(TAG, "FCM Token получен: " + token);

                        // 1. Сохраняем в Firebase Database
                        saveFcmTokenToDatabase(token);

                        // 2. Отправляем на наш Python-сервер
                        //sendTokenToPushServer(token);
                    } else {
                        Log.e(TAG, "Не удалось получить FCM токен", task.getException());
                    }
                });

        // Подписка на тестовую тему
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

            // Сохраняем в /users/{uid}/fcmTokens/{token}: true
            databaseReference.child("users").child(userId)
                    .child("fcmTokens").setValue(token)
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "FCM токен сохранён в Firebase"))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Ошибка сохранения FCM токена", e));
        }
    }




    // ==================== PERMISSIONS RESULT ====================

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
        } else if (requestCode == PERMISSION_NOTIFICATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Разрешение на уведомления предоставлено");
                Toast.makeText(this, "Уведомления включены", Toast.LENGTH_SHORT).show();
                setupFirebaseMessaging();
            } else {
                Log.w(TAG, "Разрешение на уведомления отклонено");
                Toast.makeText(this, "Вы не будете получать уведомления", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ==================== LIFECYCLE ====================

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

    // ==================== NAVIGATION ====================

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

            if (id == R.id.userProfileActivity2) {
                openProfileActivity();
                return true;
            }

            if (id == R.id.Conference) {
                openConferenceActivity();
                return true;
            }


            if (id == R.id.Saves) {
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


    private void openConferenceActivity() {
        Intent intent = new Intent(MainActivity.this, ConferenceActivity.class);

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

    // ==================== AVATAR ====================

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

    // ==================== USER DATA ====================

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

    // ==================== NAVIGATE UP ====================

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