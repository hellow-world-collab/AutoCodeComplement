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
 * 调用大模型的 HTTP 客户端
 */
public class LLMClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String API_KEY = "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";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static String queryLLM(String prompt) {
        if (API_KEY == null || API_KEY.isEmpty()) {
            return "错误：未设置 OPENAI_API_KEY 环境变量。";
        }

        OkHttpClient client = new OkHttpClient();

        JSONObject json = new JSONObject();
        json.put("model", "gpt-4o-mini");

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", "你是一个专业的代码助手。"));
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        json.put("messages", messages);

        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "调用API失败：" + response.code();
            }
            if (response.body() == null) {
                return "响应体为空。";
            }
            String responseBody = response.body().string();
            JSONObject result = new JSONObject(responseBody);
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
