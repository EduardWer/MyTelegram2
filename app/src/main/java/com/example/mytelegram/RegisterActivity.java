package com.example.mytelegram;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;


public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword, etConfirmPassword, etUsername;
    private FirebaseAuth mAuth;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        initViews();
        setupListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        findViewById(R.id.btnRegister).setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        String username = etUsername.getText().toString().trim();

        if (validateInputs(email, password, confirmPassword, username)) {
            registerWithFirebase(email, password, username);
        }
    }

    private boolean validateInputs(String email, String password, String confirmPassword, String username) {
        boolean isValid = true;

        if (username.isEmpty()) {
            etUsername.setError("Введите имя пользователя");
            isValid = false;
        }

        if (email.isEmpty()) {
            etEmail.setError("Введите email");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Некорректный email");
            isValid = false;
        }

        if (password.isEmpty()) {
            etPassword.setError("Введите пароль");
            isValid = false;
        } else if (password.length() < 6) {
            etPassword.setError("Пароль должен быть не менее 6 символов");
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Пароли не совпадают");
            isValid = false;
        }

        return isValid;
    }

    private void registerWithFirebase(String email, String password, String username) {
        showProgress(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            // Отправляем письмо для подтверждения почты
                            firebaseUser.sendEmailVerification()
                                    .addOnCompleteListener(verificationTask -> {
                                        showProgress(false);

                                        if (verificationTask.isSuccessful()) {
                                            // Сохраняем пользователя в базу
                                            saveUserToDatabase(firebaseUser.getUid(), email, username);
                                            Toast.makeText(this, "Письмо для подтверждения отправлено на " + email,
                                                    Toast.LENGTH_LONG).show();
                                        } else {
                                            // Все равно сохраняем пользователя, но показываем ошибку
                                            saveUserToDatabase(firebaseUser.getUid(), email, username);
                                            String error = verificationTask.getException() != null ?
                                                    verificationTask.getException().getMessage() : "Ошибка отправки";
                                            Toast.makeText(this, "Аккаунт создан, но не удалось отправить письмо: " + error,
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                        }
                    } else {
                        showProgress(false);
                        String error = task.getException() != null ?
                                task.getException().getMessage() : "Неизвестная ошибка";
                        Toast.makeText(this, "Ошибка регистрации: " + error,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToDatabase(String uid, String email, String username) {
        User newUser = new User(username, email, System.currentTimeMillis());
        newUser.setUid(uid); // Устанавливаем UID

        FirebaseDatabase.getInstance().getReference()
                .child("users")
                .child(uid)
                .setValue(newUser)
                .addOnCompleteListener(task -> {
                    showProgress(false);

                    if (task.isSuccessful()) {
                        navigateToLoginWithUserData(newUser);
                    } else {
                        Toast.makeText(this, "Ошибка сохранения данных пользователя",
                                Toast.LENGTH_SHORT).show();
                        // Удаляем пользователя если не удалось сохранить данные
                        mAuth.getCurrentUser().delete();
                    }
                });
    }

    private void navigateToLoginWithUserData(User user) {
        // Выходим из аккаунта после регистрации
        mAuth.signOut();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("user_data", user); // Передаем объект пользователя
        startActivity(intent);
        finish();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        etEmail.setEnabled(!show);
        etUsername.setEnabled(!show);
        etPassword.setEnabled(!show);
        etConfirmPassword.setEnabled(!show);
        findViewById(R.id.btnRegister).setEnabled(!show);
    }
}