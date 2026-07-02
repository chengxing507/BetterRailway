package com.train12306;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class MCPClient {
    private String mcpBaseUrl;
    private String sessionId = null;
    private boolean initialized = false;
    private int requestId = 1;
    private static final int TIMEOUT = 30000;

    public MCPClient() {
        this("http://localhost:3000/mcp");
    }

    public MCPClient(String baseUrl) {
        this.mcpBaseUrl = baseUrl;
    }

    /** 确保会话已初始化 */
    private void ensureInitialized() throws Exception {
        if (initialized) return;
        AppLogger.log("MCP", "开始MCP会话初始化...");

        String body = "{\"jsonrpc\":\"2.0\",\"id\":" + (requestId++)
                + ",\"method\":\"initialize\""
                + ",\"params\":{\"protocolVersion\":\"2025-03-26\""
                + ",\"capabilities\":{\"tools\":{}}"
                + ",\"clientInfo\":{\"name\":\"Train12306\",\"version\":\"1.0\"}}}";

        String resp = rawPost(null, body);
        initialized = true;
        AppLogger.log("MCP", "初始化完成, sessionId=" + (sessionId != null ? sessionId : "无"));
    }

    /** 带 session 的 JSON-RPC 调用 */
    private String jsonRpcCall(String method, String paramsJson) throws Exception {
        ensureInitialized();

        String body = "{\"jsonrpc\":\"2.0\",\"id\":" + (requestId++)
                + ",\"method\":\"tools/call\""
                + ",\"params\":{\"name\":\"" + escapeJson(method)
                + "\",\"arguments\":" + paramsJson + "}}";

        return rawPost(sessionId, body);
    }

    /** 核心HTTP POST：统一处理 Accept header + session + 重定向 + 响应 */
    private String rawPost(String sid, String body) throws Exception {
        String targetUrl = mcpBaseUrl;
        int maxRedirects = 5;

        while (maxRedirects-- > 0) {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // ★ 所有请求头必须在 getOutputStream/getResponseCode 之前设置 ★
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json, text/event-stream");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setDoOutput(true);

            if (sid != null) {
                conn.setRequestProperty("mcp-session-id", sid);
            }

            String tag = (sid == null ? "INIT" : "CALL");
            AppLogger.log("MCP", "[" + tag + "] POST " + targetUrl + (sid != null ? " SID=" + sid.substring(0, Math.min(8, sid.length())) + "..." : ""));

            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();

            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                if (location != null && !location.isEmpty()) {
                    targetUrl = location;
                    continue;
                }
            }

            // 初始化时从 header 拿 sessionId
            if (sid == null) {
                String headerSid = conn.getHeaderField("mcp-session-id");
                if (headerSid != null && !headerSid.isEmpty()) {
                    sessionId = headerSid;
                    AppLogger.log("MCP", "从header拿到 sessionId: " + sessionId);
                }
            }

            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resp = readStream(is);
            AppLogger.log("MCP", "[" + tag + "] 响应码 " + code + " | " + truncate(resp));

            if (code >= 200 && code < 300) {
                return resp;
            }

            throw new Exception("HTTP " + code + ": " + truncate(resp));
        }
        throw new Exception("重定向次数过多");
    }

    private String readStream(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    // ---- 公开接口 ----

    public String getCurrentDate() throws Exception {
        return jsonRpcCall("get-current-date", "{}");
    }

    public String getStationCode(String stationName) throws Exception {
        String params = "{\"stationNames\":\"" + escapeJson(stationName) + "\"}";
        return jsonRpcCall("get-station-code-by-names", params);
    }

    public String getTickets(String date, String from, String to, String filterFlags) throws Exception {
        StringBuilder params = new StringBuilder();
        params.append("{\"date\":\"").append(escapeJson(date)).append("\"");
        params.append(",\"fromStation\":\"").append(escapeJson(from)).append("\"");
        params.append(",\"toStation\":\"").append(escapeJson(to)).append("\"");
        if (filterFlags != null && !filterFlags.isEmpty()) {
            params.append(",\"trainFilterFlags\":\"").append(escapeJson(filterFlags)).append("\"");
        }
        params.append(",\"format\":\"json\"");
        params.append("}");
        return jsonRpcCall("get-tickets", params.toString());
    }

    public String getTrainRoute(String trainCode, String date) throws Exception {
        String params = "{\"trainCode\":\"" + escapeJson(trainCode) + "\",\"departDate\":\"" + escapeJson(date) + "\",\"format\":\"json\"}";
        return jsonRpcCall("get-train-route-stations", params);
    }

    /** 测试连接：初始化 + 列出所有工具定义 */
    public String testConnection() throws Exception {
        ensureInitialized();
        return listTools();
    }

    /** 列出所有工具及参数 */
    public String listTools() throws Exception {
        ensureInitialized();
        String body = "{\"jsonrpc\":\"2.0\",\"id\":" + (requestId++)
                + ",\"method\":\"tools/list\",\"params\":{}}";
        return rawPost(sessionId, body);
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}