package com.example.mytelegram;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class CallActionReceiver extends BroadcastReceiver {

    private static final String TAG = "CallActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra("action");
        String callId = intent.getStringExtra("call_id");

        Log.d(TAG, "Получено действие: " + action + ", callId: " + callId);

        if (callId == null) return;

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if ("DECLINE".equals(action)) {
            // Убираем уведомление
            notificationManager.cancel(callId.hashCode());
            Log.d(TAG, "Звонок отклонён, уведомление убрано");

            // TODO: Отправить на сервер rejectCall(callId)
        }
    }
}