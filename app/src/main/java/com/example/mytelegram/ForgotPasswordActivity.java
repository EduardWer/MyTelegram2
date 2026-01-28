package com.example.mytelegram;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputEditText etEmail;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();
        etEmail = findViewById(R.id.etEmail);

        // Обработчик кнопки сброса пароля
        findViewById(R.id.btnResetPassword).setOnClickListener(v -> attemptPasswordReset());

        // Обработчик возврата к экрану входа
        findViewById(R.id.tvBackToLogin).setOnClickListener(v -> finish());
    }

    private void attemptPasswordReset() {
        String email = etEmail.getText().toString().trim();

        if (validateEmail(email)) {
            sendPasswordResetEmail(email);
        }
    }

    private boolean validateEmail(String email) {
        if (email.isEmpty()) {
            etEmail.setError("Введите email");
            return false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Некорректный email");
            return false;
        }

        return true;
    }

    private void sendPasswordResetEmail(String email) {
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    findViewById(R.id.progressBar).setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Ссылка для сброса пароля отправлена на ваш email",
                                Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(this,
                                "Ошибка: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}