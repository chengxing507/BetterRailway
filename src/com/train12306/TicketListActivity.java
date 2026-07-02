package com.train12306;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

public class TicketListActivity extends Activity {
    private ListView listView;
    private TextView tvEmpty;
    private List<TicketItem> ticketList = new ArrayList<>();
    private String queryDate, fromStation, toStation;

    public static class TicketItem {
        String trainCode, fromStation, toStation, startTime, arriveTime, duration;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ticket_list);

        listView = (ListView) findViewById(R.id.list_tickets);
        tvEmpty = (TextView) findViewById(R.id.tv_empty);

        queryDate = getIntent().getStringExtra("query_date");
        fromStation = getIntent().getStringExtra("from_station");
        toStation = getIntent().getStringExtra("to_station");
        String ticketData = getIntent().getStringExtra("ticket_data");

        if (ticketData != null && !ticketData.isEmpty()) {
            parseTickets(ticketData);
        }

        if (ticketList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            listView.setAdapter(new TicketAdapter());
        }

        Button btnCompare = (Button) findViewById(R.id.btn_compare);
        Button btnBack = (Button) findViewById(R.id.btn_back);
        btnCompare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAIAnalysis();
            }
        });
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void parseTickets(String data) {
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
                    if (line.isEmpty() || line.contains("车次") || line.contains("===") || line.contains("---")) continue;
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 5) {
                        TicketItem item = new TicketItem();
                        item.trainCode = parts[0];
                        item.fromStation = parts.length > 1 ? parts[1] : fromStation;
                        item.toStation = parts.length > 2 ? parts[2] : toStation;
                        item.startTime = parts.length > 3 ? parts[3] : "";
                        item.arriveTime = parts.length > 4 ? parts[4] : "";
                        item.duration = parts.length > 5 ? parts[5] : "";
                        ticketList.add(item);
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private class TicketAdapter extends BaseAdapter {
        @Override
        public int getCount() { return ticketList.size(); }

        @Override
        public Object getItem(int i) { return ticketList.get(i); }

        @Override
        public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_ticket, parent, false);
                holder = new ViewHolder();
                holder.tvTrainCode = (TextView) convertView.findViewById(R.id.tv_train_code);
                holder.tvFromStation = (TextView) convertView.findViewById(R.id.tv_from_station);
                holder.tvToStation = (TextView) convertView.findViewById(R.id.tv_to_station);
                holder.tvStartTime = (TextView) convertView.findViewById(R.id.tv_start_time);
                holder.tvArriveTime = (TextView) convertView.findViewById(R.id.tv_arrive_time);
                holder.tvDuration = (TextView) convertView.findViewById(R.id.tv_duration);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            TicketItem item = ticketList.get(i);
            holder.tvTrainCode.setText(item.trainCode);
            holder.tvFromStation.setText(item.fromStation);
            holder.tvToStation.setText(item.toStation);
            holder.tvStartTime.setText(item.startTime);
            holder.tvArriveTime.setText(item.arriveTime);
            holder.tvDuration.setText(item.duration);

            final int pos = i;
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TicketItem t = ticketList.get(pos);
                    Intent intent = new Intent(TicketListActivity.this, RouteDetailActivity.class);
                    intent.putExtra("train_code", t.trainCode);
                    intent.putExtra("query_date", queryDate);
                    intent.putExtra("from_station", t.fromStation);
                    intent.putExtra("to_station", t.toStation);
                    intent.putExtra("start_time", t.startTime);
                    intent.putExtra("arrive_time", t.arriveTime);
                    intent.putExtra("duration", t.duration);
                    startActivity(intent);
                }
            });
            return convertView;
        }
    }

    private static class ViewHolder {
        TextView tvTrainCode, tvFromStation, tvToStation, tvStartTime, tvArriveTime, tvDuration;
    }

    private void openAIAnalysis() {
        StringBuilder sb = new StringBuilder();
        for (TicketItem t : ticketList) {
            sb.append(t.trainCode).append(" ")
              .append(t.startTime).append("-").append(t.arriveTime).append(" ")
              .append(t.duration).append("\n");
        }
        Intent intent = new Intent(this, AIAnalysisActivity.class);
        intent.putExtra("ticket_summary", sb.toString());
        startActivity(intent);
    }
}