package com.example.mytelegram;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreateGroupActivity extends AppCompatActivity {

    private static final String TAG = "CreateGroupActivity";
    private static final int PICK_GROUP_AVATAR = 101;

    private EditText groupNameEditText;
    private ImageView groupAvatarPreview;
    private Button pickAvatarButton;
    private RecyclerView usersRecyclerView;
    private ProgressBar progressBar;
    private Button createButton;

    private FirebaseUser currentUser;
    private List<UserModel> allUsers;
    private UserSelectionAdapter adapter;

    private Uri selectedAvatarUri;
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
        loadUsers();
        setupClickListeners();
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

        allUsers = new ArrayList<>();
        adapter = new UserSelectionAdapter(allUsers, currentUser.getUid());
        usersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        usersRecyclerView.setAdapter(adapter);
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
                        user.setUid(userSnap.getKey()); // uid может быть ключом
                        allUsers.add(user);
                    }
                }
                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(CreateGroupActivity.this, "Ошибка загрузки пользователей", Toast.LENGTH_SHORT).show();
            }
        });
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

        // Обязательно добавляем текущего пользователя в группу
        if (!selectedUsers.contains(currentUser.getUid())) {
            selectedUsers.add(currentUser.getUid());
        }

        progressBar.setVisibility(View.VISIBLE);
        createButton.setEnabled(false);

        // 1. Генерируем уникальные ID для группы и чата
        String groupId = UUID.randomUUID().toString();
        String chatId = UUID.randomUUID().toString();

        // 2. Создаём узел группы в /groups/{groupId}
        Map<String, Object> groupData = new HashMap<>();
        groupData.put("id", groupId);
        groupData.put("name", groupName);
        groupData.put("createdBy", currentUser.getUid());
        groupData.put("createdAt", System.currentTimeMillis());
        groupData.put("chatId", chatId);

        // Добавляем участников
        Map<String, Boolean> members = new HashMap<>();
        for (String uid : selectedUsers) {
            members.put(uid, true);
        }
        groupData.put("members", members);

        // Аватар загрузим позже, либо сначала отправим на сервер, а потом обновим
        // Если аватар выбран, нужно загрузить его (например, в Yandex Cloud) и сохранить URL.
        // Для простоты пока установим поле avatarUrl = "" (обновим после загрузки)

        // 3. Создаём узел чата в /chats/{chatId}
        Map<String, Object> chatData = new HashMap<>();
        chatData.put("chatId", chatId);
        chatData.put("chatType", "group");
        chatData.put("groupId", groupId);
        chatData.put("lastMessage", "Группа создана");
        chatData.put("timestamp", System.currentTimeMillis());
        chatData.put("messageType", "system");

        // 4. Для каждого участника создаём/обновляем запись в /userChats/{userId}/{chatId}
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

        // Выполняем многопутевое обновление (batch update) для атомарности
        Map<String, Object> updates = new HashMap<>();
        updates.put("groups/" + groupId, groupData);
        updates.put("chats/" + chatId, chatData);
        for (String uid : selectedUsers) {
            updates.put("userChats/" + uid + "/" + chatId, userChatEntry);
        }

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    createButton.setEnabled(true);
                    if (task.isSuccessful()) {
                        // Если есть аватар, загружаем его и обновляем поле avatarUrl в группе
                        if (selectedAvatarUri != null) {
                            uploadGroupAvatar(groupId, selectedAvatarUri);
                        }
                        Toast.makeText(CreateGroupActivity.this, "Группа создана!", Toast.LENGTH_SHORT).show();
                        // Открываем групповой чат и закрываем текущую активность
                        openGroupChat(chatId, groupId, groupName);
                        finish();
                    } else {
                        Log.e(TAG, "Ошибка создания группы", task.getException());
                        Toast.makeText(CreateGroupActivity.this, "Ошибка создания группы", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void openGroupChat(String chatId, String groupId, String groupName) {
        Intent intent = new Intent(this, GroupChatActivity.class);
        intent.putExtra("chatId", chatId);
        intent.putExtra("groupId", groupId);
        intent.putExtra("groupName", groupName);
        startActivity(intent);
    }

    // Загрузка аватара в Yandex Cloud (или Firebase Storage)
    private void uploadGroupAvatar(String groupId, Uri avatarUri) {
        // Здесь нужно реализовать загрузку (как в ChatActivity для файлов)
        // После успешной загрузки обновляем узел groups/{groupId}/avatarUrl
        // Для примера используем заглушку:
        Log.d(TAG, "Загрузка аватара для группы " + groupId);
        // TODO: реализация загрузки в облако
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}