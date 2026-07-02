package com.train12306;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class AIAnalysisClient {
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private static final int TIMEOUT = 60000;

    public AIAnalysisClient(String baseUrl, String apiKey, String modelName) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    public String testConnection() throws Exception {
        String body = "{\"model\":\"" + escapeJson(modelName) + "\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}],\"max_tokens\":5}";
        String response = callAPI(body);
        return "连接成功！API可用";
    }

    public String analyzeRoute(String systemPrompt, String userMessage) throws Exception {
        String body = "{\"model\":\"" + escapeJson(modelName) + "\",\"messages\":[" +
                "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"}," +
                "{\"role\":\"user\",\"content\":\"" + escapeJson(userMessage) + "\"}]," +
                "\"temperature\":0.7,\"max_tokens\":2000}";
        return callAPI(body);
    }

    private String callAPI(String jsonBody) throws Exception {
        String targetUrl = baseUrl;
        int maxRedirects = 5;

        while (maxRedirects-- > 0) {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                conn.setDoOutput(true);
                conn.setInstanceFollowRedirects(false);

                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();

                // 手动处理重定向，保持 POST 方法
                if (code == HttpURLConnection.HTTP_MOVED_PERM
                        || code == HttpURLConnection.HTTP_MOVED_TEMP
                        || code == HttpURLConnection.HTTP_SEE_OTHER
                        || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        throw new Exception("API返回 " + code + " 但无 Location 头");
                    }
                    targetUrl = location;
                    continue;
                }

                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                if (code >= 200 && code < 300) {
                    return parseAIResponse(response.toString());
                } else {
                    throw new Exception("API返回 " + code + ": " + response.toString());
                }
            } finally {
                conn.disconnect();
            }
        }
        throw new Exception("重定向次数过多");
    }

    private String parseAIResponse(String json) throws Exception {
        // 解析 OpenAI 兼容格式
        org.json.JSONObject obj = new org.json.JSONObject(json);
        if (obj.has("choices")) {
            org.json.JSONArray choices = obj.getJSONArray("choices");
            if (choices.length() > 0) {
                org.json.JSONObject msg = choices.getJSONObject(0);
                if (msg.has("message")) {
                    return msg.getJSONObject("message").getString("content");
                }
                if (msg.has("text")) {
                    return msg.getString("text");
                }
            }
        }
        if (obj.has("response")) return obj.getString("response");
        if (obj.has("output")) return obj.getString("output");
        return json;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}