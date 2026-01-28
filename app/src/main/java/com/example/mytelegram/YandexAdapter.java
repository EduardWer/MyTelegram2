package com.example.mytelegram;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.*;
import okio.BufferedSink;
import okio.Okio;

public class YandexAdapter {
    private static final String TAG = "YandexCloudUploader";

    private final String accessKey;
    private final String secretKey;
    private final OkHttpClient client;
    private final Context context;

    public YandexAdapter(Context context, String accessKey, String secretKey) {
        this.context = context;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.client = new OkHttpClient.Builder().build();
    }

    public interface UploadCallback {
        void onSuccess(String fileUrl);
        void onError(String error);
        void onProgress(int progress);
    }

    public void uploadFile(Uri fileUri, String fileType, UploadCallback callback) {
        new Thread(() -> {
            try {
                // Создаем временный файл из Uri
                File tempFile = createTempFileFromUri(fileUri);
                if (tempFile == null) {
                    callback.onError("Не удалось создать временный файл");
                    return;
                }

                // Генерируем имя файла
                String fileName = generateFileName(fileType, fileUri);
                String bucketName = "your-bucket-name"; // ЗАМЕНИТЕ на ваш бакет
                String mimeType = getMimeType(fileType);

                // Загружаем файл
                String fileUrl = uploadToYandexCloud(tempFile, bucketName, fileName, mimeType, callback);

                // Удаляем временный файл
                tempFile.delete();

                callback.onSuccess(fileUrl);

            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки: " + e.getMessage(), e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private String uploadToYandexCloud(File file, String bucket, String fileName, String mimeType, UploadCallback callback) throws Exception {
        String url = "https://storage.yandexcloud.net/" + bucket + "/" + fileName;

        // Создаем RequestBody с отслеживанием прогресса
        RequestBody requestBody = new CountingRequestBody(
                RequestBody.create(file, MediaType.parse(mimeType)),
                (bytesWritten, contentLength) -> {
                    int progress = (int) ((100 * bytesWritten) / contentLength);
                    callback.onProgress(progress);
                }
        );

        // Создаем подпись для аутентификации (AWS Signature v2)
        String date = getCurrentDate();
        String signature = generateSignature("PUT", "", mimeType, date, "/" + bucket + "/" + fileName);

        Request request = new Request.Builder()
                .url(url)
                .put(requestBody)
                .header("Authorization", "AWS " + accessKey + ":" + signature)
                .header("Date", date)
                .header("Content-Type", mimeType)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return url;
            } else {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException("Server returned error: " + response.code() + " - " + errorBody);
            }
        }
    }

    private String generateSignature(String method, String md5, String contentType, String date, String resource) throws Exception {
        String stringToSign = method + "\n" + md5 + "\n" + contentType + "\n" + date + "\n" + resource;

        Mac hmac = Mac.getInstance("HmacSHA1");
        hmac.init(new SecretKeySpec(secretKey.getBytes("UTF-8"), "HmacSHA1"));
        byte[] signature = hmac.doFinal(stringToSign.getBytes("UTF-8"));

        return android.util.Base64.encodeToString(signature, android.util.Base64.NO_WRAP);
    }

    private String getCurrentDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(new Date());
    }

    private File createTempFileFromUri(Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            String fileName = getFileNameFromUri(uri);
            File tempFile = new File(context.getCacheDir(), "upload_" + System.currentTimeMillis() + "_" + fileName);

            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            outputStream.close();
            inputStream.close();
            return tempFile;

        } catch (Exception e) {
            Log.e(TAG, "Ошибка создания временного файла", e);
            return null;
        }
    }

    private String generateFileName(String fileType, Uri fileUri) {
        String originalName = getFileNameFromUri(fileUri);
        String extension = getFileExtension(originalName);
        String uuid = UUID.randomUUID().toString();

        if (extension.isEmpty()) {
            extension = getDefaultExtension(fileType);
        }

        switch (fileType) {
            case "image":
                return "images/" + uuid + "." + extension;
            case "video":
                return "videos/" + uuid + "." + extension;
            case "document":
                return "documents/" + uuid + (extension.isEmpty() ? "" : "." + extension);
            default:
                return "files/" + uuid + (extension.isEmpty() ? "" : "." + extension);
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения имени файла", e);
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "file";
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot == -1) ? "" : fileName.substring(lastDot + 1).toLowerCase();
    }

    private String getDefaultExtension(String fileType) {
        switch (fileType) {
            case "image": return "jpg";
            case "video": return "mp4";
            default: return "";
        }
    }

    private String getMimeType(String fileType) {
        switch (fileType) {
            case "image": return "image/jpeg";
            case "video": return "video/mp4";
            case "document": return "application/octet-stream";
            default: return "application/octet-stream";
        }
    }

    // Класс для отслеживания прогресса загрузки
    private static class CountingRequestBody extends RequestBody {
        private final RequestBody delegate;
        private final ProgressListener listener;

        public CountingRequestBody(RequestBody delegate, ProgressListener listener) {
            this.delegate = delegate;
            this.listener = listener;
        }

        @Override
        public MediaType contentType() {
            return delegate.contentType();
        }

        @Override
        public long contentLength() throws IOException {
            return delegate.contentLength();
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            CountingSink countingSink = new CountingSink(sink);
            BufferedSink bufferedSink = Okio.buffer(countingSink);
            delegate.writeTo(bufferedSink);
            bufferedSink.flush();
        }

        protected final class CountingSink extends okio.ForwardingSink {
            private long bytesWritten = 0;

            public CountingSink(okio.Sink delegate) {
                super(delegate);
            }

            @Override
            public void write(okio.Buffer source, long byteCount) throws IOException {
                super.write(source, byteCount);
                bytesWritten += byteCount;
                listener.onProgress(bytesWritten, contentLength());
            }
        }

        public interface ProgressListener {
            void onProgress(long bytesWritten, long contentLength);
        }
    }
}