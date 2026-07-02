package com.train12306;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 站点代码解析器 — 从 MCP Server 响应中提取站点代码
 * <p>
 * 支持多种响应格式，增强容错性：
 * 1. MCP Streamable HTTP 标准格式: result.content[0].text
 * 2. 简单文本行格式: "站名 代码"
 * 3. JSON 表格格式
 */
public class StationCodeParser {

    /**
     * 从 MCP 响应中解析第一个站点的代码
     *
     * @param stationName 要查找的站点名称
     * @param response    MCP 原始响应 JSON
     * @return 站点代码，未找到返回 null
     */
    public static String parseFirstCode(String stationName, String response) {
        if (response == null || response.isEmpty()) {
            AppLogger.log("PARSE", "解析站点[" + stationName + "] 响应为空");
            return null;
        }

        AppLogger.log("PARSE", "解析站点[" + stationName + "] 原始响应: "
                + (response.length() > 300 ? response.substring(0, 300) + "..." : response));

        try {
            // 尝试 JSON 解析
            if (response.trim().startsWith("{")) {
                JsonObject json = JsonParser.parseString(response).getAsJsonObject();

                // 检查是否有错误
                if (json.has("error")) {
                    JsonObject err = json.getAsJsonObject("error");
                    AppLogger.log("PARSE", "MCP 返回错误: " + err.toString());
                    return null;
                }

                // 标准 MCP result.content 格式
                if (json.has("result")) {
                    JsonObject result = json.getAsJsonObject("result");

                    // 检查 isError
                    if (result.has("isError") && result.get("isError").getAsBoolean()) {
                        AppLogger.log("PARSE", "MCP result.isError=true");
                        return null;
                    }

                    if (result.has("content")) {
                        JsonArray content = result.getAsJsonArray("content");
                        if (content.size() > 0) {
                            JsonObject first = content.get(0).getAsJsonObject();
                            if (first.has("text")) {
                                String text = first.get("text").getAsString();
                                AppLogger.log("PARSE", "提取的 text 内容:\n" + text);
                                return parseCodeFromText(text, stationName);
                            }
                        }
                    }
                }

                // 尝试 toolResult 格式
                if (json.has("toolResult")) {
                    String text = json.getAsJsonObject("toolResult").get("text").getAsString();
                    AppLogger.log("PARSE", "toolResult text: " + text);
                    return parseCodeFromText(text, stationName);
                }
            }
        } catch (Exception e) {
            AppLogger.log("PARSE", "JSON 解析异常: " + e.getMessage() + "，尝试文本解析");
        }

        // 如果 JSON 解析失败，尝试直接作为文本解析
        return parseCodeFromText(response, stationName);
    }

    /**
     * 从文本内容中解析站点代码
     * 支持格式：
     * - "北京南 VNP"
     * - "北京南, VNP"
     * - "| 北京南 | VNP |"
     * - "北京南(VNP)"
     */
    private static String parseCodeFromText(String text, String stationName) {
        if (text == null || text.isEmpty()) return null;

        String[] lines = text.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.contains("站名") || line.contains("===") || line.contains("---")) {
                continue;
            }

            // 尝试多种分隔符
            String code = tryParseLine(line, stationName);
            if (code != null) return code;
        }

        AppLogger.log("PARSE", "未找到站点[" + stationName + "] 的代码");
        return null;
    }

    /**
     * 尝试用多种格式解析单行文本
     */
    private static String tryParseLine(String line, String stationName) {
        // 格式1: "北京南 VNP" (空格分隔)
        String[] parts = line.split("\\s+");
        if (parts.length >= 2) {
            if (parts[0].contains(stationName) || stationName.contains(parts[0])) {
                AppLogger.log("PARSE", "格式1 匹配成功: " + parts[1]);
                return parts[1];
            }
        }

        // 格式2: "北京南, VNP" (逗号分隔)
        parts = line.split(",");
        if (parts.length >= 2) {
            String name = parts[0].trim();
            String code = parts[1].trim();
            if (name.contains(stationName) || stationName.contains(name)) {
                AppLogger.log("PARSE", "格式2 匹配成功: " + code);
                return code;
            }
        }

        // 格式3: "| 北京南 | VNP |" (Markdown 表格)
        parts = line.split("\\|");
        if (parts.length >= 3) {
            String name = parts[1].trim();
            String code = parts[2].trim().split("\\s+")[0]; // 取第一个非空字段
            if (name.contains(stationName) || stationName.contains(name)) {
                AppLogger.log("PARSE", "格式3 匹配成功: " + code);
                return code;
            }
        }

        // 格式4: "北京南(VNP)"
        int idx = line.indexOf('(');
        int endIdx = line.indexOf(')');
        if (idx > 0 && endIdx > idx) {
            String name = line.substring(0, idx).trim();
            String code = line.substring(idx + 1, endIdx).trim();
            if (name.contains(stationName) || stationName.contains(name)) {
                AppLogger.log("PARSE", "格式4 匹配成功: " + code);
                return code;
            }
        }

        return null;
    }
}