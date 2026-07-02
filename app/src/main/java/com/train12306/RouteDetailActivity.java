package com.train12306;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 路线详情页面 — 显示指定车次的经停站列表
 * <p>
 * 特性：
 * - 三态 UI：加载中 / 空数据 / 正常列表
 * - AI 分析入口（携带详细路线数据）
 */
public class RouteDetailActivity extends Activity {

    private ListView listView;
    private TextView tvHeader, tvEmpty, tvLoading;
    private ProgressBar progressBar;

    private final List<String> routeStations = new ArrayList<>();
    private String trainCode, queryDate;
    private String fromStation, toStation, startTime, arriveTime, duration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_detail);

        listView = findViewById(R.id.list_route);
        tvHeader = findViewById(R.id.tv_route_header);
        tvEmpty = findViewById(R.id.tv_empty);
        tvLoading = findViewById(R.id.tv_loading);
        progressBar = findViewById(R.id.progress_bar);

        Button btnAnalyze = findViewById(R.id.btn_analyze);
        Button btnBack = findViewById(R.id.btn_back);

        // 获取传入数据
        trainCode = getIntent().getStringExtra("train_code");
        queryDate = getIntent().getStringExtra("query_date");
        fromStation = getIntent().getStringExtra("from_station");
        toStation = getIntent().getStringExtra("to_station");
        startTime = getIntent().getStringExtra("start_time");
        arriveTime = getIntent().getStringExtra("arrive_time");
        duration = getIntent().getStringExtra("duration");

        // 更新头部信息
        tvHeader.setText(trainCode + " | " + fromStation + " → " + toStation
                + " | " + startTime + " - " + arriveTime + " | " + duration);

        // 显示加载中
        showLoading();

        // 加载路线
        loadRoute();

        ButtonGuard.guard(btnAnalyze, () -> openAIAnalysis());
        btnBack.setOnClickListener(v -> finish());
    }

    private void showLoading() {
        tvLoading.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        listView.setVisibility(View.GONE);
    }

    /**
     * 异步加载经停站列表
     */
    private void loadRoute() {
        final String mcpUrl = getSharedPreferences("ai_config", MODE_PRIVATE)
                .getString("mcp_url", "http://localhost:3000/mcp");

        Toast.makeText(this, "加载路线中...", Toast.LENGTH_SHORT).show();
        AppLogger.log("ROUTE", "加载路线: " + trainCode + " | " + queryDate);

        new Thread(() -> {
            try {
                MCPClient mcp = new MCPClient(mcpUrl);
                String result = mcp.getTrainRoute(trainCode, queryDate);
                parseRoute(result);

                runOnUiThread(() -> {
                    if (routeStations.isEmpty()) {
                        tvLoading.setVisibility(View.GONE);
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("未获取到路线信息");
                    } else {
                        tvLoading.setVisibility(View.GONE);
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.GONE);
                        listView.setVisibility(View.VISIBLE);
                        listView.setAdapter(new ArrayAdapter<>(
                                RouteDetailActivity.this,
                                android.R.layout.simple_list_item_1,
                                routeStations));
                    }
                });

            } catch (final Exception e) {
                AppLogger.error("ROUTE", "路线加载失败: " + e.getMessage());
                runOnUiThread(() -> {
                    tvLoading.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("路线加载失败: " + e.getMessage());
                    Toast.makeText(RouteDetailActivity.this,
                            "路线加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 解析 MCP 响应中的经停站数据
     */
    private void parseRoute(String data) {
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(data).getAsJsonObject();
            if (json.has("result")) {
                String text = json.getAsJsonObject("result")
                        .getAsJsonArray("content")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();

                String[] lines = text.split("\\n");
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.contains("站序") && !line.contains("===") && !line.contains("---")) {
                        routeStations.add(line);
                    }
                }
                AppLogger.log("ROUTE", "解析到 " + routeStations.size() + " 个经停站");
            }
        } catch (Exception e) {
            AppLogger.error("ROUTE", "路线解析异常: " + e.getMessage());
        }
    }

    /**
     * 打开 AI 分析页面，携带路线详情
     */
    private void openAIAnalysis() {
        if (routeStations.isEmpty()) {
            Toast.makeText(this, "没有路线数据可供分析", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(RouteDetailActivity.this, AIAnalysisActivity.class);
        intent.putExtra("train_code", trainCode);

        StringBuilder sb = new StringBuilder();
        for (String s : routeStations) sb.append(s).append("\n");
        intent.putExtra("route_detail", sb.toString());

        intent.putExtra("from_station", fromStation);
        intent.putExtra("to_station", toStation);
        intent.putExtra("start_time", startTime);
        intent.putExtra("arrive_time", arriveTime);
        intent.putExtra("duration", duration);
        startActivity(intent);
    }
}