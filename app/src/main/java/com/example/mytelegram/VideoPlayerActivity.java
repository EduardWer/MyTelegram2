package com.example.mytelegram;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import cn.jzvd.Jzvd;
import cn.jzvd.JzvdStd;

public class VideoPlayerActivity extends AppCompatActivity {

    private JzvdStd jzVideoPlayer;
    private ImageButton backButton;
    private Button downloadButton;
    private TextView speedIndicator;
    private TextView qualityIndicator;

    private String videoUrl;
    private String videoTitle;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        videoUrl = getIntent().getStringExtra("video_url");
        videoTitle = getIntent().getStringExtra("video_title");

        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "Ошибка: видео не найдено", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupJZVideoPlayer();
        setupClickListeners();
        showNetworkIndicators();
    }

    private void initViews() {
        jzVideoPlayer = findViewById(R.id.jzVideoPlayer);
        backButton = findViewById(R.id.backButton);
        downloadButton = findViewById(R.id.downloadbutton);
        speedIndicator = findViewById(R.id.speedIndicator);
        qualityIndicator = findViewById(R.id.qualityIndicator);
    }

    private void setupJZVideoPlayer() {
        // Устанавливаем URL и заголовок
        jzVideoPlayer.setUp(videoUrl, videoTitle != null ? videoTitle : "Видео");

        // Автоматическое воспроизведение
        jzVideoPlayer.startVideo();

        // Обработка ошибок
        jzVideoPlayer.setOnDragListener((what, extra) -> {
            runOnUiThread(() -> {
                Toast.makeText(VideoPlayerActivity.this,
                        "Ошибка воспроизведения видео", Toast.LENGTH_LONG).show();
            });
            return true;
        });
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        downloadButton.setOnClickListener(v -> showDownloadDialog());
    }

    private void showDownloadDialog() {
        String[] qualities = {"Обычное качество", "Высокое качество", "Экономия трафика"};

        new AlertDialog.Builder(this)
                .setTitle("Скачать видео")
                .setItems(qualities, (dialog, which) -> {
                    String quality = qualities[which];
                    String fileName = generateFileName(quality);

                    // Показываем прогресс скачивания


                    // Получаем ключ объекта из URL
                    String objectKey = extractObjectKeyFromUrl(videoUrl);

                    // Создаем загрузчик
                    YandexCloudDownloader downloader = new YandexCloudDownloader(this);
                    Toast.makeText(VideoPlayerActivity.this,
                            "Видео загружается!!",
                            Toast.LENGTH_LONG).show();

                    // Устанавливаем слушатель
                    downloader.setDownloadListener(new YandexCloudDownloader.DownloadListener() {
                        @Override
                        public void onProgress(int progress) {
                            runOnUiThread(() -> updateDownloadProgress(progress, fileName));
                        }

                        @Override
                        public void onSuccess(File file) {
                            runOnUiThread(() -> {

                                Toast.makeText(VideoPlayerActivity.this,
                                        "✅ Видео сохранено: " + file.getAbsolutePath(),
                                        Toast.LENGTH_LONG).show();


                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {

                                Toast.makeText(VideoPlayerActivity.this,
                                        "❌ Ошибка: " + error,
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    });

                    // Запускаем скачивание в папку videos
                    downloader.downloadPublicVideo("server21", objectKey, fileName);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }


    private String extractObjectKeyFromUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath(); // /server21/chat_videos/uuid.mp4
            if (path != null && path.startsWith("/")) {
                // Убираем первый слеш и имя бакета (server21/)
                String withoutFirstSlash = path.substring(1); // server21/chat_videos/uuid.mp4
                int firstSlashIndex = withoutFirstSlash.indexOf('/');
                if (firstSlashIndex != -1) {
                    return withoutFirstSlash.substring(firstSlashIndex + 1); // chat_videos/uuid.mp4
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка парсинга URL: " + e.getMessage());
        }

        // Если не удалось распарсить, возвращаем имя файла
        return generateFileName("video") + ".mp4";
    }

    private void updateDownloadProgress(int progress, String fileName) {
        // Можно показывать прогресс в Toast или Snackbar



    }

    private String generateFileName(String quality) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String title = (videoTitle != null) ? videoTitle.replace(" ", "_") : "video";
        return title + "_" + quality + "_" + timeStamp + ".mp4";
    }

    private void showNetworkIndicators() {
        if (!isWifiConnected()) {
            speedIndicator.setText("📱 Мобильная сеть");
            speedIndicator.setVisibility(View.VISIBLE);

            qualityIndicator.setText("📶 Экономия трафика");
            qualityIndicator.setVisibility(View.VISIBLE);

            handler.postDelayed(() -> {
                speedIndicator.setVisibility(View.GONE);
                qualityIndicator.setVisibility(View.GONE);
            }, 3000);
        }
    }

    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifiInfo != null && wifiInfo.isConnected();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Jzvd.releaseAllVideos();
    }



    @Override
    public void onBackPressed() {
        if (Jzvd.backPress()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        Jzvd.releaseAllVideos();
    }
}