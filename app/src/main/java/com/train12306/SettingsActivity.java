package com.train12306;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 设置页面 — AI API & MCP 配置
 * <p>
 * 功能：
 * - 配置 AI API 地址/密钥/模型名
 * - 配置 MCP 服务器地址
 * - 测试连接（AI + MCP 分开测试）
 * - 配置持久化（SharedPreferences）
 */
public class SettingsActivity extends Activity {

    private EditText etBaseUrl, etApiKey, etModelName, etMcpUrl;
    private TextView tvMcpStatus, tvAiStatus;
    private ProgressBar progressBar;
    private SharedPreferences prefs;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final int REQUEST_EXPORT_CONFIG = 1001;
    private static final int REQUEST_IMPORT_CONFIG = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("ai_config", MODE_PRIVATE);

        etBaseUrl = findViewById(R.id.et_base_url);
        etApiKey = findViewById(R.id.et_api_key);
        etModelName = findViewById(R.id.et_model_name);
        etMcpUrl = findViewById(R.id.et_mcp_url);
        tvMcpStatus = findViewById(R.id.tv_mcp_status);
        tvAiStatus = findViewById(R.id.tv_ai_status);
        progressBar = findViewById(R.id.progress_bar);

        Button btnSave = findViewById(R.id.btn_save);
        Button btnTest = findViewById(R.id.btn_test);
        Button btnTestMcp = findViewById(R.id.btn_test_mcp);
        Button btnBack = findViewById(R.id.btn_back);
        Button btnLog = findViewById(R.id.btn_log);
        Button btnExport = findViewById(R.id.btn_export_config);
        Button btnImport = findViewById(R.id.btn_import_config);

        // 加载已保存的配置
        loadSavedConfig();

        btnSave.setOnClickListener(v -> saveConfig());

        ButtonGuard.guard(btnTest, () -> testAiConnection());
        ButtonGuard.guard(btnTestMcp, () -> testMcpConnection());
        btnBack.setOnClickListener(v -> finish());
        btnLog.setOnClickListener(v ->
                startActivity(new Intent(SettingsActivity.this, LogActivity.class)));

        btnExport.setOnClickListener(v -> exportConfig());
        btnImport.setOnClickListener(v -> importConfig());
    }

    /**
     * 加载已保存的配置到输入框
     */
    private void loadSavedConfig() {
        etBaseUrl.setText(prefs.getString("base_url",
                "https://api.sensenova.cn/v1/chat/completions"));
        etApiKey.setText(prefs.getString("api_key", ""));
        etModelName.setText(prefs.getString("model_name", "deepseek-v4-flash"));
        etMcpUrl.setText(prefs.getString("mcp_url", "http://localhost:3000/mcp"));
    }

    /**
     * 保存配置到 SharedPreferences
     */
    private void saveConfig() {
        String baseUrl = etBaseUrl.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        String modelName = etModelName.getText().toString().trim();
        String mcpUrl = etMcpUrl.getText().toString().trim();

        // 基本校验
        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "API 地址不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "API 密钥不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (modelName.isEmpty()) {
            Toast.makeText(this, "模型名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mcpUrl.isEmpty()) {
            Toast.makeText(this, "MCP 地址不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit()
                .putString("base_url", baseUrl)
                .putString("api_key", apiKey)
                .putString("model_name", modelName)
                .putString("mcp_url", mcpUrl)
                .apply();

        Toast.makeText(this, "✅ 配置已保存", Toast.LENGTH_SHORT).show();
        AppLogger.log("SETTINGS", "配置已保存: baseUrl=" + baseUrl + ", model=" + modelName);

        // 保存后清空 MCP 缓存，下次使用新地址
        MCPClient.clearStationCache();
    }

    /**
     * 测试 AI API 连接
     */
    private void testAiConnection() {
        final String url = etBaseUrl.getText().toString().trim();
        final String key = etApiKey.getText().toString().trim();
        final String model = etModelName.getText().toString().trim();

        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, "请先填写 API 地址和密钥", Toast.LENGTH_SHORT).show();
            return;
        }

        tvAiStatus.setText("⏳ 测试连接中...");
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                AIAnalysisClient ai = new AIAnalysisClient(url, key, model);
                final String result = ai.testConnection();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvAiStatus.setText("✅ " + result);
                    Toast.makeText(SettingsActivity.this, result, Toast.LENGTH_LONG).show();
                });
            } catch (final Exception e) {
                AppLogger.error("SETTINGS", "AI 连接测试失败: " + e.getMessage());
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvAiStatus.setText("❌ 连接失败: " + e.getMessage());
                    Toast.makeText(SettingsActivity.this,
                            "AI 连接失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * 测试 MCP 连接
     */
    private void testMcpConnection() {
        final String mcpUrl = etMcpUrl.getText().toString().trim();

        if (mcpUrl.isEmpty()) {
            Toast.makeText(this, "请先填写 MCP 服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }

        tvMcpStatus.setText("⏳ 测试 MCP 连接中...");
        progressBar.setVisibility(View.VISIBLE);
        AppLogger.log("SETTINGS", "测试 MCP 连接: " + mcpUrl);

        new Thread(() -> {
            try {
                MCPClient mcp = new MCPClient(mcpUrl);
                String result = mcp.testConnection();
                final String msg = "MCP 连接成功！";
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvMcpStatus.setText("✅ " + msg);
                    Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            } catch (final Exception e) {
                AppLogger.error("SETTINGS", "MCP 连接测试失败: " + e.getMessage());
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvMcpStatus.setText("❌ 连接失败: " + e.getMessage());
                    Toast.makeText(SettingsActivity.this,
                            "MCP 连接失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ======================== 导出 / 导入 配置 ========================

    /**
     * 导出配置为 JSON 文件
     */
    private void exportConfig() {
        // 用当前输入框的值生成 JSON
        JsonObject config = new JsonObject();
        config.addProperty("base_url", etBaseUrl.getText().toString().trim());
        config.addProperty("api_key", etApiKey.getText().toString().trim());
        config.addProperty("model_name", etModelName.getText().toString().trim());
        config.addProperty("mcp_url", etMcpUrl.getText().toString().trim());

        JsonObject root = new JsonObject();
        root.addProperty("version", "2.0");
        root.addProperty("export_time",
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
        root.add("config", config);

        final String json = gson.toJson(root);
        AppLogger.log("SETTINGS", "导出配置: " + json);

        // 用 SAF 让用户选择保存位置
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "better_railway_config.json");
        startActivityForResult(intent, REQUEST_EXPORT_CONFIG);
    }

    /**
     * 从 JSON 文件导入配置
     */
    private void importConfig() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_CONFIG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == REQUEST_EXPORT_CONFIG) {
            handleExportResult(uri);
        } else if (requestCode == REQUEST_IMPORT_CONFIG) {
            handleImportResult(uri);
        }
    }

    private void handleExportResult(Uri uri) {
        String json = buildConfigJson();
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os != null) {
                os.write(json.getBytes("UTF-8"));
                os.flush();
            }
            Toast.makeText(this, "✅ 配置已导出", Toast.LENGTH_SHORT).show();
            AppLogger.log("SETTINGS", "配置导出成功");
        } catch (Exception e) {
            AppLogger.error("SETTINGS", "导出失败: " + e.getMessage());
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleImportResult(Uri uri) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        } catch (Exception e) {
            Toast.makeText(this, "读取文件失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(sb.toString()).getAsJsonObject();
            JsonObject config = root.getAsJsonObject("config");

            String baseUrl = getJsonString(config, "base_url");
            String apiKey = getJsonString(config, "api_key");
            String modelName = getJsonString(config, "model_name");
            String mcpUrl = getJsonString(config, "mcp_url");

            etBaseUrl.setText(baseUrl);
            etApiKey.setText(apiKey);
            etModelName.setText(modelName);
            etMcpUrl.setText(mcpUrl);

            // 自动保存
            prefs.edit()
                    .putString("base_url", baseUrl)
                    .putString("api_key", apiKey)
                    .putString("model_name", modelName)
                    .putString("mcp_url", mcpUrl)
                    .apply();

            MCPClient.clearStationCache();

            Toast.makeText(this, "✅ 配置已导入并保存", Toast.LENGTH_SHORT).show();
            AppLogger.log("SETTINGS", "配置导入成功: model=" + modelName);
        } catch (Exception e) {
            Toast.makeText(this, "解析配置文件失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            AppLogger.error("SETTINGS", "导入解析失败: " + e.getMessage());
        }
    }

    private String buildConfigJson() {
        JsonObject config = new JsonObject();
        config.addProperty("base_url", etBaseUrl.getText().toString().trim());
        config.addProperty("api_key", etApiKey.getText().toString().trim());
        config.addProperty("model_name", etModelName.getText().toString().trim());
        config.addProperty("mcp_url", etMcpUrl.getText().toString().trim());

        JsonObject root = new JsonObject();
        root.addProperty("version", "2.0");
        root.addProperty("export_time",
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
        root.add("config", config);
        return gson.toJson(root);
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }
}