package com.system.demo.LLM;

import com.system.demo.utils.ContextCacheManager;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * 大模型LLM客户端 - 优化版本，更接近IDEA官方实现
 * 改进：
 * 1. 使用专业的多级缓存管理器
 * 2. 更智能的缓存键生成（基于语义）
 * 3. 更好的请求取消机制
 * 4. 优化的连接池配置
 */
public class LLMClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // 单例 OkHttpClient（优化配置）
    private static final OkHttpClient client = createHttpClient();

    // 当前正在执行的请求
    private static volatile Call currentCall = null;
    
    // 使用专业的缓存管理器
    private static final ContextCacheManager cacheManager = ContextCacheManager.getInstance();

    /**
     * 创建HTTP客户端（优化配置）
     */
    private static OkHttpClient createHttpClient() {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7897));

        return new OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)  // 增加读取超时
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))  // 增加连接池大小
                .retryOnConnectionFailure(true)  // 启用自动重试
                .build();
    }

    /**
     * 生成优化的缓存键（基于语义特征）
     */
    private static String generateContextKey(String context) {
        if (context == null || context.isEmpty()) {
            return "empty";
        }

        // 标准化上下文：去除注释和多余空格
        String normalized = normalizeContext(context);
        
        // 提取语义特征
        String features = extractSemanticFeatures(normalized);
        
        // 使用MD5生成稳定哈希（跨JVM一致）
        String contentHash = generateStableHash(normalized);
        
        return features + "_" + contentHash;
    }
    
    /**
     * 标准化上下文
     */
    private static String normalizeContext(String context) {
        return context
                .replaceAll("/\\*.*?\\*/", "")  // 移除块注释
                .replaceAll("//.*?\\n", "")      // 移除行注释
                .replaceAll("\\s+", " ")         // 规范化空格
                .trim();
    }
    
    /**
     * 提取语义特征（用于缓存键）
     */
    private static String extractSemanticFeatures(String context) {
        StringBuilder features = new StringBuilder();
        
        // 检测代码结构特征
        if (context.contains("class ") || context.contains("interface ")) {
            features.append("C");  // Class/Interface
        }
        if (context.contains("public") || context.contains("private") || context.contains("protected")) {
            features.append("V");  // Visibility
        }
        if (context.matches(".*\\b(void|int|String|boolean|double|float)\\b.*")) {
            features.append("T");  // Type
        }
        if (context.contains("(") && context.contains(")")) {
            features.append("M");  // Method
        }
        if (context.contains("=") || context.contains("return")) {
            features.append("S");  // Statement
        }
        if (context.contains("if") || context.contains("for") || context.contains("while")) {
            features.append("L");  // Loop/Condition
        }
        
        return features.toString();
    }
    
    /**
     * 生成稳定的哈希值（使用MD5）
     */
    private static String generateStableHash(String text) {
        try {
            // 取最近的关键部分（最后500字符）
            String relevant = text.length() > 500 ? text.substring(text.length() - 500) : text;
            
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(relevant.getBytes(StandardCharsets.UTF_8));
            
            // 转换为16进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.substring(0, 12);  // 取前12位
        } catch (Exception e) {
            // 降级到简单哈希
            return Integer.toHexString(text.hashCode());
        }
    }
    
    /**
     * 生成语义键（用于相似度匹配）
     */
    private static String generateSemanticKey(String context) {
        if (context == null || context.isEmpty()) {
            return "";
        }
        
        // 提取关键词和结构
        String normalized = normalizeContext(context);
        String features = extractSemanticFeatures(normalized);
        
        // 取最后100个字符作为语义上下文
        String semantic = normalized.length() > 100 
            ? normalized.substring(normalized.length() - 100) 
            : normalized;
            
        return features + "_" + semantic.replaceAll("\\s+", "");
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
     * 查询LLM（优化版本）
     */
    public static String queryLLM(String prompt, String context) {
        // 生成缓存键
        String cacheKey = generateContextKey(context);
        String semanticKey = generateSemanticKey(context);
        
        // 首先尝试从多级缓存获取
        String cached = cacheManager.get(cacheKey, semanticKey);
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

            // 缓存结果（使用优化的缓存管理器）
            cacheManager.put(cacheKey, completion, semanticKey);
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
        return cacheManager.getStats().toString();
    }

    /**
     * 清空缓存（用于测试）
     */
    public static void clearCache() {
        cacheManager.clear();
    }
    
    /**
     * 定期清理过期缓存
     */
    public static void cleanExpiredCache() {
        cacheManager.cleanExpired();
    }
}