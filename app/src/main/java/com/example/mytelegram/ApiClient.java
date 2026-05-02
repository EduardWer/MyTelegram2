package com.example.mytelegram;

import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ApiClient {

    private static final String BASE_URL = "http://192.168.31.163:8000";  // Замените на ваш IP
    private static final String TAG = "ApiClient";

    /**
     * Отправляет FCM-токен на сервер
     */
    public static void registerToken(String userId, String token, String platform, String username) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/register-token");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                // Формируем JSON
                JSONObject json = new JSONObject();
                json.put("user_id", userId);
                json.put("token", token);
                json.put("platform", platform);
                json.put("username", username);

                // Отправляем
                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    Log.d(TAG, "Токен успешно зарегистрирован на сервере");
                    // Читаем ответ
                    java.util.Scanner scanner = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
                    String response = scanner.hasNext() ? scanner.next() : "";
                    Log.d(TAG, "Ответ сервера: " + response);
                } else {
                    Log.e(TAG, "Ошибка регистрации токена. Код: " + responseCode);
                }

                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки токена: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Удаляет токен (при выходе из аккаунта)
     */
    public static void unregisterToken(String userId, String token) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/unregister-token");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                JSONObject json = new JSONObject();
                json.put("user_id", userId);
                json.put("token", token);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Токен удалён. Код: " + responseCode);

                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Ошибка удаления токена: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Отправляет запрос на отправку push-уведомления
     */
    public static void sendPush(String token, String title, String body,
                                String chatId, String messageId, String senderId) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/send-push");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                JSONObject json = new JSONObject();
                json.put("token", token);
                json.put("title", title);
                json.put("body", body);
                json.put("chat_id", chatId);
                json.put("message_id", messageId);
                json.put("sender_id", senderId);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Push отправлен. Код: " + responseCode);

                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки push: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Отклоняет звонок (сообщает серверу)
     */
    public static void rejectCall(String callId) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/reject-call");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("call_id", callId);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Ошибка отклонения звонка: " + e.getMessage());
            }
        }).start();
    }
}