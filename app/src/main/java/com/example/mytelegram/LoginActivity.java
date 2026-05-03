package com.example.mytelegram;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final String USER_PREFS = "UserPrefs";
    private static final String KEY_UID = "user_uid";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_EMAIL = "user_email";

    private TextInputEditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvForgotPassword, tvRegister;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance();

        initViews();
        setupListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkAutoLogin();
    }

    /**
     * Проверка автоматического входа
     */
    private void checkAutoLogin() {
        // Проверяем сохраненное состояние входа
        boolean isLoggedIn = getSharedPreferences(USER_PREFS, MODE_PRIVATE)
                .getBoolean(KEY_IS_LOGGED_IN, false);

        if (!isLoggedIn) {
            Log.d(TAG, "Нет сохраненной сессии, требуется вход");
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Проверяем, что пользователь существует в FirebaseAuth
        if (currentUser == null) {
            Log.d(TAG, "Пользователь не найден в FirebaseAuth");
            clearLoginState();
            return;
        }

        // Проверяем, что email не пустой
        if (currentUser.getEmail() == null || currentUser.getEmail().isEmpty()) {
            Log.d(TAG, "Email пользователя отсутствует");
            clearLoginState();
            return;
        }

        // Проверяем, что email подтвержден (рекомендуется)
        if (!currentUser.isEmailVerified()) {
            Log.d(TAG, "Email не подтвержден: " + currentUser.getEmail());
            Toast.makeText(this, "Пожалуйста, подтвердите ваш email", Toast.LENGTH_LONG).show();
            logoutAndClear();
            return;
        }

        // Проверяем соответствие сохраненного UID
        String savedUid = getSharedPreferences(USER_PREFS, MODE_PRIVATE)
                .getString(KEY_UID, null);

        String savedEmail = getSharedPreferences(USER_PREFS, MODE_PRIVATE)
                .getString(KEY_USER_EMAIL, null);

        if (savedUid == null || !savedUid.equals(currentUser.getUid())) {
            Log.w(TAG, "UID не совпадает, требуется повторный вход");
            logoutAndClear();
            return;
        }

        if (savedEmail != null && !savedEmail.equals(currentUser.getEmail())) {
            Log.w(TAG, "Email не совпадает, требуется повторный вход");
            logoutAndClear();
            return;
        }

        Log.d(TAG, "Автоматический вход для: " + currentUser.getEmail());
        fetchUserDataAndNavigate(currentUser.getUid());
    }

    /**
     * Инициализация View компонентов
     */
    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvRegister = findViewById(R.id.tvRegister);
        progressBar = findViewById(R.id.progressBar);
    }

    /**
     * Настройка слушателей
     */
    private void setupListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());
        tvForgotPassword.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        tvRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    /**
     * Попытка входа
     */
    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (validateInputs(email, password)) {
            loginWithFirebase(email, password);
        }
    }

    /**
     * Валидация полей ввода
     */
    private boolean validateInputs(String email, String password) {
        boolean isValid = true;

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
        }

        return isValid;
    }

    /**
     * Вход через Firebase Authentication
     */
    private void loginWithFirebase(String email, String password) {
        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);

                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();

                        if (user == null) {
                            showError("Ошибка: пользователь не найден");
                            logoutAndClear();
                            return;
                        }

                        if (user.getEmail() == null || user.getEmail().isEmpty()) {
                            showError("Ошибка: email отсутствует");
                            logoutAndClear();
                            return;
                        }

                        // Проверка подтверждения email (опционально, но рекомендуется)
                        if (!user.isEmailVerified()) {
                            showError("Пожалуйста, подтвердите ваш email перед входом");
                            mAuth.signOut();
                            clearLoginState();
                            return;
                        }

                        Log.d(TAG, "Успешный вход: " + user.getEmail());
                        saveUserLoginState(user.getUid(), user.getEmail());
                        fetchUserDataAndNavigate(user.getUid());

                    } else {
                        String error = task.getException() != null ?
                                task.getException().getMessage() : "Неизвестная ошибка";
                        Log.e(TAG, "Ошибка входа: " + error);
                        showError("Ошибка входа: " + error);
                        clearLoginState();
                    }
                });
    }

    /**
     * Сохранение состояния входа
     */
    private void saveUserLoginState(String uid, String email) {
        getSharedPreferences(USER_PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_UID, uid)
                .putString(KEY_USER_EMAIL, email)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
        Log.d(TAG, "Сохранена сессия для: " + email);
    }

    /**
     * Очистка состояния входа
     */
    private void clearLoginState() {
        getSharedPreferences(USER_PREFS, MODE_PRIVATE)
                .edit()
                .remove(KEY_UID)
                .remove(KEY_USER_EMAIL)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .apply();
        Log.d(TAG, "Сессия очищена");
    }

    /**
     * Выход и очистка всех данных
     */
    private void logoutAndClear() {
        if (mAuth.getCurrentUser() != null) {
            mAuth.signOut();
        }
        clearLoginState();
    }

    /**
     * Получение данных пользователя из базы и переход в MainActivity
     */
    private void fetchUserDataAndNavigate(String uid) {
        showLoading(true);

        database.getReference("users").child(uid).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        showLoading(false);

                        if (!snapshot.exists()) {
                            Log.e(TAG, "Пользователь не найден в БД: " + uid);
                            showError("Пользователь не найден в базе данных");
                            logoutAndClear();
                            return;
                        }

                        User user = snapshot.getValue(User.class);

                        if (user == null) {
                            Log.e(TAG, "Ошибка: user = null");
                            showError("Ошибка загрузки данных пользователя");
                            logoutAndClear();
                            return;
                        }

                        if (user.getUid() == null || user.getUid().isEmpty()) {
                            Log.e(TAG, "Ошибка: UID пользователя отсутствует");
                            showError("Некорректные данные пользователя");
                            logoutAndClear();
                            return;
                        }

                        // Проверяем соответствие UID
                        if (!user.getUid().equals(uid)) {
                            Log.e(TAG, "UID не совпадает: " + user.getUid() + " != " + uid);
                            showError("Ошибка верификации пользователя");
                            logoutAndClear();
                            return;
                        }

                        Log.d(TAG, "Данные загружены: " + user.getUsername() + " (" + user.getEmail() + ")");
                        navigateToMain(user);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showLoading(false);
                        Log.e(TAG, "Ошибка базы данных: " + error.getMessage());
                        showError("Ошибка загрузки данных: " + error.getMessage());
                        logoutAndClear();
                    }
                });
    }

    /**
     * Переход в MainActivity
     */
    private void navigateToMain(User user) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("user_data", user);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Показ сообщения об ошибке
     */
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Показ/скрытие загрузки
     */
    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
        btnLogin.setText(isLoading ? "Вход..." : "Войти");
        etEmail.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
        tvForgotPassword.setEnabled(!isLoading);
        tvRegister.setEnabled(!isLoading);
    }
}