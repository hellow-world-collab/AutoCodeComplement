package com.system.demo.LLM;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * LLM响应缓存
 */
public class LLMCache {
    private static final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        private final String response;
        private final long timestamp;

        public CacheEntry(String response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired(long cacheDurationMs) {
            return System.currentTimeMillis() - timestamp > cacheDurationMs;
        }

        public String getResponse() {
            return response;
        }
    }

    public static String get(String key) {
        LLMSettings settings = LLMSettings.getInstance();
        long cacheDurationMs = TimeUnit.MINUTES.toMillis(settings.cacheTimeoutMinutes);
        
        CacheEntry entry = cache.get(key);
        if (entry == null || entry.isExpired(cacheDurationMs)) {
            if (entry != null) {
                cache.remove(key);
            }
            return null;
        }
        return entry.getResponse();
    }

    public static void put(String key, String response) {
        if (key != null && response != null) {
            cache.put(key, new CacheEntry(response));
        }
    }

    public static void clear() {
        cache.clear();
    }

    public static int size() {
        return cache.size();
    }
}