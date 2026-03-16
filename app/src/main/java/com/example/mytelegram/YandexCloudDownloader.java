package com.example.mytelegram;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class YandexCloudDownloader {
    private static final String TAG = "YandexCloud";
    private static final String BASE_FOLDER = "Pride"; // Базовая папка

    // Подпапки для разных типов файлов
    private static final String IMAGES_FOLDER = "images";
    private static final String DOCUMENTS_FOLDER = "documents";
    private static final String VIDEOS_FOLDER = "videos";
    private static final String AUDIO_FOLDER = "audio";
    private static final String OTHER_FOLDER = "other";

    private Context context;
    private DownloadListener listener;

    public interface DownloadListener {
        void onProgress(int progress);
        void onSuccess(File file);
        void onError(String error);
    }

    public YandexCloudDownloader(Context context) {
        this.context = context;
    }

    public void setDownloadListener(DownloadListener listener) {
        this.listener = listener;
    }

    /**
     * Скачивает файл с автоматическим определением типа и сохранением в соответствующую подпапку
     */
    public void downloadFile(String bucketName, String objectKey, String fileName, String iamToken) {
        new Thread(() -> {
            try {
                if (!isNetworkAvailable()) {
                    notifyError("Нет подключения к интернету");
                    return;
                }

                if (!checkPermissions()) {
                    notifyError("Нет разрешения на запись файлов");
                    return;
                }

                String urlString = "https://storage.yandexcloud.net/" + bucketName + "/" + objectKey;
                Log.d(TAG, "URL: " + urlString);

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + iamToken);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String contentType = connection.getContentType();
                    int contentLength = connection.getContentLength();

                    Log.d(TAG, "Content type: " + contentType);
                    Log.d(TAG, "Content length: " + contentLength);

                    // Определяем подпапку на основе типа файла
                    String subFolder = determineSubFolder(contentType, fileName);

                    // Получаем целевую папку (Pride/подпапка)
                    File targetFolder = getTargetFolder(subFolder);
                    if (targetFolder == null) {
                        notifyError("Не удалось создать папку " + subFolder);
                        return;
                    }

                    // Создаем файл с уникальным именем
                    File outputFile = getUniqueFileName(targetFolder, fileName);

                    // Проверяем свободное место
                    long freeSpace = targetFolder.getFreeSpace();
                    if (contentLength > freeSpace) {
                        notifyError("Недостаточно места на устройстве");
                        return;
                    }

                    // Скачиваем файл
                    downloadAndSave(connection, outputFile, contentLength);

                    Log.d(TAG, "Файл сохранен: " + outputFile.getAbsolutePath());

                    // Сканируем для галереи если это изображение или видео
                    if (subFolder.equals(IMAGES_FOLDER) || subFolder.equals(VIDEOS_FOLDER)) {
                        scanFileForGallery(outputFile);
                    }

                    notifySuccess(outputFile);

                } else {
                    handleErrorResponse(connection, responseCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "Ошибка скачивания: " + e.getMessage(), e);
                notifyError("Ошибка: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Скачивает публичный файл (без токена)
     */
    public void downloadPublicFile(String bucketName, String objectKey, String fileName) {
        new Thread(() -> {
            try {
                if (!isNetworkAvailable()) {
                    notifyError("Нет подключения к интернету");
                    return;
                }

                String urlString = "https://storage.yandexcloud.net/" + bucketName + "/" + objectKey;
                Log.d(TAG, "Public URL: " + urlString);

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);

                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String authHeader = connection.getHeaderField("WWW-Authenticate");
                    if (authHeader != null) {
                        notifyError("Файл требует авторизации");
                        return;
                    }

                    String contentType = connection.getContentType();
                    int contentLength = connection.getContentLength();

                    // Определяем подпапку на основе типа файла
                    String subFolder = determineSubFolder(contentType, fileName);

                    // Получаем целевую папку
                    File targetFolder = getTargetFolder(subFolder);
                    if (targetFolder == null) {
                        notifyError("Не удалось создать папку " + subFolder);
                        return;
                    }

                    File outputFile = getUniqueFileName(targetFolder, fileName);

                    // Скачиваем файл
                    downloadAndSave(connection, outputFile, contentLength);

                    // Сканируем для галереи если это изображение или видео
                    if (subFolder.equals(IMAGES_FOLDER) || subFolder.equals(VIDEOS_FOLDER)) {
                        scanFileForGallery(outputFile);
                    }

                    notifySuccess(outputFile);

                } else {
                    notifyError("Ошибка сервера: " + responseCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "Ошибка: " + e.getMessage(), e);
                notifyError("Ошибка: " + e.getMessage());
            }
        }).start();
    }

    // ================ МЕТОДЫ ДЛЯ КОНКРЕТНЫХ ТИПОВ ФАЙЛОВ ================

    /**
     * Скачивает изображение в папку Pride/images
     */
    public void downloadImage(String bucketName, String objectKey, String fileName, String iamToken) {
        downloadToSpecificFolder(bucketName, objectKey, fileName, iamToken, IMAGES_FOLDER);
    }

    /**
     * Скачивает документ в папку Pride/documents
     */
    public void downloadDocument(String bucketName, String objectKey, String fileName, String iamToken) {
        downloadToSpecificFolder(bucketName, objectKey, fileName, iamToken, DOCUMENTS_FOLDER);
    }

    /**
     * Скачивает видео в папку Pride/videos
     */
    public void downloadVideo(String bucketName, String objectKey, String fileName, String iamToken) {
        downloadToSpecificFolder(bucketName, objectKey, fileName, iamToken, VIDEOS_FOLDER);
    }

    /**
     * Скачивает аудио в папку Pride/audio
     */
    public void downloadAudio(String bucketName, String objectKey, String fileName, String iamToken) {
        downloadToSpecificFolder(bucketName, objectKey, fileName, iamToken, AUDIO_FOLDER);
    }

    /**
     * Скачивает файл в указанную подпапку
     */
    private void downloadToSpecificFolder(String bucketName, String objectKey, String fileName,
                                          String iamToken, String subFolder) {
        new Thread(() -> {
            try {
                if (!isNetworkAvailable()) {
                    notifyError("Нет подключения к интернету");
                    return;
                }

                if (!checkPermissions()) {
                    notifyError("Нет разрешения на запись файлов");
                    return;
                }

                String urlString = "https://storage.yandexcloud.net/" + bucketName + "/" + objectKey;
                Log.d(TAG, "URL: " + urlString);

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                if (iamToken != null && !iamToken.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + iamToken);
                }
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    int contentLength = connection.getContentLength();

                    // Получаем целевую папку
                    File targetFolder = getTargetFolder(subFolder);
                    if (targetFolder == null) {
                        notifyError("Не удалось создать папку " + subFolder);
                        return;
                    }

                    File outputFile = getUniqueFileName(targetFolder, fileName);

                    // Скачиваем файл
                    downloadAndSave(connection, outputFile, contentLength);

                    // Сканируем для галереи если это изображение или видео
                    if (subFolder.equals(IMAGES_FOLDER) || subFolder.equals(VIDEOS_FOLDER)) {
                        scanFileForGallery(outputFile);
                    }

                    notifySuccess(outputFile);

                } else {
                    notifyError("Ошибка сервера: " + responseCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "Ошибка: " + e.getMessage(), e);
                notifyError("Ошибка: " + e.getMessage());
            }
        }).start();
    }

    // ================ ПУБЛИЧНЫЕ МЕТОДЫ ДЛЯ РАЗНЫХ ТИПОВ (БЕЗ ТОКЕНА) ================

    public void downloadPublicImage(String bucketName, String objectKey, String fileName) {
        downloadToSpecificFolder(bucketName, objectKey, fileName, null, IMAGES_FOLDER);
    }

    public void downloadPublicDocument(String bucketName, String objectKey, String fileName) {
        downloadToSpecificFolder(bucketName, objectKey, fileName, null, DOCUMENTS_FOLDER);
    }

    public void downloadPublicVideo(String bucketName, String objectKey, String fileName) {
        downloadToSpecificFolder(bucketName, objectKey, fileName, null, VIDEOS_FOLDER);
    }

    public void downloadPublicAudio(String bucketName, String objectKey, String fileName) {
        downloadToSpecificFolder(bucketName, objectKey, fileName, null, AUDIO_FOLDER);
    }

    // ================ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ================

    /**
     * Определяет подпапку на основе MIME-типа или расширения файла
     */
    private String determineSubFolder(String contentType, String fileName) {
        if (contentType != null) {
            if (contentType.startsWith("image/")) {
                return IMAGES_FOLDER;
            } else if (contentType.startsWith("video/")) {
                return VIDEOS_FOLDER;
            } else if (contentType.startsWith("audio/")) {
                return AUDIO_FOLDER;
            } else if (contentType.startsWith("text/") ||
                    contentType.equals("application/pdf") ||
                    contentType.contains("document") ||
                    contentType.contains("word") ||
                    contentType.contains("excel") ||
                    contentType.contains("presentation")) {
                return DOCUMENTS_FOLDER;
            }
        }

        // Если не удалось определить по contentType, пробуем по расширению
        String extension = getFileExtension(fileName).toLowerCase();

        if (isImageExtension(extension)) {
            return IMAGES_FOLDER;
        } else if (isVideoExtension(extension)) {
            return VIDEOS_FOLDER;
        } else if (isAudioExtension(extension)) {
            return AUDIO_FOLDER;
        } else if (isDocumentExtension(extension)) {
            return DOCUMENTS_FOLDER;
        }

        return OTHER_FOLDER;
    }

    /**
     * Получает целевую папку (Pride/подпапка)
     */
    private File getTargetFolder(String subFolderName) {
        File baseDir;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Для Android 10+ используем соответствующую публичную папку
            if (subFolderName.equals(IMAGES_FOLDER)) {
                baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            } else if (subFolderName.equals(VIDEOS_FOLDER)) {
                baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            } else if (subFolderName.equals(AUDIO_FOLDER)) {
                baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            } else if (subFolderName.equals(DOCUMENTS_FOLDER)) {
                baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            } else {
                baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            }
        } else {
            // Для старых версий используем корень
            baseDir = Environment.getExternalStorageDirectory();
        }

        // Создаем путь Pride/подпапка
        File prideFolder = new File(baseDir, BASE_FOLDER);
        File targetFolder = new File(prideFolder, subFolderName);

        if (!targetFolder.exists()) {
            boolean created = targetFolder.mkdirs();
            if (created) {
                Log.d(TAG, "Папка создана: " + targetFolder.getAbsolutePath());
            } else {
                Log.e(TAG, "Не удалось создать папку: " + targetFolder.getAbsolutePath());
                return null;
            }
        }

        return targetFolder;
    }

    /**
     * Скачивает и сохраняет файл
     */
    private void downloadAndSave(HttpURLConnection connection, File outputFile, int contentLength)
            throws Exception {

        InputStream input = connection.getInputStream();
        FileOutputStream output = new FileOutputStream(outputFile);

        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalBytesRead = 0;
        int lastProgress = 0;

        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;

            if (contentLength > 0) {
                int progress = (int) (totalBytesRead * 100 / contentLength);
                if (progress > lastProgress) {
                    lastProgress = progress;
                    notifyProgress(progress);
                    Log.d(TAG, "Прогресс: " + progress + "%");
                }
            }
        }

        output.flush();
        output.close();
        input.close();
        connection.disconnect();
    }

    /**
     * Получает уникальное имя файла (если файл существует, добавляет номер)
     */
    private File getUniqueFileName(File dir, String fileName) {
        File file = new File(dir, fileName);
        if (!file.exists()) return file;

        int lastDotIndex = fileName.lastIndexOf('.');

        if (lastDotIndex == -1) {
            // Файл без расширения
            int counter = 1;
            while (file.exists()) {
                file = new File(dir, fileName + "_" + counter);
                counter++;
            }
        } else {
            // Файл с расширением
            String name = fileName.substring(0, lastDotIndex);
            String ext = fileName.substring(lastDotIndex);
            int counter = 1;

            while (file.exists()) {
                file = new File(dir, name + "_" + counter + ext);
                counter++;
            }
        }

        return file;
    }

    /**
     * Получает расширение файла
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    /**
     * Проверяет, является ли расширение расширением изображения
     */
    private boolean isImageExtension(String extension) {
        return extension.equals("jpg") || extension.equals("jpeg") ||
                extension.equals("png") || extension.equals("gif") ||
                extension.equals("bmp") || extension.equals("webp") ||
                extension.equals("svg") || extension.equals("ico");
    }

    /**
     * Проверяет, является ли расширение расширением видео
     */
    private boolean isVideoExtension(String extension) {
        return extension.equals("mp4") || extension.equals("avi") ||
                extension.equals("mkv") || extension.equals("mov") ||
                extension.equals("wmv") || extension.equals("flv") ||
                extension.equals("webm") || extension.equals("m4v") ||
                extension.equals("3gp");
    }

    /**
     * Проверяет, является ли расширение расширением аудио
     */
    private boolean isAudioExtension(String extension) {
        return extension.equals("mp3") || extension.equals("wav") ||
                extension.equals("ogg") || extension.equals("flac") ||
                extension.equals("aac") || extension.equals("m4a") ||
                extension.equals("wma") || extension.equals("opus");
    }

    /**
     * Проверяет, является ли расширение расширением документа
     */
    private boolean isDocumentExtension(String extension) {
        return extension.equals("pdf") || extension.equals("doc") ||
                extension.equals("docx") || extension.equals("txt") ||
                extension.equals("rtf") || extension.equals("odt") ||
                extension.equals("xls") || extension.equals("xlsx") ||
                extension.equals("ppt") || extension.equals("pptx");
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return true; // Для Android 11+ используем MediaStore
        } else {
            int result = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return result == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void handleErrorResponse(HttpURLConnection connection, int responseCode) {
        try {
            InputStream errorStream = connection.getErrorStream();
            if (errorStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String errorMessage = "Ошибка " + responseCode + ": " + response.toString();
                Log.e(TAG, errorMessage);
                notifyError(errorMessage);
            } else {
                notifyError("Ошибка сервера: " + responseCode);
            }
        } catch (Exception e) {
            notifyError("Ошибка сервера: " + responseCode);
        }
    }

    private void scanFileForGallery(File file) {
        MediaScannerConnection.scanFile(context,
                new String[]{file.getAbsolutePath()},
                null,
                (path, uri) -> Log.d(TAG, "Файл просканирован: " + path));
    }

    private void notifyProgress(int progress) {
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    listener.onProgress(progress));
        }
    }

    private void notifySuccess(File file) {
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    listener.onSuccess(file));
        }
    }

    private void notifyError(String error) {
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    listener.onError(error));
        }
    }
}