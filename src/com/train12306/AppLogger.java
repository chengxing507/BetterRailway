package com.train12306;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppLogger {
    private static AppLogger instance;
    private final List<LogEntry> logs = new ArrayList<>();
    private static final int MAX_LOG = 200;
    private AppLogger() {}
    public static synchronized AppLogger getInstance() {
        if (instance == null) instance = new AppLogger();
        return instance;
    }
    public static void log(String tag, String msg) {
        getInstance().addLog(tag, msg);
    }
    private void addLog(String tag, String msg) {
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA).format(new Date());
        synchronized (logs) {
            logs.add(new LogEntry(time, tag, msg));
            if (logs.size() > MAX_LOG) logs.remove(0);
        }
    }
    public List<LogEntry> getLogs() {
        synchronized (logs) { return new ArrayList<>(logs); }
    }
    public String getLogText() {
        StringBuilder sb = new StringBuilder();
        synchronized (logs) {
            for (LogEntry e : logs)
                sb.append(e.time).append(" [").append(e.tag).append("] ").append(e.msg).append("\n");
        }
        return sb.toString();
    }
    public void clear() { synchronized (logs) { logs.clear(); } }
    public static class LogEntry {
        public final String time, tag, msg;
        LogEntry(String time, String tag, String msg) {
            this.time = time; this.tag = tag; this.msg = msg;
        }
    }
}