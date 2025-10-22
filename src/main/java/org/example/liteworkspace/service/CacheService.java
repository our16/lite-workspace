package org.example.liteworkspace.service;

import com.intellij.openapi.project.Project;
import org.example.liteworkspace.bean.core.BeanDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 缓存服务接口
 * 负责插件数据的缓存管理、失效策略和持久化
 */
public interface CacheService {
    
    /**
     * 缓存Bean定义列表
     * 
     * @param project 项目
     * @param key 缓存键
     * @param beans Bean定义列表
     */
    void cacheBeanDefinitions(Project project, String key, Collection<BeanDefinition> beans);
    
    /**
     * 获取缓存的Bean定义列表
     * 
     * @param project 项目
     * @param key 缓存键
     * @return Bean定义列表，如果不存在返回null
     */
    Collection<BeanDefinition> getCachedBeanDefinitions(Project project, String key);
    
    /**
     * 异步缓存Bean定义列表
     * 
     * @param project 项目
     * @param key 缓存键
     * @param beans Bean定义列表
     * @return CompletableFuture
     */
    CompletableFuture<Void> cacheBeanDefinitionsAsync(Project project, String key, Collection<BeanDefinition> beans);
    
    /**
     * 异步获取缓存的Bean定义列表
     * 
     * @param project 项目
     * @param key 缓存键
     * @return CompletableFuture包含Bean定义列表
     */
    CompletableFuture<Collection<BeanDefinition>> getCachedBeanDefinitionsAsync(Project project, String key);
    
    /**
     * 缓存分析结果
     * 
     * @param project 项目
     * @param key 缓存键
     * @param result 分析结果
     */
    void cacheAnalysisResult(Project project, String key, Object result);
    
    /**
     * 获取缓存的分析结果
     * 
     * @param project 项目
     * @param key 缓存键
     * @param resultType 结果类型
     * @return 分析结果，如果不存在返回null
     */
    <T> T getCachedAnalysisResult(Project project, String key, Class<T> resultType);
    
    /**
     * 检查缓存是否存在且有效
     * 
     * @param project 项目
     * @param key 缓存键
     * @return 缓存是否存在且有效
     */
    boolean isCacheValid(Project project, String key);
    
    /**
     * 使缓存失效
     * 
     * @param project 项目
     * @param key 缓存键
     */
    void invalidateCache(Project project, String key);
    
    /**
     * 使所有缓存失效
     * 
     * @param project 项目
     */
    void invalidateAllCache(Project project);
    
    /**
     * 清理过期缓存
     * 
     * @param project 项目
     * @return 清理的缓存数量
     */
    int cleanupExpiredCache(Project project);
    
    /**
     * 获取缓存统计信息
     * 
     * @param project 项目
     * @return 缓存统计信息
     */
    CacheStatistics getCacheStatistics(Project project);
    
    /**
     * 持久化缓存到磁盘
     * 
     * @param project 项目
     * @return 持久化是否成功
     */
    CompletableFuture<Boolean> persistCache(Project project);
    
    /**
     * 从磁盘加载缓存
     * 
     * @param project 项目
     * @return 加载是否成功
     */
    CompletableFuture<Boolean> loadCache(Project project);
    
    /**
     * 设置缓存过期时间
     * 
     * @param project 项目
     * @param expireTime 过期时间（毫秒）
     */
    void setCacheExpireTime(Project project, long expireTime);
    
    /**
     * 获取缓存过期时间
     * 
     * @param project 项目
     * @return 过期时间（毫秒）
     */
    long getCacheExpireTime(Project project);
    
    /**
     * 缓存统计信息
     */
    class CacheStatistics {
        private final int totalEntries;
        private final int validEntries;
        private final int expiredEntries;
        private final long memoryUsage;
        private final long diskUsage;
        private final double hitRate;
        private final double missRate;
        
        public CacheStatistics(int totalEntries, int validEntries, int expiredEntries, 
                              long memoryUsage, long diskUsage, double hitRate, double missRate) {
            this.totalEntries = totalEntries;
            this.validEntries = validEntries;
            this.expiredEntries = expiredEntries;
            this.memoryUsage = memoryUsage;
            this.diskUsage = diskUsage;
            this.hitRate = hitRate;
            this.missRate = missRate;
        }
        
        public int getTotalEntries() {
            return totalEntries;
        }
        
        public int getValidEntries() {
            return validEntries;
        }
        
        public int getExpiredEntries() {
            return expiredEntries;
        }
        
        public long getMemoryUsage() {
            return memoryUsage;
        }
        
        public long getDiskUsage() {
            return diskUsage;
        }
        
        public double getHitRate() {
            return hitRate;
        }
        
        public double getMissRate() {
            return missRate;
        }
        
        @Override
        public String toString() {
            return String.format(
                "CacheStatistics{totalEntries=%d, validEntries=%d, expiredEntries=%d, " +
                "memoryUsage=%d bytes, diskUsage=%d bytes, hitRate=%.2f%%, missRate=%.2f%%}",
                totalEntries, validEntries, expiredEntries, memoryUsage, diskUsage, 
                hitRate * 100, missRate * 100
            );
        }
    }
    
    /**
     * 缓存监听器
     */
    interface CacheListener {
        /**
         * 缓存命中时调用
         * 
         * @param project 项目
         * @param key 缓存键
         */
        void onCacheHit(Project project, String key);
        
        /**
         * 缓存未命中时调用
         * 
         * @param project 项目
         * @param key 缓存键
         */
        void onCacheMiss(Project project, String key);
        
        /**
         * 缓存失效时调用
         * 
         * @param project 项目
         * @param key 缓存键
         */
        void onCacheInvalidated(Project project, String key);
        
        /**
         * 缓存清理时调用
         * 
         * @param project 项目
         * @param cleanedCount 清理的缓存数量
         */
        void onCacheCleaned(Project project, int cleanedCount);
    }
    
    /**
     * 添加缓存监听器
     * 
     * @param listener 监听器
     */
    void addCacheListener(CacheListener listener);
    
    /**
     * 移除缓存监听器
     * 
     * @param listener 监听器
     */
    void removeCacheListener(CacheListener listener);
}
