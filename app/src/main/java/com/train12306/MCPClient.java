package com.train12306;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * MCP 客户端 — 基于 OkHttp + JSON-RPC 2.0 协议
 * <p>
 * 特性：
 * - 线程安全（AtomicInteger ID + volatile/synchronized 初始化保护）
 * - 站点代码内存缓存（ConcurrentHashMap）
 * - OkHttp 连接池复用
 * - 完整的资源关闭（try-finally）
 * - 手动重定向处理
 */
public class MCPClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_REDIRECTS = 5;
    private static final int CACHE_MAX_SIZE = 500;

    private final String mcpBaseUrl;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    /** 站点代码缓存：站名 → 代码 */
    private static final ConcurrentHashMap<String, String> stationCodeCache = new ConcurrentHashMap<>();

    /** JSON-RPC 请求 ID（线程安全） */
    private final AtomicInteger requestId = new AtomicInteger(1);

    /** MCP 会话状态 */
    private volatile String sessionId = null;
    private volatile boolean initialized = false;
    private final Object initLock = new Object();

    // ======================== 构造 & 初始化 ========================

    public MCPClient() {
        this("http://localhost:3000/mcp");
    }

    public MCPClient(String baseUrl) {
        this.mcpBaseUrl = baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(false)   // 手动处理重定向
                .followSslRedirects(false)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * 确保 MCP 会话已初始化（线程安全）
     */
    private void ensureInitialized() throws Exception {
        if (initialized) return;
        synchronized (initLock) {
            if (initialized) return;  // 双重检查锁定
            doInitialize();
            initialized = true;
        }
    }

    private void doInitialize() throws Exception {
        AppLogger.log("MCP", "开始 MCP 会话初始化...");

        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", "2025-03-26");

        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        params.add("capabilities", capabilities);

        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "Train12306");
        clientInfo.addProperty("version", "2.0");
        params.add("clientInfo", clientInfo);

        String body = buildJsonRpc("initialize", params);
        String resp = rawPost(null, body);

        AppLogger.log("MCP", "初始化完成, sessionId=" + (sessionId != null ? sessionId : "无"));
    }

    // ======================== JSON-RPC 调用 ========================

    private String jsonRpcCall(String method, JsonObject arguments) throws Exception {
        ensureInitialized();

        JsonObject params = new JsonObject();
        params.addProperty("name", method);
        params.add("arguments", arguments);

        String body = buildJsonRpc("tools/call", params);
        return rawPost(sessionId, body);
    }

    private String buildJsonRpc(String method, JsonObject params) {
        JsonObject json = new JsonObject();
        json.addProperty("jsonrpc", "2.0");
        json.addProperty("id", requestId.getAndIncrement());
        json.addProperty("method", method);
        json.add("params", params);
        return gson.toJson(json);
    }

    // ======================== HTTP 通信（手动重定向支持） ========================

    private String rawPost(String sid, String body) throws Exception {
        String targetUrl = mcpBaseUrl;
        int redirects = MAX_REDIRECTS;

        while (redirects-- > 0) {
            Request.Builder reqBuilder = new Request.Builder()
                    .url(targetUrl)
                    .post(RequestBody.create(body, JSON))
                    .addHeader("Accept", "application/json, text/event-stream");

            if (sid != null) {
                reqBuilder.addHeader("mcp-session-id", sid);
            }

            Request request = reqBuilder.build();
            String tag = (sid == null ? "INIT" : "CALL");
            AppLogger.log("MCP", "[" + tag + "] POST " + targetUrl
                    + (sid != null ? " SID=" + sid.substring(0, Math.min(8, sid.length())) + "..." : ""));

            try (Response response = httpClient.newCall(request).execute()) {
                int code = response.code();

                // 处理重定向
                if (code == 301 || code == 302 || code == 307 || code == 308) {
                    String location = response.header("Location");
                    if (location != null && !location.isEmpty()) {
                        targetUrl = location;
                        AppLogger.log("MCP", "[" + tag + "] 重定向到: " + location);
                        continue;
                    }
                }

                // 初始化时从 header 获取 sessionId
                if (sid == null) {
                    String headerSid = response.header("mcp-session-id");
                    if (headerSid != null && !headerSid.isEmpty()) {
                        sessionId = headerSid;
                        AppLogger.log("MCP", "从 header 获取 sessionId: " + sessionId);
                    }
                }

                String respBody = response.body() != null ? response.body().string() : "";

                if (code >= 200 && code < 300) {
                    AppLogger.log("MCP", "[" + tag + "] 响应码 " + code + " | " + truncate(respBody));
                    return respBody;
                }

                throw new Exception("HTTP " + code + ": " + truncate(respBody));
            }
        }
        throw new Exception("重定向次数过多 (超过 " + MAX_REDIRECTS + " 次)");
    }

    // ======================== 站点代码缓存系统 ========================

    /**
     * 清空站点代码缓存
     */
    public static void clearStationCache() {
        stationCodeCache.clear();
        AppLogger.log("MCP", "站点代码缓存已清空");
    }

    /**
     * 获取缓存大小
     */
    public static int getStationCacheSize() {
        return stationCodeCache.size();
    }

    // ======================== 公开 API ========================

    /**
     * 获取当前日期 (来自 MCP Server)
     */
    public String getCurrentDate() throws Exception {
        return jsonRpcCall("get-current-date", new JsonObject());
    }

    /**
     * 根据站点名称查询站点代码（带缓存）
     *
     * @param stationName 站点名称，如 "北京南"
     * @return MCP 原始响应 JSON 字符串
     */
    public String getStationCode(String stationName) throws Exception {
        // 查缓存
        String cached = stationCodeCache.get(stationName);
        if (cached != null) {
            AppLogger.log("MCP", "站点缓存命中: " + stationName + "=" + cached);
            // 构造一个模拟的 MCP 响应
            JsonObject result = new JsonObject();
            result.addProperty("jsonrpc", "2.0");

            JsonObject contentItem = new JsonObject();
            contentItem.addProperty("type", "text");
            contentItem.addProperty("text", stationName + " " + cached);

            JsonArray content = new JsonArray();
            content.add(contentItem);

            JsonObject resultObj = new JsonObject();
            resultObj.add("content", content);

            result.add("result", resultObj);
            return gson.toJson(result);
        }

        JsonObject params = new JsonObject();
        params.addProperty("stationNames", stationName);
        String response = jsonRpcCall("get-station-code-by-names", params);

        // 解析并缓存
        String code = StationCodeParser.parseFirstCode(stationName, response);
        if (code != null && !code.isEmpty()) {
            // 控制缓存大小
            if (stationCodeCache.size() >= CACHE_MAX_SIZE) {
                stationCodeCache.clear();
            }
            stationCodeCache.put(stationName, code);
            AppLogger.log("MCP", "站点代码已缓存: " + stationName + "=" + code);
        }

        return response;
    }

    /**
     * 查询车次列表
     *
     * @param date        日期 yyyy-MM-dd
     * @param from        出发站代码
     * @param to          到达站代码
     * @param filterFlags 筛选标志，如 "GD" 表示仅高铁和动车，空字符串表示全部
     */
    public String getTickets(String date, String from, String to, String filterFlags) throws Exception {
        JsonObject params = new JsonObject();
        params.addProperty("date", date);
        params.addProperty("fromStation", from);
        params.addProperty("toStation", to);
        if (filterFlags != null && !filterFlags.isEmpty()) {
            params.addProperty("trainFilterFlags", filterFlags);
        }
        params.addProperty("format", "json");
        return jsonRpcCall("get-tickets", params);
    }

    /**
     * 查询车次经停站列表
     *
     * @param trainCode 车次代码，如 "G1234"
     * @param date      日期 yyyy-MM-dd
     */
    public String getTrainRoute(String trainCode, String date) throws Exception {
        JsonObject params = new JsonObject();
        params.addProperty("trainCode", trainCode);
        params.addProperty("departDate", date);
        params.addProperty("format", "json");
        return jsonRpcCall("get-train-route-stations", params);
    }

    /**
     * 测试 MCP 连接：初始化 + 列出所有工具
     */
    public String testConnection() throws Exception {
        ensureInitialized();
        return listTools();
    }

    /**
     * 列出 MCP Server 所有可用工具
     */
    public String listTools() throws Exception {
        ensureInitialized();
        String body = buildJsonRpc("tools/list", new JsonObject());
        return rawPost(sessionId, body);
    }

    // ======================== 工具方法 ========================

    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}