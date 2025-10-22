package org.example.liteworkspace.bean.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * 线程安全的Bean注册表
 * 
 * 主要改进：
 * 1. 使用 ConcurrentHashMap 替代 LinkedHashMap
 * 2. 添加读写锁保护复合操作
 * 3. 线程安全的统计信息
 * 4. 原子操作支持
 */
public class ThreadSafeBeanRegistry {
    
    /**
     * 注册表统计信息
     */
    public static class RegistryStatistics {
        private volatile long totalRegistrations = 0;
        private volatile long totalLookups = 0;
        private volatile long totalHits = 0;
        private volatile long totalMisses = 0;
        private volatile long totalClears = 0;
        private final Map<String, Long> beanTypeCounts = new ConcurrentHashMap<>();
        private final Map<String, Long> beanSourceCounts = new ConcurrentHashMap<>();
        
        public void recordRegistration(String beanType, String source) {
            totalRegistrations++;
            beanTypeCounts.merge(beanType, 1L, Long::sum);
            beanSourceCounts.merge(source, 1L, Long::sum);
        }
        
        public void recordLookup(boolean hit) {
            totalLookups++;
            if (hit) {
                totalHits++;
            } else {
                totalMisses++;
            }
        }
        
        public void recordClear() {
            totalClears++;
        }
        
        public double getHitRate() {
            return totalLookups == 0 ? 0.0 : (double) totalHits / totalLookups;
        }
        
        // Getters
        public long getTotalRegistrations() { return totalRegistrations; }
        public long getTotalLookups() { return totalLookups; }
        public long getTotalHits() { return totalHits; }
        public long getTotalMisses() { return totalMisses; }
        public long getTotalClears() { return totalClears; }
        public Map<String, Long> getBeanTypeCounts() { return new HashMap<>(beanTypeCounts); }
        public Map<String, Long> getBeanSourceCounts() { return new HashMap<>(beanSourceCounts); }
        
        @Override
        public String toString() {
            return String.format(
                "RegistryStats{registrations=%d, lookups=%d, hits=%d, misses=%d, hitRate=%.2f%%, clears=%d}",
                totalRegistrations, totalLookups, totalHits, totalMisses, getHitRate() * 100, totalClears
            );
        }
    }
    
    // 核心存储
    private final Map<String, BeanDefinition> beanMap;
    private final ReadWriteLock lock;
    private final RegistryStatistics statistics;
    private volatile boolean isShutdown;
    
    /**
     * 默认构造函数 - 使用ConcurrentHashMap
     */
    public ThreadSafeBeanRegistry() {
        this.beanMap = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.statistics = new RegistryStatistics();
        this.isShutdown = false;
    }
    
    /**
     * 构造函数 - 允许指定Map实现
     */
    public ThreadSafeBeanRegistry(Map<String, BeanDefinition> map) {
        this.beanMap = map != null ? map : new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.statistics = new RegistryStatistics();
        this.isShutdown = false;
    }
    
    /**
     * 注册Bean定义
     */
    public boolean register(BeanDefinition bean) {
        if (isShutdown || bean == null) {
            return false;
        }
        
        lock.writeLock().lock();
        try {
            BeanDefinition existing = beanMap.putIfAbsent(bean.getBeanName(), bean);
            if (existing == null) {
                // 新注册
                statistics.recordRegistration(
                    bean.getClass().getSimpleName(), 
                    bean.getClassName() != null ? bean.getClassName() : "unknown"
                );
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 强制注册Bean定义（覆盖已存在的）
     */
    public void forceRegister(BeanDefinition bean) {
        if (isShutdown || bean == null) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            boolean isNew = !beanMap.containsKey(bean.getBeanName());
            beanMap.put(bean.getBeanName(), bean);
            
            if (isNew) {
                statistics.recordRegistration(
                    bean.getClass().getSimpleName(), 
                    bean.getClassName() != null ? bean.getClassName() : "unknown"
                );
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 批量注册Bean定义
     */
    public int registerAll(Collection<BeanDefinition> beans) {
        if (isShutdown || beans == null || beans.isEmpty()) {
            return 0;
        }
        
        lock.writeLock().lock();
        try {
            int count = 0;
            for (BeanDefinition bean : beans) {
                if (bean != null && beanMap.putIfAbsent(bean.getBeanName(), bean) == null) {
                    statistics.recordRegistration(
                        bean.getClass().getSimpleName(), 
                        bean.getClassName() != null ? bean.getClassName() : "unknown"
                    );
                    count++;
                }
            }
            return count;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取Bean定义
     */
    public BeanDefinition get(String beanName) {
        if (isShutdown || beanName == null) {
            return null;
        }
        
        lock.readLock().lock();
        try {
            BeanDefinition bean = beanMap.get(beanName);
            statistics.recordLookup(bean != null);
            return bean;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取Bean定义（带类型检查）
     */
    @SuppressWarnings("unchecked")
    public <T extends BeanDefinition> T get(String beanName, Class<T> type) {
        BeanDefinition bean = get(beanName);
        if (bean != null && type.isInstance(bean)) {
            return (T) bean;
        }
        return null;
    }
    
    /**
     * 检查是否包含指定Bean
     */
    public boolean contains(String beanName) {
        if (isShutdown || beanName == null) {
            return false;
        }
        
        lock.readLock().lock();
        try {
            return beanMap.containsKey(beanName);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取所有Bean定义
     */
    public Collection<BeanDefinition> getAllBeans() {
        if (isShutdown) {
            return Collections.emptyList();
        }
        
        lock.readLock().lock();
        try {
            return new ArrayList<>(beanMap.values());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取所有Bean名称
     */
    public Set<String> getAllBeanNames() {
        if (isShutdown) {
            return Collections.emptySet();
        }
        
        lock.readLock().lock();
        try {
            return new HashSet<>(beanMap.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 根据类型获取Bean定义
     */
    public List<BeanDefinition> getBeansByType(Class<?> beanType) {
        if (isShutdown || beanType == null) {
            return Collections.emptyList();
        }
        
        lock.readLock().lock();
        try {
            return beanMap.values().stream()
                .filter(bean -> beanType.isInstance(bean))
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 根据来源获取Bean定义
     */
    public List<BeanDefinition> getBeansBySource(String source) {
        if (isShutdown || source == null) {
            return Collections.emptyList();
        }
        
        lock.readLock().lock();
        try {
            return beanMap.values().stream()
                .filter(bean -> source.equals(bean.getClassName()))
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 移除Bean定义
     */
    public boolean remove(String beanName) {
        if (isShutdown || beanName == null) {
            return false;
        }
        
        lock.writeLock().lock();
        try {
            return beanMap.remove(beanName) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 批量移除Bean定义
     */
    public int removeAll(Collection<String> beanNames) {
        if (isShutdown || beanNames == null || beanNames.isEmpty()) {
            return 0;
        }
        
        lock.writeLock().lock();
        try {
            int count = 0;
            for (String beanName : beanNames) {
                if (beanMap.remove(beanName) != null) {
                    count++;
                }
            }
            return count;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 清空所有Bean定义
     */
    public void clear() {
        if (isShutdown) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            beanMap.clear();
            statistics.recordClear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取Bean数量
     */
    public int size() {
        if (isShutdown) {
            return 0;
        }
        
        lock.readLock().lock();
        try {
            return beanMap.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return size() == 0;
    }
    
    /**
     * 获取统计信息
     */
    public RegistryStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 创建快照
     */
    public Map<String, BeanDefinition> createSnapshot() {
        if (isShutdown) {
            return Collections.emptyMap();
        }
        
        lock.readLock().lock();
        try {
            return new HashMap<>(beanMap);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 从快照恢复
     */
    public void restoreFromSnapshot(Map<String, BeanDefinition> snapshot) {
        if (isShutdown || snapshot == null) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            beanMap.clear();
            beanMap.putAll(snapshot);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 执行原子操作
     */
    public <T> T executeAtomic(Function<Map<String, BeanDefinition>, T> operation) {
        if (isShutdown || operation == null) {
            return null;
        }
        
        lock.writeLock().lock();
        try {
            return operation.apply(beanMap);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 执行读操作
     */
    public <T> T executeRead(Function<Map<String, BeanDefinition>, T> operation) {
        if (isShutdown || operation == null) {
            return null;
        }
        
        lock.readLock().lock();
        try {
            return operation.apply(beanMap);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 关闭注册表
     */
    public void shutdown() {
        lock.writeLock().lock();
        try {
            isShutdown = true;
            beanMap.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 检查是否已关闭
     */
    public boolean isShutdown() {
        return isShutdown;
    }
    
    @Override
    public String toString() {
        if (isShutdown) {
            return "ThreadSafeBeanRegistry{shutdown=true}";
        }
        
        lock.readLock().lock();
        try {
            return String.format("ThreadSafeBeanRegistry{size=%d, statistics=%s}", 
                beanMap.size(), statistics);
        } finally {
            lock.readLock().unlock();
        }
    }
}
