package com.example.mytelegram;


import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.File;

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


    // Создайте внутренний класс или отдельный файл
    private class MyDownloadListener implements YandexCloudDownloader.DownloadListener {
        private ProgressDialog progressDialog;
        private Context context;

        public MyDownloadListener(Context context, ProgressDialog progressDialog) {
            this.context = context;
            this.progressDialog = progressDialog;
        }

        @Override
        public void onProgress(int progress) {
            if (progressDialog != null) {
                progressDialog.setProgress(progress);
            }
        }

        @Override
        public void onSuccess(File file) {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            Toast.makeText(context, "Файл загружен: " + file.getName(), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onError(String error) {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            Toast.makeText(context, "Ошибка: " + error, Toast.LENGTH_LONG).show();
        }
    }

    // Использование:
    public void onButtonDownLoad(View view) {
        String fileUrl = getIntent().getStringExtra("image_url");
        String fileName = getIntent().getStringExtra("FileName");

        if (fileUrl == null || fileUrl.isEmpty()) {
            Toast.makeText(this, "Ошибка: URL не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        String finalFileName = (fileName != null && !fileName.isEmpty()) ?
                fileName : "file_" + System.currentTimeMillis() + ".jpg";

        YandexCloudDownloader downloader = new YandexCloudDownloader(this);

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Загрузка...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Используем наш класс-слушатель
        downloader.setDownloadListener(new MyDownloadListener(this, progressDialog));

        // Загрузка...
        try {
            Uri uri = Uri.parse(fileUrl);
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                String[] parts = path.substring(1).split("/", 2);
                if (parts.length == 2) {
                    downloader.downloadPublicFile(parts[0], parts[1], finalFileName);
                }
            }
        } catch (Exception e) {
            progressDialog.dismiss();
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

}