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
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.Calendar;

public class MainActivity extends Activity {
    private EditText etFrom, etTo;
    private Button btnDate;
    private String selectedDate = "";
    private String stationFromName = "", stationToName = "";
    private String filterFlags = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etFrom = (EditText) findViewById(R.id.et_from);
        etTo = (EditText) findViewById(R.id.et_to);
        btnDate = (Button) findViewById(R.id.btn_date);
        Button btnSwap = (Button) findViewById(R.id.btn_swap);
        Button btnQuery = (Button) findViewById(R.id.btn_query);
        Button btnSettings = (Button) findViewById(R.id.btn_settings);
        Button btnFilter = (Button) findViewById(R.id.btn_filter);
        Button btnLog = (Button) findViewById(R.id.btn_log);

        Calendar cal = Calendar.getInstance();
        selectedDate = String.format("%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
        btnDate.setText(selectedDate);

        btnDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker();
            }
        });
        btnSwap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swapStations();
            }
        });

        ButtonGuard.guard(btnSettings, new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        ButtonGuard.guard(btnLog, new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, LogActivity.class));
            }
        });

        btnFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFilterDialog();
            }
        });
        ButtonGuard.guard(btnQuery, new Runnable() {
            @Override
            public void run() {
                stationFromName = etFrom.getText().toString().trim();
                stationToName = etTo.getText().toString().trim();
                if (stationFromName.isEmpty() || stationToName.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请输入出发站和到达站", Toast.LENGTH_SHORT).show();
                    return;
                }
                doQuery();
            }
        });
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(android.widget.DatePicker view, int year, int month, int dayOfMonth) {
                        selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth);
                        btnDate.setText(selectedDate);
                    }
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void swapStations() {
        String tmp = etFrom.getText().toString();
        etFrom.setText(etTo.getText().toString());
        etTo.setText(tmp);
        String tmpName = stationFromName;
        stationFromName = stationToName;
        stationToName = tmpName;
    }

    private void showFilterDialog() {
        final String[] items = {"高铁G", "动车D", "直达Z", "特快T", "快速K"};
        final boolean[] checked = {false, false, false, false, false};
        final String[] codes = {"G", "D", "Z", "T", "K"};

        new AlertDialog.Builder(this)
                .setTitle("车次筛选")
                .setMultiChoiceItems(items, checked, null)
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) sb.append(codes[i]);
                        }
                        filterFlags = sb.toString();
                        Toast.makeText(MainActivity.this,
                                filterFlags.isEmpty() ? "显示全部车次" : "已筛选: " + filterFlags,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doQuery() {
        final String from = stationFromName;
        final String to = stationToName;
        final String date = selectedDate;

        AppLogger.log("QUERY", "开始查询: " + from + " -> " + to + " | " + date);

        // 从设置读取 MCP 地址
        SharedPreferences prefs = getSharedPreferences("ai_config", MODE_PRIVATE);
        final String mcpUrl = prefs.getString("mcp_url", "http://localhost:3000/mcp");
        AppLogger.log("QUERY", "MCP地址: " + mcpUrl);

        Toast.makeText(this, "查询中...", Toast.LENGTH_LONG).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MCPClient mcp = new MCPClient(mcpUrl);
                    AppLogger.log("QUERY", "查站点码: " + from);
                    String fromResult = mcp.getStationCode(from);
                    AppLogger.log("QUERY", "查站点码: " + to);
                    String toResult = mcp.getStationCode(to);
                    final String fromCode = parseStationCode(fromResult, from);
                    final String toCode = parseStationCode(toResult, to);
                    AppLogger.log("QUERY", "站点码结果: " + from + "=" + fromCode + ", " + to + "=" + toCode);
                    if (fromCode.isEmpty() || toCode.isEmpty()) {
                        final String debugFrom = fromResult.length() > 200 ? fromResult.substring(0, 200) + "..." : fromResult;
                        final String debugTo = toResult.length() > 200 ? toResult.substring(0, 200) + "..." : toResult;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this,
                                        "未找到站点信息\n发：" + debugFrom + "\n到：" + debugTo,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                        return;
                    }
                    final String ticketResult = mcp.getTickets(date, fromCode, toCode, filterFlags);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(MainActivity.this, TicketListActivity.class);
                            intent.putExtra("ticket_data", ticketResult);
                            intent.putExtra("query_date", date);
                            intent.putExtra("from_station", from);
                            intent.putExtra("to_station", to);
                            startActivity(intent);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,
                                    "查询失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private String parseStationCode(String response, String stationName) {
        AppLogger.log("PARSE", "解析站点[" + stationName + "] 原始响应: " +
                (response.length() > 300 ? response.substring(0, 300) + "..." : response));
        try {
            if (response.startsWith("{")) {
                JSONObject json = new JSONObject(response);

                // 检查是否有 error
                if (json.has("error")) {
                    JSONObject err = json.getJSONObject("error");
                    AppLogger.log("PARSE", "MCP返回错误: " + err.toString());
                    return "";
                }

                if (json.has("result")) {
                    JSONObject result = json.getJSONObject("result");
                    // MCP streamable HTTP 的 content 可能是数组
                    if (result.has("content")) {
                        String text = result.getJSONArray("content")
                                .getJSONObject(0)
                                .getString("text");
                        AppLogger.log("PARSE", "提取的text内容:\n" + text);
                        String[] lines = text.split("\n");
                        for (String line : lines) {
                            line = line.trim();
                            if (!line.isEmpty() && !line.contains("站名")) {
                                String[] parts = line.split("\\s+");
                                if (parts.length >= 2) {
                                    AppLogger.log("PARSE", "解析到代码: " + parts[1]);
                                    return parts[1];
                                }
                            }
                        }
                    } else if (result.has("isError") && result.getBoolean("isError")) {
                        AppLogger.log("PARSE", "MCP result isError=true");
                    }
                }

                // 尝试直接解析 toolResult
                if (json.has("toolResult")) {
                    String text = json.getJSONObject("toolResult").getString("text");
                    AppLogger.log("PARSE", "toolResult text: " + text);
                }
            }
        } catch (Exception e) {
            AppLogger.log("PARSE", "解析异常: " + e.getMessage());
        }
        AppLogger.log("PARSE", "未找到站点代码");
        return "";
    }
}