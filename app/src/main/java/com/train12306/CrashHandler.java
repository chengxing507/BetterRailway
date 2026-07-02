package com.train12306;

import android.os.Looper;
import android.widget.Toast;

/**
 * 全局未捕获异常处理器
 * 捕获崩溃并将日志写入 AppLogger，方便排查
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static boolean installed = false;

    public static void install() {
        if (installed) return;
        installed = true;
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler());
        AppLogger.log("CRASH", "全局崩溃捕获已安装");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("线程: ").append(thread.getName()).append("\n");
        sb.append("异常: ").append(ex.toString()).append("\n");

        for (StackTraceElement e : ex.getStackTrace()) {
            sb.append("  at ").append(e.toString()).append("\n");
        }

        // 写入日志
        AppLogger.error("CRASH", sb.toString());

        // 尝试在 UI 线程弹出提示
        try {
            Looper.prepare();
            Toast.makeText(
                    AppHolder.getApp(),
                    "出错啦！请查看运行日志了解详情",
                    Toast.LENGTH_LONG).show();
            Looper.loop();
        } catch (Exception ignored) {}

        // 交给系统默认处理
        if (Thread.getDefaultUncaughtExceptionHandler() != this) {
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, ex);
        }
    }
}