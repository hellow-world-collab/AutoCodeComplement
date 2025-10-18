package com.system.demo.LLM;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * 大模型LLM部分，优化缓存机制
 */
public class LLMClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // 单例 OkHttpClient
    private static final OkHttpClient client = createHttpClient();

    // 当前正在执行的请求
    private static volatile Call currentCall = null;

    // 改进的缓存：基于上下文哈希
    private static final Map<String, CacheEntry> cache = new LinkedHashMap<>();
    private static final int MAX_CACHE_SIZE = 100; // 可以设置更大，因为存储开销小了

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

    private static final long CACHE_TTL_MS = 60000; // 缓存1分钟

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
     * 生成上下文哈希键
     */
    private static String generateContextKey(String context) {
        // 归一化处理
        String normalized = context
                .replaceAll("\\s+", " ")           // 合并空白
                .replaceAll("//[^\\n]*", "")       // 移除单行注释
                .trim();

        // 取最近有意义的代码段（最后150字符通常足够）
        int start = Math.max(0, normalized.length() - 150);
        String recent = normalized.substring(start);

        // 提取代码结构特征，提高缓存命中率
        String features = extractCodeFeatures(recent);

        return features + "_" + Integer.toHexString(recent.hashCode());
    }

    /**
     * 提取代码特征
     */
    private static String extractCodeFeatures(String code) {
        if (code.contains(".") && !code.endsWith(".")) return "method";
        if (code.contains("new ")) return "new";
        if (code.contains("if (")) return "if";
        if (code.contains("for (")) return "for";
        if (code.contains("while (")) return "while";
        if (code.contains("=")) return "assign";
        if (code.trim().endsWith(".")) return "dot";
        return "code";
    }

    /**
     * 从缓存获取建议
     */
    private static String getCachedSuggestion(String context) {
        String key = generateContextKey(context);
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            System.out.println("缓存命中: " + key);
            return entry.result;
        }
        // 移除过期条目
        if (entry != null && entry.isExpired()) {
            cache.remove(key);
        }
        return null;
    }

    /**
     * 缓存建议结果
     */
    private static void cacheSuggestion(String context, String suggestion) {
        if (suggestion == null || suggestion.trim().isEmpty()) return;

        String key = generateContextKey(context);

        // LRU 淘汰策略
        if (cache.size() >= MAX_CACHE_SIZE) {
            Iterator<String> it = cache.keySet().iterator();
            if (it.hasNext()) {
                String oldestKey = it.next();
                it.remove();
                System.out.println("缓存淘汰: " + oldestKey);
            }
        }

        cache.put(key, new CacheEntry(suggestion));
        System.out.println("缓存保存: " + key + " -> " + suggestion.substring(0, Math.min(20, suggestion.length())));
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
     * 查询LLM
     */
    public static String queryLLM(String prompt, String context) {
        // 首先尝试从缓存获取
        String cached = getCachedSuggestion(context);
        if (cached != null) {
            return cached;
        }

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

            // 缓存结果（使用上下文而不是prompt）
            cacheSuggestion(context, completion);
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
     * 获取缓存统计信息（用于调试）
     */
    public static String getCacheStats() {
        return "缓存大小: " + cache.size() + "/" + MAX_CACHE_SIZE;
    }

    /**
     * 清空缓存（用于测试）
     */
    public static void clearCache() {
        cache.clear();
    }
}