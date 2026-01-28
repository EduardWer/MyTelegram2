package com.example.mytelegram;

import android.util.Log;
import okhttp3.*;
import okio.BufferedSink;
import okio.Okio;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Base64;

public class YandexCloudUploader {

    private final String accessKey;
    private final String secretKey;
    private final OkHttpClient client;

    public YandexCloudUploader(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.client = new OkHttpClient.Builder()
                .addInterceptor(new LoggingInterceptor())
                .build();
    }

    public interface UploadCallback {
        void onSuccess(String fileUrl);
        void onError(String error);
        void onProgress(int progress);
    }

    public void uploadFile(File file, String bucket, String fileName, UploadCallback callback) {
        new UploadTask(file, bucket, fileName, callback).execute();
    }

    private class UploadTask extends android.os.AsyncTask<Void, Integer, String> {

        private final File file;
        private final String bucket;
        private final String fileName;
        private final UploadCallback callback;
        private Exception exception;

        public UploadTask(File file, String bucket, String fileName, UploadCallback callback) {
            this.file = file;
            this.bucket = bucket;
            this.fileName = fileName;
            this.callback = callback;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                return uploadToYandexCloud();
            } catch (Exception e) {
                this.exception = e;
                Log.e("YandexCloudUploader", "Upload error", e);
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (callback != null) {
                callback.onProgress(values[0]);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                if (callback != null) {
                    callback.onSuccess(result);
                }
            } else {
                if (callback != null) {
                    String errorMessage = exception != null ? exception.getMessage() : "Unknown error";
                    callback.onError(errorMessage);
                }
            }
        }

        private String uploadToYandexCloud() throws Exception {
            // URL для загрузки в Яндекс Облако
            String url = "https://storage.yandexcloud.net/" + bucket + "/" + fileName;

            // Создаем RequestBody с отслеживанием прогресса
            RequestBody requestBody = new CountingRequestBody(
                    RequestBody.create(file, MediaType.parse("image/jpeg")),
                    (bytesWritten, contentLength) -> {
                        int progress = (int) ((100 * bytesWritten) / contentLength);
                        publishProgress(progress);
                    }
            );

            // Создаем подпись для аутентификации
            String date = getCurrentDate();
            String signature = generateSignature("PUT", "", "image/jpeg", date, "/" + bucket + "/" + fileName);

            Request request = new Request.Builder()
                    .url(url)
                    .put(requestBody)
                    .header("Authorization", "AWS " + accessKey + ":" + signature)
                    .header("Date", date)
                    .header("Content-Type", "image/jpeg")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return url;
                } else {
                    throw new IOException("Server returned error: " + response.code() + " - " + response.message());
                }
            }
        }

        private String generateSignature(String method, String md5, String contentType, String date, String resource) throws Exception {
            String stringToSign = method + "\n" + md5 + "\n" + contentType + "\n" + date + "\n" + resource;

            Mac hmac = Mac.getInstance("HmacSHA1");
            hmac.init(new SecretKeySpec(secretKey.getBytes("UTF-8"), "HmacSHA1"));
            byte[] signature = hmac.doFinal(stringToSign.getBytes("UTF-8"));

            return Base64.getEncoder().encodeToString(signature);
        }

        private String getCurrentDate() {
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            return dateFormat.format(new Date());
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

    // Интерцептор для логирования
    private static class LoggingInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Log.d("YandexCloudUploader", "Sending request: " + request.url());

            Response response = chain.proceed(request);

            Log.d("YandexCloudUploader", "Received response: " + response.code());
            return response;
        }
    }
}