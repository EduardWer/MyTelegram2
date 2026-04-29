package com.example.mytelegram;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreateGroupActivity extends AppCompatActivity {

    private static final String TAG = "CreateGroupActivity";
    private static final int PICK_GROUP_AVATAR = 101;
    private static final String BUCKET_NAME = "server21";
    private static final String YANDEX_CLOUD_ACCESS_KEY = "YCAJETFSyLNjaaVZt_qSnMevC";
    private static final String YANDEX_CLOUD_SECRET_KEY = "YCNfeBlLIjDPEhWRcWl14PYmQE9oOI6pXcePO6fu";

    private EditText groupNameEditText;
    private ImageView groupAvatarPreview;
    private Button pickAvatarButton;
    private RecyclerView usersRecyclerView;
    private ProgressBar progressBar;
    private Button createButton;
    private EditText searchEditText;

    private FirebaseUser currentUser;
    private List<UserModel> allUsers;
    private List<UserModel> filteredUsers;
    private UserSelectionAdapter adapter;

    private Uri selectedAvatarUri;
    private String adminDomain; // Домен администратора

    // Firebase
    private DatabaseReference usersRef;
    private DatabaseReference groupsRef;
    private DatabaseReference chatsRef;
    private DatabaseReference userChatsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        initFirebase();
        initViews();
        loadAdminDomain(); // Загружаем домен админа первым
        setupClickListeners();
        setupSearch();
    }

    private void initFirebase() {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Не авторизован", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        groupsRef = FirebaseDatabase.getInstance().getReference("groups");
        chatsRef = FirebaseDatabase.getInstance().getReference("chats");
        userChatsRef = FirebaseDatabase.getInstance().getReference("userChats");
    }

    private void initViews() {
        groupNameEditText = findViewById(R.id.groupNameEditText);
        groupAvatarPreview = findViewById(R.id.groupAvatarPreview);
        pickAvatarButton = findViewById(R.id.pickAvatarButton);
        usersRecyclerView = findViewById(R.id.usersRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        createButton = findViewById(R.id.createButton);
        searchEditText = findViewById(R.id.searchUsersEditText);

        allUsers = new ArrayList<>();
        filteredUsers = new ArrayList<>();
        adapter = new UserSelectionAdapter(filteredUsers, currentUser.getUid());
        usersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        usersRecyclerView.setAdapter(adapter);
    }

    // Загрузка домена администратора
    private void loadAdminDomain() {
        progressBar.setVisibility(View.VISIBLE);

        DatabaseReference currentUserRef = FirebaseDatabase.getInstance()
                .getReference("users").child(currentUser.getUid());

        currentUserRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String email = snapshot.child("email").getValue(String.class);
                    if (email != null && email.contains("@")) {
                        adminDomain = email.substring(email.indexOf("@") + 1).toLowerCase();
                        Log.d(TAG, "Admin domain: " + adminDomain);
                    }
                }
                // После загрузки домена загружаем пользователей
                loadUsers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                // При ошибке загружаем всех пользователей
                loadUsers();
            }
        });
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filterUsers(s.toString());
            }
        });
    }

    private void filterUsers(String query) {
        filteredUsers.clear();
        if (TextUtils.isEmpty(query)) {
            filteredUsers.addAll(allUsers);
        } else {
            String lowerQuery = query.toLowerCase();
            for (UserModel user : allUsers) {
                String name = user.getUsername() != null ? user.getUsername().toLowerCase() : "";
                if (name.contains(lowerQuery)) {
                    filteredUsers.add(user);
                }
            }
        }
        adapter.updateList(filteredUsers);
    }

    private void loadUsers() {
        progressBar.setVisibility(View.VISIBLE);

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allUsers.clear();

                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    UserModel user = userSnap.getValue(UserModel.class);
                    if (user != null && !user.getUid().equals(currentUser.getUid())) {
                        user.setUid(userSnap.getKey());

                        // Проверяем домен пользователя
                        String userEmail = user.getEmail();
                        if (userEmail != null && userEmail.contains("@")) {
                            String userDomain = userEmail.substring(userEmail.indexOf("@") + 1).toLowerCase();
                            user.setDomain(userDomain);

                            // Фильтруем по домену (если домен админа известен)
                            if (!TextUtils.isEmpty(adminDomain)) {
                                if (!adminDomain.equals(userDomain)) {
                                    continue; // Пропускаем пользователей с другим доменом
                                }
                            }
                        } else if (!TextUtils.isEmpty(adminDomain)) {
                            // Если домен админа известен, но у пользователя нет email - пропускаем
                            continue;
                        }

                        allUsers.add(user);
                        loadAvatarForUser(user);
                    }
                }

                filterUsers(""); // Показать всех после загрузки
                progressBar.setVisibility(View.GONE);

                Log.d(TAG, "Loaded " + allUsers.size() + " users (domain filter: " + adminDomain + ")");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(CreateGroupActivity.this, "Ошибка загрузки пользователей", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAvatarForUser(UserModel user) {
        FirebaseDatabase.getInstance().getReference("avatars")
                .child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String avatarUrl = snapshot.getValue(String.class);
                        if (avatarUrl != null) {
                            user.setAvatarUrl(avatarUrl);
                            int pos = findUserPosition(user.getUid());
                            if (pos >= 0) {
                                adapter.notifyItemChanged(pos);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private int findUserPosition(String uid) {
        for (int i = 0; i < filteredUsers.size(); i++) {
            if (filteredUsers.get(i).getUid().equals(uid)) {
                return i;
            }
        }
        return -1;
    }

    private void setupClickListeners() {
        pickAvatarButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_GROUP_AVATAR);
        });

        createButton.setOnClickListener(v -> createGroup());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_GROUP_AVATAR && resultCode == RESULT_OK && data != null) {
            selectedAvatarUri = data.getData();
            Glide.with(this)
                    .load(selectedAvatarUri)
                    .circleCrop()
                    .into(groupAvatarPreview);
        }
    }

    private void createGroup() {
        String groupName = groupNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(groupName)) {
            groupNameEditText.setError("Введите название группы");
            return;
        }

        List<String> selectedUsers = adapter.getSelectedUserIds();
        if (selectedUsers.isEmpty()) {
            Toast.makeText(this, "Выберите хотя бы одного участника", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!selectedUsers.contains(currentUser.getUid())) {
            selectedUsers.add(currentUser.getUid());
        }

        progressBar.setVisibility(View.VISIBLE);
        createButton.setEnabled(false);

        String groupId = UUID.randomUUID().toString();
        String chatId = UUID.randomUUID().toString();

        Map<String, Object> groupData = new HashMap<>();
        groupData.put("id", groupId);
        groupData.put("name", groupName);
        groupData.put("createdBy", currentUser.getUid());
        groupData.put("createdAt", System.currentTimeMillis());
        groupData.put("chatId", chatId);

        // Сохраняем домен группы (для проверки при добавлении новых участников)
        if (!TextUtils.isEmpty(adminDomain)) {
            groupData.put("domain", adminDomain);
        }

        Map<String, Boolean> members = new HashMap<>();
        for (String uid : selectedUsers) {
            members.put(uid, true);
        }
        groupData.put("members", members);

        Map<String, Object> chatData = new HashMap<>();
        chatData.put("chatId", chatId);
        chatData.put("chatType", "group");
        chatData.put("groupId", groupId);
        chatData.put("lastMessage", "Группа создана");
        chatData.put("timestamp", System.currentTimeMillis());
        chatData.put("messageType", "system");

        Map<String, Object> userChatEntry = new HashMap<>();
        userChatEntry.put("chatId", chatId);
        userChatEntry.put("chatType", "group");
        userChatEntry.put("groupId", groupId);
        userChatEntry.put("groupName", groupName);
        userChatEntry.put("lastMessage", "Группа создана");
        userChatEntry.put("timestamp", System.currentTimeMillis());
        userChatEntry.put("unreadCount", 0);
        userChatEntry.put("lastMessageSenderId", currentUser.getUid());
        userChatEntry.put("messageType", "system");

        // Добавляем домен в userChatEntry
        if (!TextUtils.isEmpty(adminDomain)) {
            userChatEntry.put("domain", adminDomain);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("groups/" + groupId, groupData);
        updates.put("chats/" + chatId, chatData);
        for (String uid : selectedUsers) {
            updates.put("userChats/" + uid + "/" + chatId, userChatEntry);
        }

        FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    createButton.setEnabled(true);
                    if (task.isSuccessful()) {
                        if (selectedAvatarUri != null) {
                            uploadGroupAvatar(groupId, selectedAvatarUri);
                        }
                        Toast.makeText(CreateGroupActivity.this, "Группа создана!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(CreateGroupActivity.this, GroupChatActivity.class);
                        intent.putExtra("chatId", chatId);
                        intent.putExtra("groupId", groupId);
                        intent.putExtra("groupName", groupName);
                        startActivity(intent);
                        finish();
                    } else {
                        Log.e(TAG, "Ошибка создания группы", task.getException());
                        Toast.makeText(CreateGroupActivity.this, "Ошибка создания группы", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void uploadGroupAvatar(String groupId, Uri avatarUri) {
        if (groupId == null || avatarUri == null) {
            Toast.makeText(this, "Нет данных для загрузки", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Загрузка аватара...", Toast.LENGTH_SHORT).show();

        File tempFile = createTempFileFromUri(avatarUri);
        if (tempFile == null) {
            Toast.makeText(this, "Не удалось создать временный файл", Toast.LENGTH_SHORT).show();
            return;
        }

        YandexCloudUploader uploader = new YandexCloudUploader(
                YANDEX_CLOUD_ACCESS_KEY,
                YANDEX_CLOUD_SECRET_KEY
        );
        String fileName = "group_avatars/" + groupId + "_" + System.currentTimeMillis() + ".jpg";

        uploader.uploadFile(tempFile, BUCKET_NAME, fileName, new YandexCloudUploader.UploadCallback() {
            @Override
            public void onSuccess(String fileUrl) {
                runOnUiThread(() -> {
                    if (tempFile.exists()) tempFile.delete();
                    FirebaseDatabase.getInstance().getReference()
                            .child("groups").child(groupId).child("avatarUrl")
                            .setValue(fileUrl)
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(CreateGroupActivity.this, "Аватар группы обновлён", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Ошибка сохранения URL аватара", e);
                                Toast.makeText(CreateGroupActivity.this, "Ошибка обновления аватара", Toast.LENGTH_SHORT).show();
                            });
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (tempFile.exists()) tempFile.delete();
                    Log.e(TAG, "Ошибка загрузки аватара: " + error);
                    Toast.makeText(CreateGroupActivity.this, "Ошибка загрузки: " + error, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onProgress(int progress) {
                Log.d(TAG, "Загрузка аватара: " + progress + "%");
            }
        });
    }

    private File createTempFileFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;
            File tempFile = File.createTempFile("upload", ".tmp", getCacheDir());
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            inputStream.close();
            return tempFile;
        } catch (IOException e) {
            Log.e(TAG, "Ошибка создания временного файла", e);
            return null;
        }
    }
}