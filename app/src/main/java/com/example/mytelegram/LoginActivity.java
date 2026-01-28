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

import com.example.mytelegram.MainActivity;
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

    private void checkAutoLogin() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            saveUserUid(currentUser.getUid());
            fetchUserDataAndNavigate(currentUser.getUid());
        }
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvRegister = findViewById(R.id.tvRegister);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());
        tvForgotPassword.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        tvRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (validateInputs(email, password)) {
            loginWithFirebase(email, password);
        }
    }

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

    private void loginWithFirebase(String email, String password) {
        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserUid(user.getUid());
                            fetchUserDataAndNavigate(user.getUid());
                        }
                    } else {
                        String error = task.getException() != null ?
                                task.getException().getMessage() : "Неизвестная ошибка";
                        Toast.makeText(this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Login error: " + error);
                    }
                });
    }

    private void saveUserUid(String uid) {
        getSharedPreferences(USER_PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_UID, uid)
                .apply();
    }

    private void fetchUserDataAndNavigate(String uid) {
        showLoading(true);

        database.getReference("users").child(uid).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        showLoading(false);
                        User user = snapshot.getValue(User.class);
                        if (user != null) {
                            navigateToMain(user);
                        } else {
                            Toast.makeText(LoginActivity.this,
                                    "Данные пользователя не найдены", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showLoading(false);
                        Toast.makeText(LoginActivity.this,
                                "Ошибка загрузки данных: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Database error: " + error.getMessage());
                    }
                });
    }

    private void navigateToMain(User user) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("user_data", (Parcelable)user);  // Передаем весь объект пользователя
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
        etEmail.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
    }
}