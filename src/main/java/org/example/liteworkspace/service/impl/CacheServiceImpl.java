package org.example.liteworkspace.service.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.service.CacheService;
import org.example.liteworkspace.util.LogUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存服务实现
 */
@Service
public final class CacheServiceImpl implements CacheService {
    
    private final Map<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();
    private final List<CacheListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private volatile long cacheExpireTime = 30 * 60 * 1000; // 默认30分钟过期
    
    @Override
    public void cacheBeanDefinitions(Project project, String key, Collection<BeanDefinition> beans) {
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(beans, "Beans cannot be null");
        
        CacheEntry entry = new CacheEntry(beans, System.currentTimeMillis() + cacheExpireTime);
        memoryCache.put(key, entry);
        
        LogUtil.debug("缓存Bean定义: {}, 数量: {}", key, beans.size());
    }
    
    @Override
    public Collection<BeanDefinition> getCachedBeanDefinitions(Project project, String key) {
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");
        
        CacheEntry entry = memoryCache.get(key);
        if (entry == null) {
            missCount.incrementAndGet();
            notifyCacheMiss(project, key);
            return null;
        }
        
        if (entry.isExpired()) {
            memoryCache.remove(key);
            missCount.incrementAndGet();
            notifyCacheMiss(project, key);
            return null;
        }
        
        hitCount.incrementAndGet();
        notifyCacheHit(project, key);
        
        @SuppressWarnings("unchecked")
        Collection<BeanDefinition> result = (Collection<BeanDefinition>) entry.getValue();
        return result;
    }
    
    @Override
    public CompletableFuture<Void> cacheBeanDefinitionsAsync(Project project, String key, Collection<BeanDefinition> beans) {
        return CompletableFuture.runAsync(() -> {
            cacheBeanDefinitions(project, key, beans);
        });
    }
    
    @Override
    public CompletableFuture<Collection<BeanDefinition>> getCachedBeanDefinitionsAsync(Project project, String key) {
        return CompletableFuture.supplyAsync(() -> {
            return getCachedBeanDefinitions(project, key);
        });
    }
    
    @Override
    public void cacheAnalysisResult(Project project, String key, Object result) {
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(result, "Result cannot be null");
        
        CacheEntry entry = new CacheEntry(result, System.currentTimeMillis() + cacheExpireTime);
        memoryCache.put(key, entry);
        
        LogUtil.debug("缓存分析结果: {}", key);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCachedAnalysisResult(Project project, String key, Class<T> resultType) {
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(resultType, "ResultType cannot be null");
        
        CacheEntry entry = memoryCache.get(key);
        if (entry == null) {
            missCount.incrementAndGet();
            notifyCacheMiss(project, key);
            return null;
        }
        
        if (entry.isExpired()) {
            memoryCache.remove(key);
            missCount.incrementAndGet();
            notifyCacheMiss(project, key);
            return null;
        }
        
        hitCount.incrementAndGet();
        notifyCacheHit(project, key);
        
        Object value = entry.getValue();
        if (resultType.isInstance(value)) {
            return (T) value;
        }
        
        return null;
    }
    
    @Override
    public boolean isCacheValid(Project project, String key) {
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");
        
        CacheEntry entry = memoryCache.get(key);
        return entry != null && !entry.isExpired();
    }
    
    @Override
    public void invalidateCache(Project project, String key) {
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");
        
        memoryCache.remove(key);
        notifyCacheInvalidated(project, key);
        
        LogUtil.debug("使缓存失效: {}", key);
    }
    
    @Override
    public void invalidateAllCache(Project project) {
        Objects.requireNonNull(project, "Project cannot be null");
        
        int size = memoryCache.size();
        memoryCache.clear();
        
        LogUtil.debug("清理所有缓存，数量: {}", size);
    }
    
    @Override
    public int cleanupExpiredCache(Project project) {
        Objects.requireNonNull(project, "Project cannot be null");
        
        long currentTime = System.currentTimeMillis();
        int cleanedCount = 0;
        
        Iterator<Map.Entry<String, CacheEntry>> iterator = memoryCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CacheEntry> entry = iterator.next();
            if (entry.getValue().isExpired(currentTime)) {
                iterator.remove();
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            notifyCacheCleaned(project, cleanedCount);
            LogUtil.debug("清理过期缓存，数量: {}", cleanedCount);
        }
        
        return cleanedCount;
    }
    
    @Override
    public CacheStatistics getCacheStatistics(Project project) {
        Objects.requireNonNull(project, "Project cannot be null");
        
        long currentTime = System.currentTimeMillis();
        int totalEntries = memoryCache.size();
        int validEntries = 0;
        int expiredEntries = 0;
        long memoryUsage = 0;
        
        for (CacheEntry entry : memoryCache.values()) {
            if (entry.isExpired(currentTime)) {
                expiredEntries++;
            } else {
                validEntries++;
            }
            memoryUsage += entry.getMemoryUsage();
        }
        
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total : 0.0;
        double missRate = total > 0 ? (double) misses / total : 0.0;
        
        return new CacheStatistics(totalEntries, validEntries, expiredEntries, 
                                  memoryUsage, 0, hitRate, missRate);
    }
    
    @Override
    public CompletableFuture<Boolean> persistCache(Project project) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path cacheDir = getCacheDirectory(project);
                Files.createDirectories(cacheDir);
                
                Path cacheFile = cacheDir.resolve("cache.dat");
                try (ObjectOutputStream oos = new ObjectOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(cacheFile)))) {
                    
                    oos.writeObject(memoryCache);
                    oos.writeLong(cacheExpireTime);
                    oos.writeLong(hitCount.get());
                    oos.writeLong(missCount.get());
                }
                
                LogUtil.info("缓存持久化成功: {}", cacheFile);
                return true;
                
            } catch (IOException e) {
                LogUtil.error("缓存持久化失败", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> loadCache(Project project) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path cacheFile = getCacheDirectory(project).resolve("cache.dat");
                if (!Files.exists(cacheFile)) {
                    return false;
                }
                
                try (ObjectInputStream ois = new ObjectInputStream(
                        new BufferedInputStream(Files.newInputStream(cacheFile)))) {
                    
                    @SuppressWarnings("unchecked")
                    Map<String, CacheEntry> loadedCache = (Map<String, CacheEntry>) ois.readObject();
                    memoryCache.clear();
                    memoryCache.putAll(loadedCache);
                    
                    cacheExpireTime = ois.readLong();
                    hitCount.set(ois.readLong());
                    missCount.set(ois.readLong());
                }
                
                LogUtil.info("缓存加载成功: {}", cacheFile);
                return true;
                
            } catch (IOException | ClassNotFoundException e) {
                LogUtil.error("缓存加载失败", e);
                return false;
            }
        });
    }
    
    @Override
    public void setCacheExpireTime(Project project, long expireTime) {
        Objects.requireNonNull(project, "Project cannot be null");
        
        if (expireTime < 0) {
            throw new IllegalArgumentException("Expire time must be non-negative");
        }
        
        this.cacheExpireTime = expireTime;
        LogUtil.debug("设置缓存过期时间: {} ms", expireTime);
    }
    
    @Override
    public long getCacheExpireTime(Project project) {
        Objects.requireNonNull(project, "Project cannot be null");
        return cacheExpireTime;
    }
    
    @Override
    public void addCacheListener(CacheListener listener) {
        listeners.add(Objects.requireNonNull(listener, "Listener cannot be null"));
    }
    
    @Override
    public void removeCacheListener(CacheListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 获取缓存目录
     */
    private Path getCacheDirectory(Project project) {
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            throw new IllegalStateException("Project base path is null");
        }
        
        return Paths.get(projectPath, ".idea", "lite-workspace", "cache");
    }
    
    /**
     * 通知缓存命中
     */
    private void notifyCacheHit(Project project, String key) {
        for (CacheListener listener : listeners) {
            try {
                listener.onCacheHit(project, key);
            } catch (Exception e) {
                LogUtil.error("缓存命中监听器执行失败", e);
            }
        }
    }
    
    /**
     * 通知缓存未命中
     */
    private void notifyCacheMiss(Project project, String key) {
        for (CacheListener listener : listeners) {
            try {
                listener.onCacheMiss(project, key);
            } catch (Exception e) {
                LogUtil.error("缓存未命中监听器执行失败", e);
            }
        }
    }
    
    /**
     * 通知缓存失效
     */
    private void notifyCacheInvalidated(Project project, String key) {
        for (CacheListener listener : listeners) {
            try {
                listener.onCacheInvalidated(project, key);
            } catch (Exception e) {
                LogUtil.error("缓存失效监听器执行失败", e);
            }
        }
    }
    
    /**
     * 通知缓存清理
     */
    private void notifyCacheCleaned(Project project, int cleanedCount) {
        for (CacheListener listener : listeners) {
            try {
                listener.onCacheCleaned(project, cleanedCount);
            } catch (Exception e) {
                LogUtil.error("缓存清理监听器执行失败", e);
            }
        }
    }
    
    /**
     * 缓存条目
     */
    private static class CacheEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final Object value;
        private final long expireTime;
        private final long memoryUsage;
        
        public CacheEntry(Object value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
            this.memoryUsage = estimateMemoryUsage(value);
        }
        
        public Object getValue() {
            return value;
        }
        
        public long getExpireTime() {
            return expireTime;
        }
        
        public long getMemoryUsage() {
            return memoryUsage;
        }
        
        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }
        
        public boolean isExpired(long currentTime) {
            return currentTime > expireTime;
        }
        
        /**
         * 估算内存使用量
         */
        private long estimateMemoryUsage(Object obj) {
            // 简单的内存估算，实际项目中可以使用更精确的方法
            if (obj == null) {
                return 0;
            }
            
            if (obj instanceof Collection) {
                return ((Collection<?>) obj).size() * 100; // 假设每个对象100字节
            }
            
            return 100; // 默认估算值
        }
    }
}
