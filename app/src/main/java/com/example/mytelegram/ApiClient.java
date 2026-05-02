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

    // ==================== ОТПРАВКА ОБЫЧНЫХ PUSH ====================

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
     * Отправка push с картинкой
     */
    public static void sendPushWithImage(String token, String title, String body,
                                         String chatId, String messageId,
                                         String senderId, String imageUrl) {
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
                json.put("image_url", imageUrl);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Push с картинкой отправлен. Код: " + responseCode);

                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки push с картинкой: " + e.getMessage());
            }
        }).start();
    }

    // ==================== ОТПРАВКА В ГРУППУ ====================

    /**
     * Отправляет push-уведомление всем участникам группы
     * @param groupId ID группы
     * @param title Заголовок уведомления
     * @param body Текст уведомления
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param senderId ID отправителя (будет исключен из рассылки)
     * @param excludeSender Исключать ли отправителя из рассылки
     */
    public static void sendToGroup(String groupId, String title, String body,
                                   String chatId, String messageId,
                                   String senderId, boolean excludeSender) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/send-to-group");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                JSONObject json = new JSONObject();
                json.put("group_id", groupId);
                json.put("title", title);
                json.put("body", body);
                json.put("chat_id", chatId);
                json.put("message_id", messageId);
                json.put("sender_id", senderId);
                json.put("exclude_sender", excludeSender);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Отправка в группу. Код: " + responseCode);

                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки в группу: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Отправляет push-уведомление в группу с картинкой
     */
    public static void sendToGroupWithImage(String groupId, String title, String body,
                                            String chatId, String messageId,
                                            String senderId, String imageUrl,
                                            boolean excludeSender) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/send-to-group");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                JSONObject json = new JSONObject();
                json.put("group_id", groupId);
                json.put("title", title);
                json.put("body", body);
                json.put("chat_id", chatId);
                json.put("message_id", messageId);
                json.put("sender_id", senderId);
                json.put("image_url", imageUrl);
                json.put("exclude_sender", excludeSender);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Отправка в группу с картинкой. Код: " + responseCode);

                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки в группу с картинкой: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Отправляет в группу через batch-эндпоинт (асинхронная обработка)
     * @param groupId ID группы
     * @param title Заголовок
     * @param body Текст
     * @param chatId ID чата
     *
     * @param senderId ID отправителя
     * @param excludeSender Исключать отправителя
     * @param callback Колбэк для получения результата
     */
    public static void sendToGroupBatch(String groupId, String title, String body,
                                        String chatId,
                                        String senderId, boolean excludeSender,
                                        ApiCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/send-to-group");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                JSONObject json = new JSONObject();
                json.put("group_id", groupId);
                json.put("title", title);
                json.put("body", body);
                json.put("chat_id", chatId);
                json.put("sender_id", senderId);
                json.put("exclude_sender", true);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();

                if (callback != null) {
                    if (responseCode == 200) {
                        callback.onSuccess("Уведомление отправлено в группу");
                    } else {
                        callback.onError("Ошибка: " + responseCode);
                    }
                }

                Log.d(TAG, "Batch отправка в группу. Код: " + responseCode);

                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Ошибка batch отправки: " + e.getMessage());
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        }).start();
    }

    // ==================== ОТПРАВКА ПОЛЬЗОВАТЕЛЮ ====================

    /**
     * Отправляет push на все устройства пользователя
     */



    // ==================== КОЛБЭК ИНТЕРФЕЙС ====================

    public interface ApiCallback {
        void onSuccess(String message);
        void onError(String error);
    }


}