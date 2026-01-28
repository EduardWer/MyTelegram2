package com.example.mytelegram;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
import java.util.List;

public class ChouseContactActivity extends AppCompatActivity {

    private RecyclerView contactsRecyclerView;
    private ContactAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyStateText;
    private SearchView searchView;
    private final List<User> allContacts = new ArrayList<>();
    private final List<User> filteredContacts = new ArrayList<>();
    private DatabaseReference databaseReference;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chouse_contact);

        // Инициализация Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Инициализация View элементов ДО загрузки данных
        initViews();
        setupRecyclerView();
        setupSearchView();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Загрузка контактов после инициализации View
        loadContacts();
    }

    private void initViews() {
        // Находим все View элементы по их ID
        contactsRecyclerView = findViewById(R.id.contactsRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyStateText = findViewById(R.id.emptyStateText);
        searchView = findViewById(R.id.searchView);

        // Проверка на null для безопасности
        if (progressBar == null) {
            Log.e("ChouseContactActivity", "ProgressBar not found in layout");
        }
        if (emptyStateText == null) {
            Log.e("ChouseContactActivity", "emptyStateText not found in layout");
        }
    }

    private void setupRecyclerView() {
        adapter = new ContactAdapter(filteredContacts, new ContactAdapter.OnContactClickListener() {
            @Override
            public void onContactClick(User user) {
                createOrOpenChat(user);
            }
        });

        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setAdapter(adapter);
    }

    private void setupSearchView() {
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    filterContacts(newText);
                    return true;
                }
            });

            // Убираем подчеркивание у SearchView
            searchView.setBackground(null);
            searchView.setQueryHint("Поиск контактов...");
        }
    }

    private void filterContacts(String query) {
        filteredContacts.clear();

        if (TextUtils.isEmpty(query)) {
            // Если запрос пустой, показываем все контакты
            filteredContacts.addAll(allContacts);
        } else {
            // Фильтруем контакты по имени или email
            String lowerCaseQuery = query.toLowerCase().trim();
            for (User user : allContacts) {
                String username = user.getUsername() != null ? user.getUsername().toLowerCase() : "";
                String email = user.getEmail() != null ? user.getEmail().toLowerCase() : "";

                if (username.contains(lowerCaseQuery) || email.contains(lowerCaseQuery)) {
                    filteredContacts.add(user);
                }
            }
        }

        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void createOrOpenChat(User selectedUser) {
        if (currentUser == null) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUserId = currentUser.getUid();
        String recipientId = selectedUser.getUid();

        // Создаем ID чата в формате user1_user2 (отсортированный)
        String chatId = generateChatId(currentUserId, recipientId);

        // Проверяем, существует ли уже такой чат
        databaseReference.child("chats").child(chatId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            // Чат уже существует - открываем его
                            openExistingChat(chatId, selectedUser);
                        } else {
                            // Создаем новый чат
                            createNewChat(chatId, currentUserId, recipientId, selectedUser);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Toast.makeText(ChouseContactActivity.this,
                                "Ошибка проверки чата", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String generateChatId(String user1, String user2) {
        // Сортируем ID пользователей для единообразия
        if (user1.compareTo(user2) < 0) {
            return user1 + "_" + user2;
        } else {
            return user2 + "_" + user1;
        }
    }

    private void createNewChat(String chatId, String currentUserId, String recipientId, User selectedUser) {
        // Создаем структуру чата
        DatabaseReference chatRef = databaseReference.child("chats").child(chatId);

        // Вместо пустой строки создаем пустой объект для messages
        chatRef.child("messages").child("empty").setValue("delete_me") // временное значение
                .addOnSuccessListener(aVoid -> {
                    // Удаляем временное значение, оставляя пустую структуру
                    chatRef.child("messages").child("empty").removeValue()
                            .addOnSuccessListener(aVoid2 -> {
                                // Создаем записи в userChats для обоих пользователей
                                createUserChatEntries(chatId, currentUserId, recipientId, selectedUser);

                                Log.d("ChatCreation", "Создан новый чат: " + chatId);
                                Toast.makeText(ChouseContactActivity.this,
                                        "Чат создан", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatCreation", "Ошибка создания чата: " + e.getMessage());
                    Toast.makeText(ChouseContactActivity.this,
                            "Ошибка создания чата", Toast.LENGTH_SHORT).show();
                });
    }

    private void createUserChatEntries(String chatId, String currentUserId, String recipientId, User selectedUser) {
        long timestamp = System.currentTimeMillis();

        // Создаем объект Chat для userChats
        Chat chatForCurrentUser = new Chat();
        chatForCurrentUser.setChatId(chatId);
        chatForCurrentUser.setParticipantId(recipientId);
        chatForCurrentUser.setLastMessage("Чат создан");
        chatForCurrentUser.setTimestamp(timestamp);
        chatForCurrentUser.setUnreadCount(0);
        chatForCurrentUser.setLastMessageSenderId(currentUserId);

        Chat chatForRecipient = new Chat();
        chatForRecipient.setChatId(chatId);
        chatForRecipient.setParticipantId(currentUserId);
        chatForRecipient.setLastMessage("Чат создан");
        chatForRecipient.setTimestamp(timestamp);
        chatForRecipient.setUnreadCount(1); // Для получателя 1 непрочитанное
        chatForRecipient.setLastMessageSenderId(currentUserId);

        // Сохраняем для текущего пользователя
        databaseReference.child("userChats")
                .child(currentUserId)
                .child(recipientId)
                .setValue(chatForCurrentUser);

        // Сохраняем для получателя
        databaseReference.child("userChats")
                .child(recipientId)
                .child(currentUserId)
                .setValue(chatForRecipient)
                .addOnSuccessListener(aVoid -> {
                    // После успешного создания чата - открываем его
                    openChatActivity(chatId, selectedUser);
                });
    }

    private void openExistingChat(String chatId, User selectedUser) {
        // Просто открываем существующий чат
        openChatActivity(chatId, selectedUser);
        Toast.makeText(this, "Открыт существующий чат", Toast.LENGTH_SHORT).show();
    }

    private void openChatActivity(String chatId, User selectedUser) {
        Intent chatIntent = new Intent(ChouseContactActivity.this, ChatActivity.class);
        chatIntent.putExtra("chatId", chatId);
        chatIntent.putExtra("recipientId", selectedUser.getUid());
        chatIntent.putExtra("recipientName", selectedUser.getUsername());
        startActivity(chatIntent);
        finish();
    }

    private void loadContacts() {

        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        if (emptyStateText != null) {
            emptyStateText.setVisibility(View.GONE);
        }

        if (currentUser == null) {
            hideProgressBar();
            showError("Пользователь не авторизован");
            return;
        }

        String currentUserEmail = currentUser.getEmail();
        if (currentUserEmail == null || currentUserEmail.isEmpty()) {
            hideProgressBar();
            showError("Email не найден");
            return;
        }

        // Извлекаем домен из email текущего пользователя
        String currentDomain = extractDomain(currentUserEmail);
        if (currentDomain == null) {
            hideProgressBar();
            showError("Некорректный формат email");
            return;
        }

        Log.d("DomainDebug", "Текущий email: " + currentUserEmail);
        Log.d("DomainDebug", "Извлеченный домен: " + currentDomain);

        databaseReference.child("users")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        allContacts.clear();
                        List<User> matchingUsers = new ArrayList<>();

                        int totalUsers = 0;
                        int matchedUsers = 0;

                        for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                            totalUsers++;
                            User user = dataSnapshot.getValue(User.class);

                            if (user != null && !user.getUid().equals(currentUser.getUid())) {
                                String userEmail = user.getEmail();

                                if (userEmail != null && !userEmail.isEmpty()) {
                                    String userDomain = extractDomain(userEmail);

                                    // Проверяем совпадение доменов
                                    if (userDomain != null && userDomain.equalsIgnoreCase(currentDomain)) {
                                        // Загружаем аватар для пользователя
                                        loadUserAvatar(user);
                                        allContacts.add(user);
                                        matchingUsers.add(user);
                                        matchedUsers++;
                                        Log.d("DomainDebug", "ДОБАВЛЕН: " + userEmail + " | Домен: " + userDomain);
                                    } else {
                                        Log.d("DomainDebug", "Пропущен: " + userEmail + " | Домен: " + userDomain);
                                    }
                                }
                            }
                        }

                        hideProgressBar();

                        // Обновляем список контактов
                        filteredContacts.clear();
                        filteredContacts.addAll(allContacts);
                        adapter.notifyDataSetChanged();

                        Log.d("DomainDebug", "Всего пользователей: " + totalUsers);
                        Log.d("DomainDebug", "С совпадающим доменом: " + matchedUsers);
                        Log.d("DomainDebug", "Добавлено в список: " + allContacts.size());

                        updateEmptyState();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        hideProgressBar();
                        showError("Ошибка загрузки: " + error.getMessage());
                        Log.e("DomainDebug", "Firebase error: " + error.getMessage());
                        updateEmptyState();
                    }
                });
    }

    private void loadUserAvatar(User user) {
        if (user == null || user.getUid() == null) return;

        // Загружаем аватар из Firebase Storage или базы данных
        DatabaseReference avatarRef = databaseReference.child("avatars").child(user.getUid());
        avatarRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String avatarUrl = dataSnapshot.getValue(String.class);
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        user.setAvatarUrl(avatarUrl);
                        // Уведомляем адаптер об обновлении
                        adapter.notifyDataSetChanged();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("AvatarLoad", "Ошибка загрузки аватара: " + databaseError.getMessage());
            }
        });
    }

    private String extractDomain(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        email = email.trim();
        int atIndex = email.indexOf('@');

        // Проверяем что @ существует и не является последним символом
        if (atIndex == -1 || atIndex == email.length() - 1) {
            return null;
        }

        // Извлекаем домен (все после @) и приводим к нижнему регистру
        return email.substring(atIndex + 1).toLowerCase();
    }

    private void hideProgressBar() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        if (emptyStateText != null) {
            emptyStateText.setText(message);
            emptyStateText.setVisibility(View.VISIBLE);
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void updateEmptyState() {
        if (emptyStateText != null) {
            if (filteredContacts.isEmpty()) {
                if (allContacts.isEmpty()) {
                    emptyStateText.setText("Нет пользователей с вашим доменом email");
                } else {
                    emptyStateText.setText("Контакты не найдены");
                }
                emptyStateText.setVisibility(View.VISIBLE);
            } else {
                emptyStateText.setVisibility(View.GONE);
            }
        }
    }

    // Вспомогательный метод для получения контекста
    private Context getContext() {
        return this;
    }
}