package com.system.demo.utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 专业的上下文缓存管理器 - 参考IDEA官方实现
 * 特性：
 * 1. 多级缓存（热缓存 + 冷缓存）
 * 2. LRU + 频率混合淘汰策略
 * 3. 语义相似度检测
 * 4. 线程安全
 */
public class ContextCacheManager {
    private static final ContextCacheManager INSTANCE = new ContextCacheManager();
    
    // 缓存配置
    private static final int HOT_CACHE_SIZE = 50;    // 热缓存大小
    private static final int COLD_CACHE_SIZE = 200;  // 冷缓存大小
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5分钟过期
    private static final int MIN_ACCESS_COUNT_FOR_HOT = 2;  // 升级到热缓存的最小访问次数
    
    // 热缓存（最近频繁使用）
    private final Map<String, CacheEntry> hotCache = new LinkedHashMap<String, CacheEntry>(HOT_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            if (size() > HOT_CACHE_SIZE) {
                // 降级到冷缓存
                coldCache.put(eldest.getKey(), eldest.getValue());
                return true;
            }
            return false;
        }
    };
    
    // 冷缓存（较少使用）
    private final Map<String, CacheEntry> coldCache = new LinkedHashMap<String, CacheEntry>(COLD_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > COLD_CACHE_SIZE;
        }
    };
    
    // 访问统计
    private final Map<String, Integer> accessCount = new ConcurrentHashMap<>();
    
    // 读写锁（提高并发性能）
    private final ReadWriteLock hotLock = new ReentrantReadWriteLock();
    private final ReadWriteLock coldLock = new ReentrantReadWriteLock();
    
    private ContextCacheManager() {}
    
    public static ContextCacheManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 缓存条目
     */
    private static class CacheEntry {
        final String suggestion;
        final long timestamp;
        final String semanticHash;  // 语义哈希（用于相似度检测）
        
        CacheEntry(String suggestion, String semanticHash) {
            this.suggestion = suggestion;
            this.timestamp = System.currentTimeMillis();
            this.semanticHash = semanticHash;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
    
    /**
     * 获取缓存的建议
     */
    public String get(String contextKey, String semanticKey) {
        // 1. 先查热缓存
        CacheEntry entry = getFromHotCache(contextKey);
        if (entry != null && !entry.isExpired()) {
            recordAccess(contextKey);
            return entry.suggestion;
        }
        
        // 2. 查冷缓存
        entry = getFromColdCache(contextKey);
        if (entry != null && !entry.isExpired()) {
            recordAccess(contextKey);
            // 如果访问次数足够，升级到热缓存
            promoteToHotIfNeeded(contextKey, entry);
            return entry.suggestion;
        }
        
        // 3. 尝试语义相似匹配（模糊匹配）
        String similarResult = findSimilarInCache(semanticKey);
        if (similarResult != null) {
            return similarResult;
        }
        
        return null;
    }
    
    /**
     * 存储缓存
     */
    public void put(String contextKey, String suggestion, String semanticKey) {
        if (suggestion == null || suggestion.trim().isEmpty()) {
            return;
        }
        
        CacheEntry entry = new CacheEntry(suggestion, semanticKey);
        
        // 直接放入冷缓存，根据访问频率自动升级
        coldLock.writeLock().lock();
        try {
            coldCache.put(contextKey, entry);
        } finally {
            coldLock.writeLock().unlock();
        }
    }
    
    /**
     * 从热缓存获取
     */
    private CacheEntry getFromHotCache(String key) {
        hotLock.readLock().lock();
        try {
            return hotCache.get(key);
        } finally {
            hotLock.readLock().unlock();
        }
    }
    
    /**
     * 从冷缓存获取
     */
    private CacheEntry getFromColdCache(String key) {
        coldLock.readLock().lock();
        try {
            return coldCache.get(key);
        } finally {
            coldLock.readLock().unlock();
        }
    }
    
    /**
     * 记录访问次数
     */
    private void recordAccess(String key) {
        accessCount.merge(key, 1, Integer::sum);
    }
    
    /**
     * 如果访问频率足够，升级到热缓存
     */
    private void promoteToHotIfNeeded(String key, CacheEntry entry) {
        int count = accessCount.getOrDefault(key, 0);
        if (count >= MIN_ACCESS_COUNT_FOR_HOT) {
            hotLock.writeLock().lock();
            coldLock.writeLock().lock();
            try {
                hotCache.put(key, entry);
                coldCache.remove(key);
            } finally {
                coldLock.writeLock().unlock();
                hotLock.writeLock().unlock();
            }
        }
    }
    
    /**
     * 语义相似度检测（简化版）
     * 在实际IDEA实现中，这里会使用更复杂的算法
     */
    private String findSimilarInCache(String semanticKey) {
        if (semanticKey == null || semanticKey.isEmpty()) {
            return null;
        }
        
        // 检查热缓存中的相似项
        hotLock.readLock().lock();
        try {
            for (Map.Entry<String, CacheEntry> entry : hotCache.entrySet()) {
                if (isSemanticallyClose(semanticKey, entry.getValue().semanticHash)) {
                    return entry.getValue().suggestion;
                }
            }
        } finally {
            hotLock.readLock().unlock();
        }
        
        return null;
    }
    
    /**
     * 判断两个语义哈希是否相近
     * 这里使用简单的编辑距离，实际可以使用更复杂的相似度算法
     */
    private boolean isSemanticallyClose(String hash1, String hash2) {
        if (hash1 == null || hash2 == null) {
            return false;
        }
        
        // 简单的相似度检测：如果超过80%的字符相同
        int minLen = Math.min(hash1.length(), hash2.length());
        int maxLen = Math.max(hash1.length(), hash2.length());
        
        if (minLen == 0) return false;
        
        int matches = 0;
        for (int i = 0; i < minLen; i++) {
            if (hash1.charAt(i) == hash2.charAt(i)) {
                matches++;
            }
        }
        
        double similarity = (double) matches / maxLen;
        return similarity >= 0.8;
    }
    
    /**
     * 清理过期缓存（定期调用）
     */
    public void cleanExpired() {
        hotLock.writeLock().lock();
        coldLock.writeLock().lock();
        try {
            hotCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            coldCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        } finally {
            coldLock.writeLock().unlock();
            hotLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        hotLock.readLock().lock();
        coldLock.readLock().lock();
        try {
            return new CacheStats(
                hotCache.size(),
                coldCache.size(),
                HOT_CACHE_SIZE,
                COLD_CACHE_SIZE
            );
        } finally {
            coldLock.readLock().unlock();
            hotLock.readLock().unlock();
        }
    }
    
    /**
     * 清空所有缓存
     */
    public void clear() {
        hotLock.writeLock().lock();
        coldLock.writeLock().lock();
        try {
            hotCache.clear();
            coldCache.clear();
            accessCount.clear();
        } finally {
            coldLock.writeLock().unlock();
            hotLock.writeLock().unlock();
        }
    }
    
    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        public final int hotSize;
        public final int coldSize;
        public final int hotCapacity;
        public final int coldCapacity;
        
        CacheStats(int hotSize, int coldSize, int hotCapacity, int coldCapacity) {
            this.hotSize = hotSize;
            this.coldSize = coldSize;
            this.hotCapacity = hotCapacity;
            this.coldCapacity = coldCapacity;
        }
        
        @Override
        public String toString() {
            return String.format("热缓存: %d/%d, 冷缓存: %d/%d, 总计: %d/%d",
                hotSize, hotCapacity,
                coldSize, coldCapacity,
                hotSize + coldSize, hotCapacity + coldCapacity);
        }
    }
}
