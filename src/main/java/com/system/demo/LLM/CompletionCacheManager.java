package com.system.demo.LLM;

import com.system.demo.utils.EditorContextUtils.CodeContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能代码补全缓存管理器
 * 采用多层缓存策略，更接近IDEA官方实现
 */
public class CompletionCacheManager {
    
    // 单例实例
    private static final CompletionCacheManager INSTANCE = new CompletionCacheManager();
    
    // 精确匹配缓存：完全相同的上下文
    private final Map<String, CacheEntry> exactCache = new ConcurrentHashMap<>();
    
    // 模糊匹配缓存：相似的上下文
    private final Map<String, List<CacheEntry>> fuzzyCache = new ConcurrentHashMap<>();
    
    // 文件级缓存：按文件组织的缓存
    private final Map<String, FileCache> fileCache = new ConcurrentHashMap<>();
    
    // 缓存配置
    private static final int MAX_EXACT_CACHE_SIZE = 100;
    private static final int MAX_FUZZY_CACHE_SIZE = 50;
    private static final int MAX_FILE_CACHE_SIZE = 20;
    private static final long CACHE_TTL_MS = 60000; // 1分钟
    private static final long FILE_CACHE_TTL_MS = 300000; // 5分钟
    
    private CompletionCacheManager() {}
    
    public static CompletionCacheManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 缓存条目
     */
    public static class CacheEntry {
        final String context;
        final String suggestion;
        final long timestamp;
        final int useCount;
        final String cacheKey;
        
        CacheEntry(String context, String suggestion, String cacheKey) {
            this.context = context;
            this.suggestion = suggestion;
            this.timestamp = System.currentTimeMillis();
            this.useCount = 1;
            this.cacheKey = cacheKey;
        }
        
        CacheEntry withIncrementedUse() {
            return new CacheEntry(context, suggestion, cacheKey, useCount + 1, timestamp);
        }
        
        private CacheEntry(String context, String suggestion, String cacheKey, int useCount, long timestamp) {
            this.context = context;
            this.suggestion = suggestion;
            this.cacheKey = cacheKey;
            this.useCount = useCount;
            this.timestamp = timestamp;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
        
        boolean isExpired(long ttl) {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }
    
    /**
     * 文件级缓存
     */
    private static class FileCache {
        final Map<String, CacheEntry> methodCache = new LinkedHashMap<>();
        long lastAccess;
        
        FileCache() {
            this.lastAccess = System.currentTimeMillis();
        }
        
        void touch() {
            this.lastAccess = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - lastAccess > FILE_CACHE_TTL_MS;
        }
    }
    
    /**
     * 获取缓存的补全建议
     */
    public String getCachedSuggestion(CodeContext context) {
        if (context == null) return null;
        
        String cacheKey = generateCacheKey(context);
        
        // 1. 尝试精确匹配
        CacheEntry exact = exactCache.get(cacheKey);
        if (exact != null && !exact.isExpired()) {
            System.out.println("[缓存命中-精确] " + cacheKey.substring(0, Math.min(30, cacheKey.length())));
            exactCache.put(cacheKey, exact.withIncrementedUse()); // 增加使用计数
            return exact.suggestion;
        }
        
        // 2. 尝试文件级缓存
        String fileCacheResult = getFromFileCache(context);
        if (fileCacheResult != null) {
            System.out.println("[缓存命中-文件级] " + context.fileName);
            return fileCacheResult;
        }
        
        // 3. 尝试模糊匹配
        String fuzzyResult = getFuzzyMatch(context, cacheKey);
        if (fuzzyResult != null) {
            System.out.println("[缓存命中-模糊] " + cacheKey.substring(0, Math.min(30, cacheKey.length())));
            return fuzzyResult;
        }
        
        // 清理过期缓存
        cleanupExpiredEntries();
        
        return null;
    }
    
    /**
     * 缓存补全建议
     */
    public void cacheSuggestion(CodeContext context, String suggestion) {
        if (context == null || suggestion == null || suggestion.trim().isEmpty()) {
            return;
        }
        
        String cacheKey = generateCacheKey(context);
        
        // 1. 存入精确缓存
        if (exactCache.size() >= MAX_EXACT_CACHE_SIZE) {
            evictLeastUsedEntry(exactCache);
        }
        exactCache.put(cacheKey, new CacheEntry(context.precedingCode, suggestion, cacheKey));
        
        // 2. 存入文件级缓存
        cacheToFileLevel(context, suggestion, cacheKey);
        
        // 3. 存入模糊缓存
        String fuzzyKey = generateFuzzyKey(context);
        fuzzyCache.computeIfAbsent(fuzzyKey, k -> new ArrayList<>())
                .add(new CacheEntry(context.precedingCode, suggestion, cacheKey));
        
        // 限制模糊缓存大小
        if (fuzzyCache.size() > MAX_FUZZY_CACHE_SIZE) {
            Iterator<String> it = fuzzyCache.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        
        System.out.println("[缓存保存] " + cacheKey.substring(0, Math.min(30, cacheKey.length())) 
                + " -> " + suggestion.substring(0, Math.min(20, suggestion.length())));
    }
    
    /**
     * 生成精确缓存键
     */
    private String generateCacheKey(CodeContext context) {
        // 使用多个维度生成缓存键
        StringBuilder keyBuilder = new StringBuilder();
        
        // 文件和方法信息
        keyBuilder.append(context.fileName).append(":");
        if (context.currentMethodName != null) {
            keyBuilder.append(context.currentMethodName).append(":");
        }
        
        // 前置代码的特征
        String precedingNormalized = normalizeCode(context.precedingCode);
        keyBuilder.append(precedingNormalized.hashCode()).append(":");
        
        // 当前行的特征
        String lineNormalized = normalizeCode(context.currentLine);
        keyBuilder.append(lineNormalized.hashCode()).append(":");
        
        // 局部变量信息
        if (context.localVariables != null && !context.localVariables.isEmpty()) {
            keyBuilder.append(context.localVariables.hashCode());
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * 生成模糊缓存键（用于相似上下文匹配）
     */
    private String generateFuzzyKey(CodeContext context) {
        StringBuilder keyBuilder = new StringBuilder();
        
        // 只使用文件和方法级别的信息
        keyBuilder.append(context.fileName).append(":");
        if (context.currentMethodName != null) {
            keyBuilder.append(context.currentMethodName);
        }
        
        // 提取代码特征（关键字）
        String features = extractCodeFeatures(context.precedingCode);
        keyBuilder.append(":").append(features);
        
        return keyBuilder.toString();
    }
    
    /**
     * 提取代码特征
     */
    private String extractCodeFeatures(String code) {
        if (code == null) return "";
        
        // 提取关键模式
        List<String> features = new ArrayList<>();
        if (code.contains("for (")) features.add("for");
        if (code.contains("if (")) features.add("if");
        if (code.contains("while (")) features.add("while");
        if (code.contains("new ")) features.add("new");
        if (code.contains(".")) features.add("method_call");
        if (code.contains("=")) features.add("assign");
        if (code.matches(".*\\w+\\s*\\(.*")) features.add("func");
        
        return String.join("_", features);
    }
    
    /**
     * 归一化代码（去除空白和注释）
     */
    private String normalizeCode(String code) {
        if (code == null) return "";
        
        return code
            .replaceAll("//[^\\n]*", "")           // 移除单行注释
            .replaceAll("/\\*.*?\\*/", "")         // 移除多行注释
            .replaceAll("\\s+", " ")               // 合并空白
            .trim();
    }
    
    /**
     * 从文件级缓存获取
     */
    private String getFromFileCache(CodeContext context) {
        FileCache fc = fileCache.get(context.fileName);
        if (fc != null && !fc.isExpired()) {
            fc.touch();
            
            // 尝试匹配相同方法的缓存
            if (context.currentMethodName != null) {
                CacheEntry entry = fc.methodCache.get(context.currentMethodName);
                if (entry != null && !entry.isExpired(FILE_CACHE_TTL_MS)) {
                    return entry.suggestion;
                }
            }
        }
        return null;
    }
    
    /**
     * 保存到文件级缓存
     */
    private void cacheToFileLevel(CodeContext context, String suggestion, String cacheKey) {
        FileCache fc = fileCache.computeIfAbsent(context.fileName, k -> new FileCache());
        fc.touch();
        
        if (context.currentMethodName != null) {
            fc.methodCache.put(context.currentMethodName, 
                new CacheEntry(context.precedingCode, suggestion, cacheKey));
        }
        
        // 限制文件缓存数量
        if (fileCache.size() > MAX_FILE_CACHE_SIZE) {
            Iterator<Map.Entry<String, FileCache>> it = fileCache.entrySet().iterator();
            if (it.hasNext()) {
                Map.Entry<String, FileCache> oldest = it.next();
                if (oldest.getValue().isExpired()) {
                    it.remove();
                }
            }
        }
    }
    
    /**
     * 模糊匹配
     */
    private String getFuzzyMatch(CodeContext context, String exactKey) {
        String fuzzyKey = generateFuzzyKey(context);
        List<CacheEntry> candidates = fuzzyCache.get(fuzzyKey);
        
        if (candidates != null && !candidates.isEmpty()) {
            // 找到最相似的候选项
            CacheEntry best = null;
            int bestScore = 0;
            
            for (CacheEntry candidate : candidates) {
                if (candidate.isExpired()) continue;
                
                int score = calculateSimilarity(context.precedingCode, candidate.context);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            
            // 相似度阈值：70%
            if (best != null && bestScore > 70) {
                return best.suggestion;
            }
        }
        
        return null;
    }
    
    /**
     * 计算代码相似度（简化版）
     */
    private int calculateSimilarity(String code1, String code2) {
        if (code1 == null || code2 == null) return 0;
        
        String norm1 = normalizeCode(code1);
        String norm2 = normalizeCode(code2);
        
        // 使用最长公共子序列计算相似度
        int lcs = longestCommonSubsequence(norm1, norm2);
        int maxLen = Math.max(norm1.length(), norm2.length());
        
        return maxLen > 0 ? (lcs * 100 / maxLen) : 0;
    }
    
    /**
     * 最长公共子序列（简化版）
     */
    private int longestCommonSubsequence(String s1, String s2) {
        int m = Math.min(s1.length(), 100); // 限制长度避免性能问题
        int n = Math.min(s2.length(), 100);
        
        int[][] dp = new int[m + 1][n + 1];
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        
        return dp[m][n];
    }
    
    /**
     * 淘汰使用最少的条目（LFU策略）
     */
    private void evictLeastUsedEntry(Map<String, CacheEntry> cache) {
        CacheEntry leastUsed = null;
        String leastUsedKey = null;
        
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (leastUsed == null || entry.getValue().useCount < leastUsed.useCount) {
                leastUsed = entry.getValue();
                leastUsedKey = entry.getKey();
            }
        }
        
        if (leastUsedKey != null) {
            cache.remove(leastUsedKey);
            System.out.println("[缓存淘汰-LFU] " + leastUsedKey.substring(0, Math.min(30, leastUsedKey.length())));
        }
    }
    
    /**
     * 清理过期条目
     */
    private void cleanupExpiredEntries() {
        // 清理精确缓存
        exactCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        
        // 清理模糊缓存
        fuzzyCache.values().forEach(list -> 
            list.removeIf(CacheEntry::isExpired));
        fuzzyCache.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        
        // 清理文件缓存
        fileCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * 清空所有缓存
     */
    public void clearAll() {
        exactCache.clear();
        fuzzyCache.clear();
        fileCache.clear();
        System.out.println("[缓存清空] 所有缓存已清除");
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        return new CacheStats(
            exactCache.size(),
            fuzzyCache.size(),
            fileCache.size(),
            exactCache.values().stream().mapToInt(e -> e.useCount).sum()
        );
    }
    
    /**
     * 缓存统计
     */
    public static class CacheStats {
        public final int exactCacheSize;
        public final int fuzzyCacheSize;
        public final int fileCacheSize;
        public final int totalUseCount;
        
        CacheStats(int exactCacheSize, int fuzzyCacheSize, int fileCacheSize, int totalUseCount) {
            this.exactCacheSize = exactCacheSize;
            this.fuzzyCacheSize = fuzzyCacheSize;
            this.fileCacheSize = fileCacheSize;
            this.totalUseCount = totalUseCount;
        }
        
        @Override
        public String toString() {
            return String.format("精确缓存: %d, 模糊缓存: %d, 文件缓存: %d, 总使用: %d",
                exactCacheSize, fuzzyCacheSize, fileCacheSize, totalUseCount);
        }
    }
}
