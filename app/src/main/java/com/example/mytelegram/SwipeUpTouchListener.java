package com.example.mytelegram;

import android.view.MotionEvent;
import android.view.View;

public class SwipeUpTouchListener implements View.OnTouchListener {

    private final IncomingCallActivity activity;
    private final Runnable onSwipeUp;
    private float startY;
    private boolean isSwiping = false;

    public SwipeUpTouchListener(IncomingCallActivity activity, Runnable onSwipeUp) {
        this.activity = activity;
        this.onSwipeUp = onSwipeUp;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startY = event.getRawY();
                isSwiping = true;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isSwiping) {
                    float deltaY = startY - event.getRawY();
                    v.setTranslationY(-Math.max(0, deltaY));

                    // Если свайпнули достаточно далеко — сразу ответить
                    if (deltaY > 300) {
                        isSwiping = false;
                        onSwipeUp.run();
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isSwiping) {
                    isSwiping = false;
                    v.animate().translationY(0).setDuration(200).start();
                }
                return true;
        }
        return false;
    }
}