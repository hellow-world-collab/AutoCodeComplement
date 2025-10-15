package com.system.demo.LLM;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * LLM API 客户端
 * 支持配置 API URL、API Key 和模型参数
 */
public class LLMClient {
    
    // ===== 配置区域 - 请根据您的 LLM 服务修改以下参数 =====
    
    /**
     * LLM API 端点 URL
     * 示例：
     * - OpenAI: "https://api.openai.com/v1/chat/completions"
     * - Azure OpenAI: "https://YOUR_RESOURCE.openai.azure.com/openai/deployments/YOUR_DEPLOYMENT/chat/completions?api-version=2023-05-15"
     * - 其他兼容的服务端点
     */
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    
    /**
     * API 密钥
     * 请替换为您的实际 API 密钥
     */
    private static final String API_KEY = "YOUR_API_KEY";
    
    /**
     * 使用的模型名称
     * 示例：
     * - "gpt-4o-mini"
     * - "gpt-4"
     * - "gpt-3.5-turbo"
     * - 或您自己部署的模型名称
     */
    private static final String MODEL = "gpt-4o-mini";
    
    /**
     * 温度参数 (0.0 - 2.0)
     * 较低的值使输出更确定，较高的值使输出更随机
     */
    private static final double TEMPERATURE = 0.3;
    
    /**
     * 最大生成 token 数
     */
    private static final int MAX_TOKENS = 2000;
    
    /**
     * 请求超时时间（秒）
     */
    private static final int TIMEOUT_SECONDS = 30;
    
    // ===== 配置区域结束 =====
    
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();

    /**
     * 调用 LLM API
     * @param prompt 用户提示词
     * @return LLM 的响应文本，如果失败返回 null
     */
    public static String queryLLM(String prompt) {
        try {
            // 构建请求 JSON
            JSONObject requestJson = buildRequestJson(prompt);
            
            // 创建请求
            RequestBody body = RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            // 执行请求
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    System.err.println("LLM API 调用失败 [" + response.code() + "]: " + errorBody);
                    return null;
                }
                
                // 解析响应
                String responseBody = response.body().string();
                return parseResponse(responseBody);
                
            }
        } catch (IOException e) {
            System.err.println("LLM API 调用出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("LLM API 处理异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 构建请求 JSON
     */
    private static JSONObject buildRequestJson(String prompt) {
        JSONObject json = new JSONObject();
        json.put("model", MODEL);
        json.put("temperature", TEMPERATURE);
        json.put("max_tokens", MAX_TOKENS);
        
        // 构建消息数组
        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.put(userMessage);
        
        json.put("messages", messages);
        
        return json;
    }

    /**
     * 解析 LLM 响应
     */
    private static String parseResponse(String responseBody) {
        try {
            JSONObject result = new JSONObject(responseBody);
            
            // 检查是否有错误
            if (result.has("error")) {
                String errorMsg = result.getJSONObject("error").optString("message", "未知错误");
                System.err.println("LLM 返回错误: " + errorMsg);
                return null;
            }
            
            // 提取生成的内容
            if (result.has("choices") && result.getJSONArray("choices").length() > 0) {
                JSONObject choice = result.getJSONArray("choices").getJSONObject(0);
                if (choice.has("message")) {
                    return choice.getJSONObject("message").getString("content").trim();
                }
            }
            
            System.err.println("LLM 响应格式异常: " + responseBody);
            return null;
            
        } catch (Exception e) {
            System.err.println("解析 LLM 响应失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 测试 API 连接是否正常
     */
    public static boolean testConnection() {
        String testPrompt = "Hello, please respond with 'OK' if you receive this message.";
        String response = queryLLM(testPrompt);
        return response != null && !response.isEmpty();
    }
}

