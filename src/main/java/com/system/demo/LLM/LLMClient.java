package com.system.demo.LLM;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 大模型LLM部分，到时候把queryLLM更改为本地
 * 优化版本：使用单例 OkHttpClient，降低超时，支持请求取消，添加缓存
 */
public class LLMClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    // 单例 OkHttpClient，复用连接池，大幅提升性能
    private static final OkHttpClient client = createHttpClient();
    
    // 当前正在执行的请求，用于取消
    private static volatile Call currentCall = null;
    
    // 简单的 LRU 缓存，避免重复请求
    private static final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 50;
    private static final long CACHE_TTL_MS = 60000; // 缓存1分钟
    
    private static class CacheEntry {
        final String result;
        final long timestamp;
        
        CacheEntry(String result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
    
    private static OkHttpClient createHttpClient() {
        // 替换为你的代理地址和端口，例如 127.0.0.1:7897
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7897));
        
        return new OkHttpClient.Builder()
                .proxy(proxy) // 如果不需要代理，可删除这一行
                .connectTimeout(5, TimeUnit.SECONDS)  // 降低连接超时：5秒
                .readTimeout(10, TimeUnit.SECONDS)    // 降低读取超时：10秒
                .writeTimeout(10, TimeUnit.SECONDS)   // 降低写入超时：10秒
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES)) // 连接池优化
                .build();
    }
    
    /**
     * 取消当前正在执行的请求
     */
    public static void cancelCurrentRequest() {
        Call call = currentCall;
        if (call != null && !call.isCanceled()) {
            call.cancel();
        }
        currentCall = null;
    }

    public static String queryLLM(String prompt) {
        // 检查缓存
        CacheEntry cached = cache.get(prompt);
        if (cached != null && !cached.isExpired()) {
            return cached.result;
        }
        
        // 取消之前的请求
        cancelCurrentRequest();
        
        LLMSettings settings = LLMSettings.getInstance();

        String apiKey = settings.apiKey;
        String apiUrl = settings.apiUrl;
        String model = settings.model;

        if (apiKey == null || apiKey.isEmpty()) {
            return null; // 返回 null 而不是错误信息
        }

        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://api.openai.com/v1/chat/completions";
        }

        JSONObject json = new JSONObject();
        json.put("model", model != null && !model.isEmpty() ? model : "gpt-4o-mini");
        json.put("max_tokens", 50); // 限制返回长度，加快响应
        json.put("temperature", 0.3); // 降低随机性，提高一致性和速度

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
                return null; // 失败时返回 null
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
            
            // 缓存结果
            cacheResult(prompt, completion);
            return completion;
        } catch (IOException e) {
            if (call.isCanceled()) {
                // 请求被取消，这是正常情况
                return null;
            }
            e.printStackTrace();
            return null;
        } finally {
            currentCall = null;
        }
    }
    
    /**
     * 缓存结果，实现简单的 LRU 淘汰策略
     */
    private static void cacheResult(String prompt, String result) {
        // 如果缓存满了，移除最旧的条目
        if (cache.size() >= MAX_CACHE_SIZE) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                if (entry.getValue().timestamp < oldestTime) {
                    oldestTime = entry.getValue().timestamp;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey != null) {
                cache.remove(oldestKey);
            }
        }
        cache.put(prompt, new CacheEntry(result));
    }
    
//    public static String queryLLM(String prompt) {
//        try {
//            OkHttpClient client = new OkHttpClient();
//
//            JSONObject json = new JSONObject();
//            json.put("model", "llama3"); // 或你的模型名
//            json.put("prompt", prompt);
//            json.put("stream", false);
//
//            RequestBody body = RequestBody.create(json.toString(), JSON);
//            Request request = new Request.Builder()
//                    .url("http://localhost:11434/api/generate") // ✅ 本地模型地址
//                    .post(body)
//                    .build();
//
//            try (Response response = client.newCall(request).execute()) {
//                if (!response.isSuccessful()) {
//                    return "调用本地模型失败：" + response.code();
//                }
//                if (response.body() == null) {
//                    return "本地模型响应为空。";
//                }
//
//                String responseBody = response.body().string();
//                JSONObject result = new JSONObject(responseBody);
//                return result.optString("response", "").trim();
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            return "调用本地模型出错：" + e.getMessage();
//        }
//    }

}
