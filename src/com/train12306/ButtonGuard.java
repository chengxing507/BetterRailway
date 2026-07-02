package com.train12306;

import android.view.View;

public class ButtonGuard {
    private static long lastClickTime = 0;
    public static void guard(View v, final Runnable action) {
        v.setOnClickListener(new View.OnClickListener() {
            private boolean busy = false;
            @Override
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (busy || now - lastClickTime < 1500) return;
                lastClickTime = now;
                busy = true;
                v.setEnabled(false);
                action.run();
                v.postDelayed(new Runnable() {
                    @Override
                    public void run() { busy = false; v.setEnabled(true); }
                }, 1500);
            }
        });
    }
}