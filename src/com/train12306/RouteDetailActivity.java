package com.train12306;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

public class RouteDetailActivity extends Activity {
    private ListView listView;
    private TextView tvHeader;
    private List<String> routeStations = new ArrayList<>();
    private String trainCode, queryDate;
    private String fromStation, toStation, startTime, arriveTime, duration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_detail);

        listView = (ListView) findViewById(R.id.list_route);
        tvHeader = (TextView) findViewById(R.id.tv_route_header);
        Button btnAnalyze = (Button) findViewById(R.id.btn_analyze);
        Button btnBack = (Button) findViewById(R.id.btn_back);

        trainCode = getIntent().getStringExtra("train_code");
        queryDate = getIntent().getStringExtra("query_date");
        fromStation = getIntent().getStringExtra("from_station");
        toStation = getIntent().getStringExtra("to_station");
        startTime = getIntent().getStringExtra("start_time");
        arriveTime = getIntent().getStringExtra("arrive_time");
        duration = getIntent().getStringExtra("duration");

        tvHeader.setText(trainCode + " | " + fromStation + "->" + toStation + " | "
                + startTime + "-" + arriveTime + " | " + duration);

        loadRoute();

        btnAnalyze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void loadRoute() {
        final String mcpUrl = getSharedPreferences("ai_config", MODE_PRIVATE)
                .getString("mcp_url", "http://localhost:3000/mcp");

        Toast.makeText(this, "加载路线中...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MCPClient mcp = new MCPClient(mcpUrl);
                    String result = mcp.getTrainRoute(trainCode, queryDate);
                    parseRoute(result);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (routeStations.isEmpty()) {
                                Toast.makeText(RouteDetailActivity.this,
                                        "未获取到路线信息", Toast.LENGTH_SHORT).show();
                            } else {
                                listView.setAdapter(new ArrayAdapter<String>(
                                        RouteDetailActivity.this,
                                        android.R.layout.simple_list_item_1, routeStations));
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(RouteDetailActivity.this,
                                    "路线加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void parseRoute(String data) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(data);
            if (json.has("result")) {
                String text = json.getJSONObject("result")
                        .getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text");
                String[] lines = text.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.contains("站序") && !line.contains("===")) {
                        routeStations.add(line);
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}