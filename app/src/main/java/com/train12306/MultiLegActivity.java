package com.train12306;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 换乘规划页面
 * <p>
 * 三种模式：
 * - AUTO: 自动通过枢纽站查找换乘
 * - WAYPOINT: 途经站必须依次经过
 * - FLEXIBLE: 途经站弹性选择
 */
public class MultiLegActivity extends Activity {

    private EditText etFrom, etTo;
    private Spinner spinnerMode, spinnerMaxTrans, spinnerMaxInterval;
    private LinearLayout layoutWaypoints;
    private Button btnAddWaypoint, btnQuery, btnCancel, btnBack;
    private Button btnAiFilter, btnAiConfig;
    private ProgressBar progressBar;
    private TextView tvStatus, tvEmpty;
    private ListView listPaths;

    private final List<EditText> waypointInputs = new ArrayList<>();
    private final List<MultiLegPlanner.Path> allPaths = new ArrayList<>();
    private PathAdapter pathAdapter;

    private MultiLegPlanner planner;

    private static final String[] MODE_NAMES = {"自动换乘", "途经站序列", "弹性途经站"};
    private static final int[] MODE_VALUES = {0, 1, 2}; // AUTO, WAYPOINT, FLEXIBLE

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_leg);

        initViews();
        setupSpinners();
        setupWaypoints();
        setupButtons();

        // 默认添加两行途经站
        addWaypointInput();
        addWaypointInput();
    }

    private void initViews() {
        etFrom = findViewById(R.id.et_from);
        etTo = findViewById(R.id.et_to);
        spinnerMode = findViewById(R.id.spinner_mode);
        spinnerMaxTrans = findViewById(R.id.spinner_max_trans);
        spinnerMaxInterval = findViewById(R.id.spinner_max_interval);
        layoutWaypoints = findViewById(R.id.layout_waypoints);
        btnAddWaypoint = findViewById(R.id.btn_add_waypoint);
        btnQuery = findViewById(R.id.btn_query);
        btnCancel = findViewById(R.id.btn_cancel);
        btnBack = findViewById(R.id.btn_back);
        btnAiFilter = findViewById(R.id.btn_ai_filter);
        btnAiConfig = findViewById(R.id.btn_ai_config);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
        tvEmpty = findViewById(R.id.tv_empty);
        listPaths = findViewById(R.id.list_paths);

        pathAdapter = new PathAdapter();
        listPaths.setAdapter(pathAdapter);
    }

    private void setupSpinners() {
        // 模式选择
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, MODE_NAMES);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMode.setAdapter(modeAdapter);

        // 最大换乘次数 (1-6)
        Integer[] transValues = {1, 2, 3, 4, 5, 6};
        ArrayAdapter<Integer> transAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, transValues);
        transAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMaxTrans.setAdapter(transAdapter);
        spinnerMaxTrans.setSelection(2); // 默认 3

        // 最大间隔小时 (1-48)
        Integer[] intervalValues = new Integer[48];
        for (int i = 0; i < 48; i++) intervalValues[i] = i + 1;
        ArrayAdapter<Integer> intervalAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, intervalValues);
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMaxInterval.setAdapter(intervalAdapter);
        spinnerMaxInterval.setSelection(23); // 默认 24
    }

    private void setupWaypoints() {
        layoutWaypoints.removeAllViews();
        waypointInputs.clear();
    }

    private void addWaypointInput() {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 4, 0, 4);

        final int index = waypointInputs.size();
        TextView label = new TextView(this);
        label.setText("途经" + (index + 1) + ": ");
        label.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        label.setPadding(0, 0, 8, 0);
        label.setTextSize(14);

        EditText et = new EditText(this);
        et.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        et.setHint("站名");
        et.setPadding(8, 8, 8, 8);
        et.setBackgroundResource(android.R.drawable.editbox_background);
        et.setTextSize(14);

        Button btnDel = new Button(this);
        btnDel.setText("×");
        btnDel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnDel.setOnClickListener(v -> {
            waypointInputs.remove(et);
            layoutWaypoints.removeView(row);
            relabelWaypoints();
        });

        row.addView(label);
        row.addView(et);
        row.addView(btnDel);
        layoutWaypoints.addView(row);
        waypointInputs.add(et);
    }

    private void relabelWaypoints() {
        for (int i = 0; i < layoutWaypoints.getChildCount(); i++) {
            View row = layoutWaypoints.getChildAt(i);
            if (row instanceof LinearLayout && row.getChildCount() > 0) {
                View first = ((LinearLayout) row).getChildAt(0);
                if (first instanceof TextView) {
                    ((TextView) first).setText("途经" + (i + 1) + ": ");
                }
            }
        }
    }

    private void setupButtons() {
        btnAddWaypoint.setOnClickListener(v -> addWaypointInput());

        btnQuery.setOnClickListener(v -> {
            ButtonGuard.guard(btnQuery, () -> startQuery());
        });

        btnCancel.setOnClickListener(v -> {
            if (planner != null) planner.cancel();
            setStatus("已取消");
            btnCancel.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            btnQuery.setEnabled(true);
        });

        btnBack.setOnClickListener(v -> finish());

        btnAiFilter.setOnClickListener(v -> openAIFilter());
        btnAiConfig.setOnClickListener(v -> showPromptConfigDialog());
    }

    private void startQuery() {
        String from = etFrom.getText().toString().trim();
        String to = etTo.getText().toString().trim();
        if (from.isEmpty() || to.isEmpty()) {
            Toast.makeText(this, "请输入起点和终点", Toast.LENGTH_SHORT).show();
            return;
        }

        int modeIdx = spinnerMode.getSelectedItemPosition();
        int maxTrans = (int) spinnerMaxTrans.getSelectedItem();
        int maxInterval = (int) spinnerMaxInterval.getSelectedItem();

        // 收集途经站
        List<String> waypoints = new ArrayList<>();
        for (EditText et : waypointInputs) {
            String w = et.getText().toString().trim();
            if (!w.isEmpty()) waypoints.add(w);
        }

        // 开始查询
        setQuerying(true);
        allPaths.clear();
        pathAdapter.notifyDataSetChanged();
        tvEmpty.setVisibility(View.GONE);
        listPaths.setVisibility(View.GONE);

        planner = new MultiLegPlanner(getQueryDate(), maxTrans, maxInterval);
        planner.setCallback(new MultiLegPlanner.ProgressCallback() {
            @Override
            public void onProgress(String msg) {
                runOnUiThread(() -> setStatus(msg));
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    setStatus("❌ " + msg);
                    Toast.makeText(MultiLegActivity.this, msg, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        });

        new Thread(() -> {
            List<MultiLegPlanner.Path> results;
            try {
                switch (modeIdx) {
                    case 0: // AUTO
                        planner.setMode(MultiLegPlanner.Mode.AUTO);
                        results = planner.planAutoTransfer(from, to, waypoints, false);
                        break;
                    case 1: // WAYPOINT
                        planner.setMode(MultiLegPlanner.Mode.WAYPOINT);
                        List<String> stations = new ArrayList<>();
                        stations.add(from);
                        stations.addAll(waypoints);
                        stations.add(to);
                        results = planner.planWithWaypoints(stations);
                        break;
                    case 2: // FLEXIBLE
                        planner.setMode(MultiLegPlanner.Mode.FLEXIBLE);
                        results = planner.planFlexible(from, to, waypoints);
                        break;
                    default:
                        results = new ArrayList<>();
                }
            } catch (Exception e) {
                AppLogger.error("MULTI", "查询异常: " + e.getMessage());
                results = new ArrayList<>();
            }

            List<MultiLegPlanner.Path> finalResults = results;
            runOnUiThread(() -> {
                setQuerying(false);
                allPaths.clear();
                allPaths.addAll(finalResults);
                pathAdapter.notifyDataSetChanged();
                showResults();
            });
        }).start();
    }

    private void setQuerying(boolean querying) {
        progressBar.setVisibility(querying ? View.VISIBLE : View.GONE);
        btnQuery.setEnabled(!querying);
        btnCancel.setVisibility(querying ? View.VISIBLE : View.GONE);
        if (querying) setStatus("正在查询...");
    }

    private void setStatus(String msg) {
        tvStatus.setText(msg);
        AppLogger.log("MULTI", msg);
    }

    private void showResults() {
        if (allPaths.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("未找到符合条件的路线\n请尝试增加最大换乘次数或扩大间隔时间");
            listPaths.setVisibility(View.GONE);
            findViewById(R.id.layout_results_controls).setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            listPaths.setVisibility(View.VISIBLE);
            findViewById(R.id.layout_results_controls).setVisibility(View.VISIBLE);
            setStatus(String.format("✅ 找到 %d 条路线", allPaths.size()));
        }
    }

    /** 获取查询日期（取当天） */
    private String getQueryDate() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(new java.util.Date());
    }

    // ======================== AI 筛选 ========================

    private void openAIFilter() {
        if (allPaths.isEmpty()) {
            Toast.makeText(this, "没有路线可供分析", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取 AI 配置
        SharedPreferences prefs = getSharedPreferences("ai_config", MODE_PRIVATE);
        String baseUrl = prefs.getString("base_url", "");
        String apiKey = prefs.getString("api_key", "");
        String modelName = prefs.getString("model_name", "");
        String prompt = prefs.getString("multi_leg_prompt", MultiLegPlanner.getDefaultAIPrompt());

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "请先在设置中配置 AI API", Toast.LENGTH_SHORT).show();
            return;
        }

        // 构建 prompt
        StringBuilder sb = new StringBuilder();
        sb.append(MultiLegPlanner.getAIPromptPrefix()).append("\n");
        sb.append("筛选规则: ").append(prompt).append("\n\n");
        sb.append("以下是全部 ").append(allPaths.size()).append(" 条换乘方案:\n\n");

        for (int i = 0; i < allPaths.size(); i++) {
            sb.append("方案").append(i + 1).append(": ")
              .append(allPaths.get(i).getDetailed()).append("\n");
        }

        sb.append("\n请按规则筛选后，输出推荐的方案编号和理由。");

        final String promptText = sb.toString();

        // 发送到 AI
        setStatus("🤖 AI 分析中...");
        final AIAnalysisClient aiClient = new AIAnalysisClient(baseUrl, apiKey, modelName);
        new Thread(() -> {
            try {
                String result = aiClient.analyzeRoute("", promptText);
                runOnUiThread(() -> showAIResult(result));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setStatus("❌ AI 分析失败: " + e.getMessage());
                    Toast.makeText(MultiLegActivity.this,
                            "AI 分析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showAIResult(String result) {
        new AlertDialog.Builder(this)
                .setTitle("🤖 AI 筛选结果")
                .setMessage(result)
                .setPositiveButton("确定", null)
                .setNeutralButton("复制", (d, w) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("ai_result", result));
                    Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /** 显示 Prompt 配置对话框 */
    private void showPromptConfigDialog() {
        String[] presets = MultiLegPlanner.getBuiltinPrompts();
        final String[] selected = {""};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("AI Prompt 设置");

        // 预设列表
        builder.setItems(presets, (dialog, which) -> {
            selected[0] = presets[which];
            showEditPromptDialog(selected[0]);
        });

        builder.setNeutralButton("查看当前", (d, w) -> {
            SharedPreferences prefs = getSharedPreferences("ai_config", MODE_PRIVATE);
            String current = prefs.getString("multi_leg_prompt", MultiLegPlanner.getDefaultAIPrompt());
            showEditPromptDialog(current);
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showEditPromptDialog(String initialText) {
        EditText et = new EditText(this);
        et.setText(initialText);
        et.setPadding(16, 16, 16, 16);
        et.setMinHeight(200);

        new AlertDialog.Builder(this)
                .setTitle("编辑 AI Prompt")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    String newPrompt = et.getText().toString().trim();
                    getSharedPreferences("ai_config", MODE_PRIVATE)
                            .edit()
                            .putString("multi_leg_prompt", newPrompt)
                            .apply();
                    Toast.makeText(this, "已保存 AI Prompt", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ======================== 列表适配器 ========================

    private class PathAdapter extends BaseAdapter {
        @Override
        public int getCount() { return allPaths.size(); }

        @Override
        public Object getItem(int i) { return allPaths.get(i); }

        @Override
        public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(
                        R.layout.item_multi_leg_path, parent, false);
            }

            MultiLegPlanner.Path path = allPaths.get(i);

            TextView tvSegments = convertView.findViewById(R.id.tv_segments);
            TextView tvInfo = convertView.findViewById(R.id.tv_info);
            TextView tvDetail = convertView.findViewById(R.id.tv_detail);

            tvSegments.setText(path.getSummary());
            tvInfo.setText(String.format("🔄 %d次换乘 | ⏱ %s | ⏳ 等待%d分 | 🚉 %s→%s",
                    path.transfers, path.totalDuration, path.totalWaitMinutes,
                    path.firstDeparture, path.lastArrival));
            tvDetail.setText(path.getDetailed());

            // 高亮
            if (i == 0) {
                convertView.setBackgroundColor(0xFFE3F2FD);
            } else {
                convertView.setBackgroundColor(0xFFF5F5F5);
            }

            return convertView;
        }
    }
}