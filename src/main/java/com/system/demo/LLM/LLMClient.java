package com.system.demo.LLM;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * LLMClient - 调用大语言模型（OpenAI API）的HTTP客户端
 */
public class LLMClient {
    // OpenAI 接口地址
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    // ✅ 建议从系统环境变量读取，而不是硬编码在源码中
    private static final String API_KEY = "OPENAI_API_KEY";

    // JSON 类型常量
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * 调用大模型接口，发送 prompt 并获取返回内容
     *
     * @param prompt 用户输入的提示文本
     * @return 模型回复的文本
     */
    public static String queryLLM(String prompt) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            return "错误：未设置 OPENAI_API_KEY 环境变量。";
        }

        OkHttpClient client = new OkHttpClient();

        // 构造请求 JSON
        JSONObject json = new JSONObject();
        json.put("model", "gpt-4o-mini");

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        json.put("messages", messages);

        // 创建请求体
        RequestBody body = RequestBody.create(json.toString(), JSON);

        // 构建请求
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        // 执行请求
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "调用API失败，HTTP状态码：" + response.code();
            }

            if (response.body() == null) {
                return "调用API失败：响应为空。";
            }

            String responseBody = response.body().string();
            JSONObject result = new JSONObject(responseBody);

            // 解析模型返回内容
            return result
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();

        } catch (IOException e) {
            e.printStackTrace();
            return "调用LLM出错：" + e.getMessage();
        }
    }
}
