package com.train12306;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class AIAnalysisActivity extends Activity {
    private EditText etPrompt;
    private TextView tvResult;
    private Button btnAnalyze;
    private String ticketSummary = "";
    private String routeDetail = "";
    private String trainCode = "", fromStation = "", toStation = "";
    private String startTime = "", arriveTime = "", duration = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_analysis);

        etPrompt = (EditText) findViewById(R.id.et_prompt);
        tvResult = (TextView) findViewById(R.id.tv_result);
        btnAnalyze = (Button) findViewById(R.id.btn_analyze);
        Button btnBack = (Button) findViewById(R.id.btn_back);

        ticketSummary = getIntent().getStringExtra("ticket_summary");
        routeDetail = getIntent().getStringExtra("route_detail");
        trainCode = getIntent().getStringExtra("train_code");
        fromStation = getIntent().getStringExtra("from_station");
        toStation = getIntent().getStringExtra("to_station");
        startTime = getIntent().getStringExtra("start_time");
        arriveTime = getIntent().getStringExtra("arrive_time");
        duration = getIntent().getStringExtra("duration");

        etPrompt.setHint("例如：分析这个车次的性价比");

        btnAnalyze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doAnalysis();
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void doAnalysis() {
        SharedPreferences prefs = getSharedPreferences("ai_config", MODE_PRIVATE);
        final String baseUrl = prefs.getString("base_url", "");
        final String apiKey = prefs.getString("api_key", "");
        final String modelName = prefs.getString("model_name", "gpt-3.5-turbo");

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "请先在设置中配置AI API", Toast.LENGTH_LONG).show();
            return;
        }

        String userInput = etPrompt.getText().toString().trim();
        if (userInput.isEmpty()) {
            userInput = "请根据以下信息，给出出行建议：";
        }

        final String userPrompt = userInput;

        StringBuilder context = new StringBuilder();
        if (trainCode != null && !trainCode.isEmpty()) {
            context.append("车次：").append(trainCode).append("\n");
            context.append(fromStation).append(" -> ").append(toStation).append("\n");
            context.append("时间：").append(startTime).append(" - ").append(arriveTime).append("\n");
            context.append("历时：").append(duration).append("\n\n");
        }
        if (routeDetail != null && !routeDetail.isEmpty()) {
            context.append("路线详情：\n").append(routeDetail).append("\n\n");
        }
        if (ticketSummary != null && !ticketSummary.isEmpty()) {
            context.append("所有车次：\n").append(ticketSummary).append("\n");
        }
        context.append("用户问题：").append(userPrompt);

        final String message = context.toString();

        tvResult.setText("AI分析中，请稍候...");
        btnAnalyze.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AIAnalysisClient ai = new AIAnalysisClient(baseUrl, apiKey, modelName);
                    final String result = ai.analyzeRoute(
                            "你是一个专业的火车出行助手。请根据车次信息，给出详细的出行建议和分析。",
                            message);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvResult.setText(result);
                            btnAnalyze.setEnabled(true);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvResult.setText("分析失败: " + e.getMessage());
                            btnAnalyze.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }
}