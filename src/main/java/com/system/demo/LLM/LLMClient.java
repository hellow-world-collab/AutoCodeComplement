package com.system.demo.LLM;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 大模型LLM部分，优化缓存机制
 * 更改为本地方法需要更改queryLLM和createHttpClient部分，将apiurl改为本地url,更改请求体，去掉代理，其他文件没必要改动
 */
public class LLMClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // 单例 OkHttpClient
    private static final OkHttpClient client = createHttpClient();

    // 当前正在执行的请求
    private static volatile Call currentCall = null;

    // 改进的缓存：基于上下文哈希
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

    // 创建HTTP客户端
    private static OkHttpClient createHttpClient() {
        // 是否需要代理，如果不需要就删掉Proxy
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7897));

        return new OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build();
    }

    // 缓存部分
    private static final Map<String, CacheEntry> cache = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true);
    private static final Map<String, CacheEntry> sessionCache = new LinkedHashMap<>(50, 0.75f, true); // 跨会话缓存

    private static String generateContextKey(String context) {
        if (context == null) return "empty";

        // 预处理：去除注释和多余空格
        String normalized = context
                .replaceAll("/\\*.*?\\*/", "")
                .replaceAll("//.*", "")
                .replaceAll("\\s+", " ")
                .trim();

        // 提取特征：方法名、类名、操作符
        String features = "";
        if (normalized.contains("class ")) {
            features += "class:";
        }
        if (normalized.contains("public") || normalized.contains("private")) {
            features += "scope:";
        }
        if (normalized.contains("(") && normalized.contains(")")) {
            features += "func:";
        }
        if (normalized.contains("=")) {
            features += "assign:";
        }

        // 取光标前最后300字符提高准确性
        int start = Math.max(0, normalized.length() - 300);
        String recent = normalized.substring(start);

        // 使用稳定哈希（避免不同 JVM 导致的 hashCode 差异）
        String stableHash = Integer.toHexString(recent.getBytes().hashCode());

        return features + "_" + stableHash;
    }

    private static String getCachedSuggestion(String context) {
        String key = generateContextKey(context);

        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.result;
        }

        // 二级缓存命中（最近会话）
        entry = sessionCache.get(key);
        if (entry != null && !entry.isExpired()) {
            cache.put(key, entry); // 升级到主缓存
            return entry.result;
        }

        return null;
    }

    private static void cacheSuggestion(String context, String suggestion) {
        if (suggestion == null || suggestion.trim().isEmpty()) return;

        String key = generateContextKey(context);
        CacheEntry entry = new CacheEntry(suggestion);

        // 自动LRU淘汰
        cache.put(key, entry);
        sessionCache.put(key, entry);
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
     * 查询LLM， 需要更改为本地方法
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

            // 缓存结果的上下文
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