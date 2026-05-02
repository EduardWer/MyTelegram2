package com.example.mytelegram;

import android.view.MotionEvent;
import android.view.View;

public class SwipeDownTouchListener implements View.OnTouchListener {

    private final IncomingCallActivity activity;
    private final Runnable onSwipeDown;
    private float startY;
    private boolean isSwiping = false;

    public SwipeDownTouchListener(IncomingCallActivity activity, Runnable onSwipeDown) {
        this.activity = activity;
        this.onSwipeDown = onSwipeDown;
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
                    float deltaY = event.getRawY() - startY;
                    v.setTranslationY(Math.max(0, deltaY));

                    if (deltaY > 300) {
                        isSwiping = false;
                        onSwipeDown.run();
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