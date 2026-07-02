package com.train12306;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

/**
 * 主页面 — 12306 智能助手
 * <p>
 * 功能：
 * - 输入出发/到达站、选择日期
 * - 车次类型筛选 (G/D/Z/T/K)
 * - 发起车次查询
 * - MCP 预初始化（进入页面时异步初始化，减少查询等待）
 */
public class MainActivity extends Activity {

    private EditText etFrom, etTo;
    private Button btnDate;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private String selectedDate = "";
    private String stationFromName = "", stationToName = "";
    private String filterFlags = "";

    /** MCP 是否已预初始化 */
    private boolean mcpReady = false;
    private String mcpUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initListeners();
        preInitMcp();
    }

    private void initViews() {
        etFrom = findViewById(R.id.et_from);
        etTo = findViewById(R.id.et_to);
        btnDate = findViewById(R.id.btn_date);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);

        Button btnSwap = findViewById(R.id.btn_swap);
        Button btnQuery = findViewById(R.id.btn_query);
        Button btnSettings = findViewById(R.id.btn_settings);
        Button btnFilter = findViewById(R.id.btn_filter);
        Button btnLog = findViewById(R.id.btn_log);

        // 设置默认日期为今天
        Calendar cal = Calendar.getInstance();
        selectedDate = String.format("%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
        btnDate.setText(selectedDate);

        btnDate.setOnClickListener(v -> showDatePicker());
        btnSwap.setOnClickListener(v -> swapStations());

        ButtonGuard.guard(btnSettings, () ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        ButtonGuard.guard(btnLog, () ->
                startActivity(new Intent(MainActivity.this, LogActivity.class)));

        btnFilter.setOnClickListener(v -> showFilterDialog());

        ButtonGuard.guard(btnQuery, () -> {
            stationFromName = etFrom.getText().toString().trim();
            stationToName = etTo.getText().toString().trim();
            if (stationFromName.isEmpty() || stationToName.isEmpty()) {
                Toast.makeText(MainActivity.this, "请输入出发站和到达站", Toast.LENGTH_SHORT).show();
                return;
            }
            doQuery();
        });
    }

    private void initListeners() {
        // 预留：后续添加 AutoCompleteTextView 站名自动补全
    }

    // ======================== MCP 预初始化 ========================

    /**
     * 进入页面时异步预初始化 MCP 连接
     * 大幅减少用户点击查询后的等待时间
     */
    private void preInitMcp() {
        SharedPreferences prefs = getSharedPreferences("ai_config", MODE_PRIVATE);
        mcpUrl = prefs.getString("mcp_url", "http://localhost:3000/mcp");

        if (mcpUrl.isEmpty()) {
            tvStatus.setText("⚠️ 请先在「AI API 设置」中配置 MCP 地址");
            return;
        }

        tvStatus.setText("⏳ 正在连接 MCP 服务器...");
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                MCPClient mcp = new MCPClient(mcpUrl);
                mcp.testConnection();
                mcpReady = true;
                runOnUiThread(() -> {
                    tvStatus.setText("✅ MCP 已就绪");
                    progressBar.setVisibility(View.GONE);
                    AppLogger.log("MAIN", "MCP 预初始化成功");
                });
            } catch (Exception e) {
                AppLogger.warn("MAIN", "MCP 预初始化失败: " + e.getMessage());
                runOnUiThread(() -> {
                    tvStatus.setText("⚠️ MCP 连接失败，查询时自动重试");
                    progressBar.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    // ======================== 日期选择 ========================

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    btnDate.setText(selectedDate);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));
        // 不允许选择过去的日期
        dpd.getDatePicker().setMinDate(System.currentTimeMillis() - 86400000);
        dpd.show();
    }

    // ======================== 站点交换 ========================

    private void swapStations() {
        String tmp = etFrom.getText().toString();
        etFrom.setText(etTo.getText().toString());
        etTo.setText(tmp);
        String tmpName = stationFromName;
        stationFromName = stationToName;
        stationToName = tmpName;
    }

    // ======================== 车次筛选 ========================

    private void showFilterDialog() {
        final String[] items = {"高铁 G", "动车 D", "直达 Z", "特快 T", "快速 K"};
        final boolean[] checked = {false, false, false, false, false};
        final String[] codes = {"G", "D", "Z", "T", "K"};

        new AlertDialog.Builder(this)
                .setTitle("车次筛选")
                .setMultiChoiceItems(items, checked, null)
                .setPositiveButton("确定", (dialog, which) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) sb.append(codes[i]);
                    }
                    filterFlags = sb.toString();
                    String msg = filterFlags.isEmpty() ? "显示全部车次" : "已筛选: " + filterFlags;
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    tvStatus.setText(msg);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ======================== 查询核心 ========================

    private void doQuery() {
        final String from = stationFromName;
        final String to = stationToName;
        final String date = selectedDate;
        final String url = mcpUrl;

        AppLogger.log("QUERY", "开始查询: " + from + " -> " + to + " | " + date);

        // 从设置读取 MCP 地址（如果预初始化时未读取到）
        if (url.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("ai_config", MODE_PRIVATE);
            mcpUrl = prefs.getString("mcp_url", "http://localhost:3000/mcp");
        }

        // 更新 UI 状态
        tvStatus.setText("⏳ 查询中...");
        progressBar.setVisibility(View.VISIBLE);

        Toast.makeText(this, "正在查询车票...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                MCPClient mcp = new MCPClient(mcpUrl);

                // 步骤1: 查询出发站代码
                AppLogger.log("QUERY", "查询站点代码: " + from);
                updateStatus("正在查询 " + from + " 的站点代码...");
                String fromResult = mcp.getStationCode(from);

                // 步骤2: 查询到达站代码
                AppLogger.log("QUERY", "查询站点代码: " + to);
                updateStatus("正在查询 " + to + " 的站点代码...");
                String toResult = mcp.getStationCode(to);

                final String fromCode = StationCodeParser.parseFirstCode(from, fromResult);
                final String toCode = StationCodeParser.parseFirstCode(to, toResult);

                AppLogger.log("QUERY", "站点码: " + from + "=" + fromCode + ", " + to + "=" + toCode);

                if (fromCode == null || fromCode.isEmpty() || toCode == null || toCode.isEmpty()) {
                    final String detail;
                    if (fromCode == null || fromCode.isEmpty()) {
                        detail = "未找到站点「" + from + "」的代码";
                    } else {
                        detail = "未找到站点「" + to + "」的代码";
                    }
                    runOnUiThread(() -> {
                        tvStatus.setText("❌ " + detail);
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this, detail, Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                // 步骤3: 查询车次
                updateStatus("正在查询 " + date + " 的车次...");
                final String ticketResult = mcp.getTickets(date, fromCode, toCode, filterFlags);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Intent intent = new Intent(MainActivity.this, TicketListActivity.class);
                    intent.putExtra("ticket_data", ticketResult);
                    intent.putExtra("query_date", date);
                    intent.putExtra("from_station", from);
                    intent.putExtra("to_station", to);
                    startActivity(intent);
                    tvStatus.setText("✅ 查询完成，共查询 " + from + " → " + to + " 车次");
                });

            } catch (final Exception e) {
                AppLogger.error("QUERY", "查询失败: " + e.getMessage());
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("❌ 查询失败: " + e.getMessage());
                    Toast.makeText(MainActivity.this,
                            "查询失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * 在 UI 线程更新状态文字
     */
    private void updateStatus(final String msg) {
        runOnUiThread(() -> tvStatus.setText(msg));
    }
}