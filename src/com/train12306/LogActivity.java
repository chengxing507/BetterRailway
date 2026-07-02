package com.train12306;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class LogActivity extends Activity {
    private TextView tvLog;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        tvLog = (TextView) findViewById(R.id.tv_log);
        scrollView = (ScrollView) findViewById(R.id.scroll_log);
        Button btnRefresh = (Button) findViewById(R.id.btn_refresh);
        Button btnClear = (Button) findViewById(R.id.btn_clear);
        Button btnBack = (Button) findViewById(R.id.btn_back);
        TextView btnCopy = (TextView) findViewById(R.id.btn_copy_log);

        refreshLog();

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { refreshLog(); }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppLogger.getInstance().clear();
                tvLog.setText("");
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });

        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = tvLog.getText().toString();
                if (text.isEmpty()) {
                    Toast.makeText(LogActivity.this, "没有内容", Toast.LENGTH_SHORT).show();
                    return;
                }
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("log", text));
                Toast.makeText(LogActivity.this, "日志已复制", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshLog() {
        tvLog.setText(AppLogger.getInstance().getLogText());
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }
}