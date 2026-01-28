package com.example.mytelegram;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.bumptech.glide.Glide;
import com.example.mytelegram.databinding.ActivityMainBinding;
import com.example.mytelegram.ui.Gallary.SlideshowFragment;
import com.example.mytelegram.ui.gallery.GalleryFragment;
import com.example.mytelegram.ui.home.HomeFragment;
import com.example.mytelegram.ui.settings.settingsFragment;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
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

        // Проверка разрешений
        checkPermissions();

        binding.appBarMain.fab.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, ChouseContactActivity.class);
            startActivity(intent);
        });

        // Инициализация навигации с задержкой
        setupNavigationWithDelay();

        // Обработка данных пользователя
        handleUserData();

        setupAvatarClick();
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

    private void initFirebase() {
        firebaseAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference();
    }

    private void checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        PERMISSION_REQUEST_CODE
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешения предоставлены", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Разрешения необходимы для работы с файлами", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupNavigationWithDelay() {
        // Даем время для инициализации NavHostFragment
        binding.getRoot().post(() -> {
            try {
                setupNavigation();
            } catch (Exception e) {
                Log.e(TAG, "Navigation setup failed, using fallback: " + e.getMessage());
                setupNavigationFallback();
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

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);

        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (drawer != null) {
                drawer.closeDrawer(GravityCompat.START);
            }
            if (id == R.id.userProfileActivity2){
                Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                if (currentUser != null) {
                    intent.putExtra("user_data", currentUser);
                }
                startActivity(intent);
                return true;
            }
            if (id == R.id.Saves){

                Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                intent.putExtra("chatId", currentUser.getId()+"_aj2Cg0QyVDdyJbkbod0wvz6mGNe2");
                intent.putExtra("recipientId", "aj2Cg0QyVDdyJbkbod0wvz6mGNe2");
                intent.putExtra("recipientName", "Избранное");
                startActivity(intent);
            }
            return NavigationUI.onNavDestinationSelected(item, navController);
        });
    }

    private void setupNavigationFallback() {
        NavigationView navigationView = binding.navView;

        navigationView.setNavigationItemSelectedListener(item -> {
            DrawerLayout drawer = binding.drawerLayout;
            if (drawer != null) {
                drawer.closeDrawer(GravityCompat.START);
            }

            int itemId = item.getItemId();

            // Обработка ВСЕХ пунктов меню в одном блоке if-else
            if (itemId == R.id.userProfileActivity2) {
                // Переход в профиль с передачей данных
                Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                if (currentUser != null) {
                    intent.putExtra("user_data", currentUser);
                }
                startActivity(intent);
                return true;
            } else if (itemId == R.id.nav_home) {
                loadFragment(new HomeFragment(), "HomeFragment");
                setTitle("Чаты");
                return true;
            } else if (itemId == R.id.nav_gallery) {
                loadFragment(new GalleryFragment(), "GalleryFragment");
                setTitle("Галерея");
                return true;
            } else if (itemId == R.id.nav_slideshow) {
                loadFragment(new SlideshowFragment(), "SlideshowFragment");
                setTitle("Слайдшоу");
                return true;
            } else if (itemId == R.id.nav_settings) {
                loadFragment(new settingsFragment(), "settingsFragment");
                setTitle("Настройки");
                return true;
            } else {
                // Для других пунктов, которых нет в меню
                return NavigationUI.onNavDestinationSelected(item,
                        Navigation.findNavController(this, R.id.nav_host_fragment_content_main));
            }
        });

        // Загружаем фрагмент по умолчанию
        loadFragment(new HomeFragment(), "ChatsListFragment");
    }


    private void loadFragment(Fragment fragment, String tag) {
        try {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.replace(R.id.nav_host_fragment_content_main, fragment, tag);
            transaction.commit();
        } catch (Exception e) {
            Log.e(TAG, "Error loading fragment: " + e.getMessage());
        }
    }

    private void setupAvatarClick() {
        View headerView = binding.navView.getHeaderView(0);
        if (headerView == null) return;

        ImageView navAvatar = headerView.findViewById(R.id.imageView);
        if (navAvatar != null) {
            navAvatar.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                if (currentUser != null) {
                    intent.putExtra("user_data", currentUser);
                }
                startActivity(intent);
            });
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

        Log.d(TAG, "Загрузка аватара для пользователя: " + userId);

        avatarRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String avatarUrl = dataSnapshot.getValue(String.class);
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        Log.d(TAG, "Аватар найден: " + avatarUrl);
                        updateAvatarInNavHeader(avatarUrl);
                    } else {
                        Log.d(TAG, "Ссылка на аватар пустая");
                        setDefaultAvatarInNavHeader();
                    }
                } else {
                    Log.d(TAG, "Аватар не найден в Firebase");
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
            if (isDestroyed()) {
                return;
            }

            View headerView = binding.navView.getHeaderView(0);
            if (headerView == null) return;

            ImageView navAvatar = headerView.findViewById(R.id.imageView);
            if (navAvatar != null) {
                Log.d(TAG, "Обновление аватара в шторке: " + avatarUrl);

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
            if (isDestroyed()) {
                return;
            }

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
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
            return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                    || super.onSupportNavigateUp();
        } catch (Exception e) {
            Log.e(TAG, "Navigate up error: " + e.getMessage());
            onBackPressed();
            return true;
        }
    }
    boolean isDeleted = true;
    boolean isEdited = true;

    public void setContent(String newContent) {
        if (isDeleted) {
            throw new IllegalStateException("Нельзя редактировать удаленное сообщение");
        }
        if (newContent == null || newContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Содержание не может быть пустым");
        }
        this.isEdited = true;
    }
}