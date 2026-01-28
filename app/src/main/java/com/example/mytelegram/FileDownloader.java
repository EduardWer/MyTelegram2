package com.example.mytelegram;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FileDownloader {
    private Context context;

    public interface DownloadCallback {
        void onSuccess(File file);
        void onError(String error);
        void onProgress(int progress);
    }

    public FileDownloader(Context context) {
        this.context = context;
    }

    public void downloadFile(String fileUrl, String fileName, DownloadCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(fileUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                int fileLength = connection.getContentLength();

                // Создаем директорию для загрузок если её нет
                File downloadDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "Telegram");
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }

                File outputFile = new File(downloadDir, fileName);

                InputStream input = connection.getInputStream();
                FileOutputStream output = new FileOutputStream(outputFile);

                byte[] buffer = new byte[4096];
                int read;
                long total = 0;
                int progress = 0;

                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    total += read;

                    // Обновляем прогресс
                    if (fileLength > 0) {
                        int newProgress = (int) (total * 100 / fileLength);
                        if (newProgress > progress) {
                            progress = newProgress;
                            callback.onProgress(progress);
                        }
                    }
                }

                output.flush();
                output.close();
                input.close();

                callback.onSuccess(outputFile);

            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }
}