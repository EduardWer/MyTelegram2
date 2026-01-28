package com.example.mytelegram;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class UserProfileActivity extends AppCompatActivity {

    private ImageView profileAvatar;
    private EditText profileNameEdit;
    private EditText profileEmailEdit;
    private EditText profileDepartmentEdit;
    private EditText profileBioEdit;
    private MaterialButton sendMessageButton;

    private DatabaseReference databaseReference;

    private String chatId;
    private String recipientId;
    private String recipientName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        initializeViews();
        initializeFirebase();

        // Получаем ID оппонента из Intent
        chatId = getIntent().getStringExtra("chatId");
        recipientId = getIntent().getStringExtra("user_id");
        recipientName = getIntent().getStringExtra("user_name");

        if (recipientId != null) {
            loadUserInfo();
        } else {
            Toast.makeText(this, "Ошибка: ID пользователя не указан", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializeViews() {
        profileAvatar = findViewById(R.id.profile_avatar);
        profileNameEdit = findViewById(R.id.profile_name_edit);
        profileEmailEdit = findViewById(R.id.profile_email_edit);
        profileDepartmentEdit = findViewById(R.id.profile_department_edit);
        profileBioEdit = findViewById(R.id.profile_bio_edit);
        sendMessageButton = findViewById(R.id.send_message_button);
    }

    private void initializeFirebase() {
        databaseReference = FirebaseDatabase.getInstance().getReference();
    }

    private void loadUserInfo() {
        // Загрузка аватара
        DatabaseReference avatarRef = FirebaseDatabase.getInstance()
                .getReference("avatars")
                .child(recipientId);

        avatarRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String avatarUrl = dataSnapshot.getValue(String.class);
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        Glide.with(UserProfileActivity.this)
                                .load(avatarUrl)
                                .placeholder(R.mipmap.ic_launcher_round)
                                .error(R.mipmap.ic_launcher_round)
                                .circleCrop()
                                .into(profileAvatar);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(UserProfileActivity.this, "Ошибка загрузки аватара", Toast.LENGTH_SHORT).show();
            }
        });

        // Загрузка информации о пользователе
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(recipientId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Загрузка имени
                    if (recipientName == null) {
                        String name = dataSnapshot.child("username").getValue(String.class);
                        if (name != null) {
                            profileNameEdit.setText(name);
                            recipientName = name;
                        } else {
                            profileNameEdit.setText("Не указано");
                        }
                    } else {
                        profileNameEdit.setText(recipientName);
                    }

                    // Загрузка email
                    String email = dataSnapshot.child("email").getValue(String.class);
                    if (email != null && !email.isEmpty()) {
                        profileEmailEdit.setText(email);
                    } else {
                        profileEmailEdit.setText("Не указан");
                    }

                    // Загрузка отдела
                    String department = dataSnapshot.child("department").getValue(String.class);
                    if (department != null && !department.isEmpty()) {
                        profileDepartmentEdit.setText(department);
                    } else {
                        profileDepartmentEdit.setText("Не указан");
                    }

                    // Загрузка био
                    String bio = dataSnapshot.child("bio").getValue(String.class);
                    if (bio != null && !bio.isEmpty()) {
                        profileBioEdit.setText(bio);
                    } else {
                        profileBioEdit.setText("Сведения отсутствуют");
                    }


                    // Загрузка статуса (если нужно)
                    String status = dataSnapshot.child("status").getValue(String.class);
                    // Можно добавить TextView для статуса если нужно

                } else {
                    Toast.makeText(UserProfileActivity.this, "Данные пользователя не найдены", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(UserProfileActivity.this, "Ошибка загрузки данных пользователя", Toast.LENGTH_SHORT).show();
            }
        });

        setupSendMessageButton();
    }

    private void setupSendMessageButton() {
        sendMessageButton.setOnClickListener(v -> {
            // Логика отправки сообщения
            // Например, открыть активность чата с этим пользователем
            Toast.makeText(UserProfileActivity.this, "Открыть чат с " + recipientName, Toast.LENGTH_SHORT).show();


             Intent intent = new Intent(UserProfileActivity.this, ChatActivity.class);
             intent.putExtra("chatId",chatId);
             intent.putExtra("recipientId", recipientId);
             intent.putExtra("recipientName", recipientName);
             startActivity(intent);
        });

        // Скрываем кнопку если это профиль текущего пользователя
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null && recipientId.equals(auth.getCurrentUser().getUid())) {
            sendMessageButton.setVisibility(android.view.View.GONE);
        }
    }
}