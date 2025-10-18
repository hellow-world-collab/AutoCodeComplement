package com.system.demo.LLM;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

/**
 * 大模型LLM客户端
 * 优化版：简化缓存逻辑，使用CompletionCacheManager统一管理
 */
public class LLMClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // 单例 OkHttpClient
    private static final OkHttpClient client = createHttpClient();

    // 当前正在执行的请求
    private static volatile Call currentCall = null;

    // 创建HTTP客户端（保持不变）
    private static OkHttpClient createHttpClient() {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7897));

        return new OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();
    }

    /**
     * 取消当前请求
     */
    public static void cancelCurrentRequest() {
        Call call = currentCall;
        if (call != null && !call.isCanceled()) {
            call.cancel();
        }
        currentCall = null;
    }

    /**
     * 查询LLM（简化版，缓存由CompletionCacheManager统一管理）
     */
    public static String queryLLM(String prompt, String context) {
        // 取消之前的请求
        cancelCurrentRequest();

        LLMSettings settings = LLMSettings.getInstance();

        String apiKey = settings.apiKey;
        String apiUrl = settings.apiUrl;
        String model = settings.model;

        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }

        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://api.openai.com/v1/chat/completions";
        }

        JSONObject json = new JSONObject();
        json.put("model", model != null && !model.isEmpty() ? model : "gpt-4o-mini");
        json.put("max_tokens", 2000);
        json.put("temperature", 0.3);

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", "你是一个专业的代码助手，请提供简洁的代码补全。"));
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        json.put("messages", messages);

        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        Call call = client.newCall(request);
        currentCall = call;

        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            if (response.body() == null) {
                return null;
            }
            String responseBody = response.body().string();
            JSONObject result = new JSONObject(responseBody);
            String completion = result
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();

            return completion;
        } catch (IOException e) {
            if (call.isCanceled()) {
                return null;
            }
            e.printStackTrace();
            return null;
        } finally {
            currentCall = null;
        }
    }

    /**
     * 获取缓存统计信息（从CompletionCacheManager获取）
     */
    public static String getCacheStats() {
        return CompletionCacheManager.getInstance().getStats().toString();
    }

    /**
     * 清空缓存（委托给CompletionCacheManager）
     */
    public static void clearCache() {
        CompletionCacheManager.getInstance().clearAll();
    }
}