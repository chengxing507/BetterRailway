package com.train12306;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private EditText etBaseUrl, etApiKey, etModelName, etMcpUrl;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("ai_config", MODE_PRIVATE);

        etBaseUrl = (EditText) findViewById(R.id.et_base_url);
        etApiKey = (EditText) findViewById(R.id.et_api_key);
        etModelName = (EditText) findViewById(R.id.et_model_name);
        etMcpUrl = (EditText) findViewById(R.id.et_mcp_url);
        Button btnSave = (Button) findViewById(R.id.btn_save);
        Button btnTest = (Button) findViewById(R.id.btn_test);
        Button btnTestMcp = (Button) findViewById(R.id.btn_test_mcp);
        Button btnBack = (Button) findViewById(R.id.btn_back);
        Button btnLog = (Button) findViewById(R.id.btn_log);

        etBaseUrl.setText(prefs.getString("base_url",
                "https://api.sensenova.cn/v1/chat/completions"));
        etApiKey.setText(prefs.getString("api_key", ""));
        etModelName.setText(prefs.getString("model_name", "deepseek-v4-flash"));
        etMcpUrl.setText(prefs.getString("mcp_url", "http://localhost:3000/mcp"));

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit()
                        .putString("base_url", etBaseUrl.getText().toString().trim())
                        .putString("api_key", etApiKey.getText().toString().trim())
                        .putString("model_name", etModelName.getText().toString().trim())
                        .putString("mcp_url", etMcpUrl.getText().toString().trim())
                        .apply();
                Toast.makeText(SettingsActivity.this, "配置已保存", Toast.LENGTH_SHORT).show();
            }
        });

        ButtonGuard.guard(btnTest, new Runnable() {
            @Override
            public void run() {
                final String url = etBaseUrl.getText().toString().trim();
                final String key = etApiKey.getText().toString().trim();
                final String model = etModelName.getText().toString().trim();

                if (url.isEmpty() || key.isEmpty()) {
                    Toast.makeText(SettingsActivity.this, "请先填写API地址和密钥", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(SettingsActivity.this, "测试连接中...", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AIAnalysisClient ai = new AIAnalysisClient(url, key, model);
                            final String result = ai.testConnection();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(SettingsActivity.this, result, Toast.LENGTH_LONG).show();
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(SettingsActivity.this,
                                            "连接失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }).start();
            }
        });

                ButtonGuard.guard(btnTestMcp, new Runnable() {
            @Override
            public void run() {
                final String mcpUrl = etMcpUrl.getText().toString().trim();

                if (mcpUrl.isEmpty()) {
                    Toast.makeText(SettingsActivity.this, "请先填写MCP服务器地址", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(SettingsActivity.this, "测试MCP连接中...", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            MCPClient mcp = new MCPClient(mcpUrl);
                            String result = mcp.testConnection();
                            final String msg = "MCP连接成功！\n返回: " +
                                    (result.length() > 100 ? result.substring(0, 100) + "..." : result);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show();
                                }
                            });
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(SettingsActivity.this,
                                            "MCP连接失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, LogActivity.class));
            }
        });
    }
}