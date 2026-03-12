package com.example.mytelegram;


import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class FullImageActivity extends AppCompatActivity {

    private ImageView fullImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_image);

        // Инициализация ImageView
        fullImage = findViewById(R.id.fullImage);

        // Получаем URL изображения из Intent
        String imageUrl = getIntent().getStringExtra("image_url");

        // Загружаем изображение с помощью Glide
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_image_placeholder) // Плейсхолдер пока грузится
                    .error(R.drawable.ic_broken_image) // Ошибка загрузки
                    .into(fullImage);
        }

        // Закрыть активность по клику на изображение
        fullImage.setOnClickListener(v -> finish());
    }
}