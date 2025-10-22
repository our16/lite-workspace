package org.example.liteworkspace.resource;

import com.intellij.openapi.project.Project;
import org.example.liteworkspace.event.EventBus;
import org.example.liteworkspace.event.PluginEvents;
import org.example.liteworkspace.util.OptimizedLogUtil;

import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * 资源管理器
 * 
 * 主要功能：
 * 1. 资源自动管理
 * 2. 资源使用监控
 * 3. 优雅的资源释放
 * 4. 内存泄漏检测
 * 5. 资源使用统计
 */
public class ResourceManager {
    
    /**
     * 资源类型枚举
     */
    public enum ResourceType {
        PSI_OBJECT("PSI对象"),
        CACHE_ENTRY("缓存条目"),
        TASK("任务"),
        EVENT_LISTENER("事件监听器"),
        FILE_HANDLE("文件句柄"),
        THREAD_POOL("线程池"),
        MEMORY_BUFFER("内存缓冲区"),
        TEMP_FILE("临时文件"),
        DATABASE_CONNECTION("数据库连接"),
        NETWORK_CONNECTION("网络连接");
        
        private final String description;
        
        ResourceType(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * 资源状态枚举
     */
    public enum ResourceStatus {
        ACTIVE("活跃"),
        IDLE("空闲"),
        EXPIRED("过期"),
        DISPOSED("已释放"),
        LEAKED("泄漏");
        
        private final String description;
        
        ResourceStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * 资源描述符
     */
    public static class ResourceDescriptor {
        private final String resourceId;
        private final ResourceType type;
        private final Object resource;
        private final long creationTime;
        private final long lastAccessTime;
        private final AtomicLong accessCount;
        private volatile ResourceStatus status;
        private final Map<String, Object> metadata;
        private final Runnable cleanupTask;
        
        public ResourceDescriptor(String resourceId, ResourceType type, Object resource, Runnable cleanupTask) {
            this.resourceId = resourceId;
            this.type = type;
            this.resource = resource;
            this.creationTime = System.currentTimeMillis();
            this.lastAccessTime = System.currentTimeMillis();
            this.accessCount = new AtomicLong(0);
            this.status = ResourceStatus.ACTIVE;
            this.metadata = new ConcurrentHashMap<>();
            this.cleanupTask = cleanupTask;
        }
        
        public void access() {
            accessCount.incrementAndGet();
            // 更新最后访问时间需要特殊处理，因为这里不能直接修改 volatile 字段
        }
        
        public void markDisposed() {
            this.status = ResourceStatus.DISPOSED;
        }
        
        public void markLeaked() {
            this.status = ResourceStatus.LEAKED;
        }
        
        public void addMetadata(String key, Object value) {
            metadata.put(key, value);
        }
        
        public Object getMetadata(String key) {
            return metadata.get(key);
        }
        
        // Getters
        public String getResourceId() { return resourceId; }
        public ResourceType getType() { return type; }
        public Object getResource() { return resource; }
        public long getCreationTime() { return creationTime; }
        public long getLastAccessTime() { return lastAccessTime; }
        public long getAccessCount() { return accessCount.get(); }
        public ResourceStatus getStatus() { return status; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        public Runnable getCleanupTask() { return cleanupTask; }
        
        @Override
        public String toString() {
            return String.format("Resource{id='%s', type=%s, status=%s, accessCount=%d, age=%dms}",
                resourceId, type, status, accessCount.get(), System.currentTimeMillis() - creationTime);
        }
    }
    
    /**
     * 资源统计信息
     */
    public static class ResourceStatistics {
        private final Map<ResourceType, AtomicLong> totalResources = new ConcurrentHashMap<>();
        private final Map<ResourceType, AtomicLong> activeResources = new ConcurrentHashMap<>();
        private final Map<ResourceType, AtomicLong> disposedResources = new ConcurrentHashMap<>();
        private final Map<ResourceType, AtomicLong> leakedResources = new ConcurrentHashMap<>();
        private final AtomicLong totalMemoryUsage = new AtomicLong(0);
        private final AtomicLong peakMemoryUsage = new AtomicLong(0);
        private final Map<String, AtomicLong> resourceAccessCounts = new ConcurrentHashMap<>();
        
        public void recordResourceCreation(ResourceType type, long memoryUsage) {
            totalResources.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();
            activeResources.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();
            totalMemoryUsage.addAndGet(memoryUsage);
            updatePeakMemoryUsage();
        }
        
        public void recordResourceDisposal(ResourceType type, long memoryUsage) {
            activeResources.computeIfAbsent(type, k -> new AtomicLong(0)).decrementAndGet();
            disposedResources.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();
            totalMemoryUsage.addAndGet(-memoryUsage);
        }
        
        public void recordResourceLeak(ResourceType type) {
            activeResources.computeIfAbsent(type, k -> new AtomicLong(0)).decrementAndGet();
            leakedResources.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        public void recordResourceAccess(String resourceId) {
            resourceAccessCounts.computeIfAbsent(resourceId, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        private void updatePeakMemoryUsage() {
            long current = totalMemoryUsage.get();
            long peak = peakMemoryUsage.get();
            while (current > peak && !peakMemoryUsage.compareAndSet(peak, current)) {
                peak = peakMemoryUsage.get();
            }
        }
        
        public long getTotalResourceCount() {
            return totalResources.values().stream().mapToLong(AtomicLong::get).sum();
        }
        
        public long getActiveResourceCount() {
            return activeResources.values().stream().mapToLong(AtomicLong::get).sum();
        }
        
        public long getCurrentMemoryUsage() {
            return totalMemoryUsage.get();
        }
        
        public long getPeakMemoryUsage() {
            return peakMemoryUsage.get();
        }
        
        public Map<ResourceType, Long> getResourceCountsByType() {
            return totalResources.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
        }
        
        @Override
        public String toString() {
            return String.format(
                "ResourceStats{total=%d, active=%d, disposed=%d, leaked=%d, memory=%dMB, peakMemory=%dMB}",
                getTotalResourceCount(), getActiveResourceCount(),
                disposedResources.values().stream().mapToLong(AtomicLong::get).sum(),
                leakedResources.values().stream().mapToLong(AtomicLong::get).sum(),
                getCurrentMemoryUsage() / 1024 / 1024, getPeakMemoryUsage() / 1024 / 1024
            );
        }
    }
    
    /**
     * 资源清理策略
     */
    public interface ResourceCleanupStrategy {
        boolean shouldCleanup(ResourceDescriptor descriptor);
        void cleanup(ResourceDescriptor descriptor);
    }
    
    /**
     * 基于时间的清理策略
     */
    public static class TimeBasedCleanupStrategy implements ResourceCleanupStrategy {
        private final long maxIdleTime;
        
        public TimeBasedCleanupStrategy(long maxIdleTime) {
            this.maxIdleTime = maxIdleTime;
        }
        
        @Override
        public boolean shouldCleanup(ResourceDescriptor descriptor) {
            return System.currentTimeMillis() - descriptor.getLastAccessTime() > maxIdleTime;
        }
        
        @Override
        public void cleanup(ResourceDescriptor descriptor) {
            disposeResource(descriptor);
        }
    }
    
    /**
     * 基于内存压力的清理策略
     */
    public static class MemoryPressureCleanupStrategy implements ResourceCleanupStrategy {
        private final double memoryThreshold;
        private final Runtime runtime;
        
        public MemoryPressureCleanupStrategy(double memoryThreshold) {
            this.memoryThreshold = memoryThreshold;
            this.runtime = Runtime.getRuntime();
        }
        
        @Override
        public boolean shouldCleanup(ResourceDescriptor descriptor) {
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryUsageRatio = (double) usedMemory / maxMemory;
            return memoryUsageRatio > memoryThreshold;
        }
        
        @Override
        public void cleanup(ResourceDescriptor descriptor) {
            disposeResource(descriptor);
        }
    }
    
    // 核心组件
    private static volatile ResourceManager instance;
    private static final Object INSTANCE_LOCK = new Object();
    
    private final Map<String, ResourceDescriptor> resources = new ConcurrentHashMap<>();
    private final Map<String, WeakReference<Object>> weakReferences = new ConcurrentHashMap<>();
    private final Map<String, SoftReference<Object>> softReferences = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ResourceManager-Cleanup");
        t.setDaemon(true);
        return t;
    });
    private final List<ResourceCleanupStrategy> cleanupStrategies = new CopyOnWriteArrayList<>();
    private final ResourceStatistics statistics = new ResourceStatistics();
    private final ReentrantReadWriteLock resourceLock = new ReentrantReadWriteLock();
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    
    private ResourceManager() {
        // 启动定期清理任务
        startCleanupTask();
        
        // 添加默认清理策略
        addCleanupStrategy(new TimeBasedCleanupStrategy(5 * 60 * 1000)); // 5分钟
        addCleanupStrategy(new MemoryPressureCleanupStrategy(0.8)); // 80%内存使用率
        
        // 注册JVM关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "ResourceManager-Shutdown"));
    }
    
    /**
     * 获取单例实例
     */
    public static ResourceManager getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new ResourceManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 注册资源
     */
    public String registerResource(ResourceType type, Object resource, Runnable cleanupTask) {
        return registerResource(type, resource, cleanupTask, null);
    }
    
    /**
     * 注册资源（带元数据）
     */
    public String registerResource(ResourceType type, Object resource, Runnable cleanupTask, Map<String, Object> metadata) {
        if (isShutdown.get()) {
            throw new IllegalStateException("ResourceManager is shutdown");
        }
        
        String resourceId = generateResourceId(type);
        ResourceDescriptor descriptor = new ResourceDescriptor(resourceId, type, resource, cleanupTask);
        
        if (metadata != null) {
            metadata.forEach(descriptor::addMetadata);
        }
        
        resourceLock.writeLock().lock();
        try {
            resources.put(resourceId, descriptor);
            
            // 估算内存使用量
            long memoryUsage = estimateMemoryUsage(resource);
            statistics.recordResourceCreation(type, memoryUsage);
            
            OptimizedLogUtil.debug("Registered resource: {}", descriptor);
            
            // 发布资源注册事件（暂时禁用，因为需要Project实例）
            // EventBus eventBus = new EventBus(null);
            // eventBus.publish(new PluginEvents.ResourceRegisteredEvent(
            //     "ResourceManager", resourceId, type.name(), memoryUsage));
            
        } finally {
            resourceLock.writeLock().unlock();
        }
        
        return resourceId;
    }
    
    /**
     * 注册弱引用资源
     */
    public String registerWeakResource(ResourceType type, Object resource, Runnable cleanupTask) {
        String resourceId = registerResource(type, resource, cleanupTask);
        weakReferences.put(resourceId, new WeakReference<>(resource));
        return resourceId;
    }
    
    /**
     * 注册软引用资源
     */
    public String registerSoftResource(ResourceType type, Object resource, Runnable cleanupTask) {
        String resourceId = registerResource(type, resource, cleanupTask);
        softReferences.put(resourceId, new SoftReference<>(resource));
        return resourceId;
    }
    
    /**
     * 访问资源
     */
    public <T> T accessResource(String resourceId, Class<T> type) {
        resourceLock.readLock().lock();
        try {
            ResourceDescriptor descriptor = resources.get(resourceId);
            if (descriptor == null) {
                return null;
            }
            
            if (descriptor.getStatus() != ResourceStatus.ACTIVE) {
                return null;
            }
            
            descriptor.access();
            statistics.recordResourceAccess(resourceId);
            
            return type.isInstance(descriptor.getResource()) ? type.cast(descriptor.getResource()) : null;
            
        } finally {
            resourceLock.readLock().unlock();
        }
    }
    
    /**
     * 释放资源
     */
    public boolean disposeResource(String resourceId) {
        resourceLock.writeLock().lock();
        try {
            ResourceDescriptor descriptor = resources.get(resourceId);
            if (descriptor == null || descriptor.getStatus() == ResourceStatus.DISPOSED) {
                return false;
            }
            
            return disposeResource(descriptor);
            
        } finally {
            resourceLock.writeLock().unlock();
        }
    }
    
    /**
     * 释放资源（内部方法）
     */
    private static boolean disposeResource(ResourceDescriptor descriptor) {
        try {
            descriptor.markDisposed();
            
            // 执行清理任务
            Runnable cleanupTask = descriptor.getCleanupTask();
            if (cleanupTask != null) {
                cleanupTask.run();
            }
            
            // 估算释放的内存
            long memoryUsage = estimateMemoryUsage(descriptor.getResource());
            
            OptimizedLogUtil.debug("Disposed resource: {}", descriptor);
            
            return true;
            
        } catch (Exception e) {
            OptimizedLogUtil.error("Failed to dispose resource: " + descriptor.getResourceId(), e);
            return false;
        }
    }
    
    /**
     * 批量释放资源
     */
    public int disposeResources(Predicate<ResourceDescriptor> predicate) {
        resourceLock.writeLock().lock();
        try {
            int count = 0;
            Iterator<Map.Entry<String, ResourceDescriptor>> iterator = resources.entrySet().iterator();
            
            while (iterator.hasNext()) {
                Map.Entry<String, ResourceDescriptor> entry = iterator.next();
                ResourceDescriptor descriptor = entry.getValue();
                
                if (predicate.test(descriptor)) {
                    if (disposeResource(descriptor)) {
                        iterator.remove();
                        count++;
                    }
                }
            }
            
            OptimizedLogUtil.info("Disposed {} resources matching predicate", count);
            return count;
            
        } finally {
            resourceLock.writeLock().unlock();
        }
    }
    
    /**
     * 释放指定类型的所有资源
     */
    public int disposeResourcesByType(ResourceType type) {
        return disposeResources(descriptor -> descriptor.getType() == type);
    }
    
    /**
     * 释放过期的资源
     */
    public int disposeExpiredResources() {
        return disposeResources(descriptor -> {
            long age = System.currentTimeMillis() - descriptor.getLastAccessTime();
            return age > 30 * 60 * 1000; // 30分钟
        });
    }
    
    /**
     * 检测资源泄漏
     */
    public List<ResourceDescriptor> detectResourceLeaks() {
        resourceLock.readLock().lock();
        try {
            List<ResourceDescriptor> leakedResources = new ArrayList<>();
            
            for (ResourceDescriptor descriptor : resources.values()) {
                if (descriptor.getStatus() == ResourceStatus.ACTIVE) {
                    long age = System.currentTimeMillis() - descriptor.getCreationTime();
                    
                    // 超过1小时未访问的资源认为是泄漏
                    if (age > 60 * 60 * 1000) {
                        descriptor.markLeaked();
                        leakedResources.add(descriptor);
                        statistics.recordResourceLeak(descriptor.getType());
                    }
                }
            }
            
            if (!leakedResources.isEmpty()) {
                OptimizedLogUtil.warn("Detected {} resource leaks", leakedResources.size());
                // EventBus eventBus = new EventBus(null);
                // eventBus.publish(new PluginEvents.ResourceLeakDetectedEvent(
                //     "ResourceManager", leakedResources.size()));
            }
            
            return leakedResources;
            
        } finally {
            resourceLock.readLock().unlock();
        }
    }
    
    /**
     * 获取资源信息
     */
    public ResourceDescriptor getResourceInfo(String resourceId) {
        resourceLock.readLock().lock();
        try {
            return resources.get(resourceId);
        } finally {
            resourceLock.readLock().unlock();
        }
    }
    
    /**
     * 获取所有资源信息
     */
    public List<ResourceDescriptor> getAllResources() {
        resourceLock.readLock().lock();
        try {
            return new ArrayList<>(resources.values());
        } finally {
            resourceLock.readLock().unlock();
        }
    }
    
    /**
     * 获取指定类型的资源
     */
    public List<ResourceDescriptor> getResourcesByType(ResourceType type) {
        resourceLock.readLock().lock();
        try {
            return resources.values().stream()
                .filter(descriptor -> descriptor.getType() == type)
                .collect(Collectors.toList());
        } finally {
            resourceLock.readLock().unlock();
        }
    }
    
    /**
     * 获取统计信息
     */
    public ResourceStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 添加清理策略
     */
    public void addCleanupStrategy(ResourceCleanupStrategy strategy) {
        cleanupStrategies.add(strategy);
    }
    
    /**
     * 移除清理策略
     */
    public void removeCleanupStrategy(ResourceCleanupStrategy strategy) {
        cleanupStrategies.remove(strategy);
    }
    
    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                performCleanup();
            } catch (Exception e) {
                OptimizedLogUtil.error("Error during resource cleanup", e);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * 执行清理
     */
    private void performCleanup() {
        resourceLock.writeLock().lock();
        try {
            List<ResourceDescriptor> resourcesToCleanup = new ArrayList<>();
            
            // 检查弱引用和软引用
            cleanupWeakReferences();
            cleanupSoftReferences();
            
            // 应用清理策略
            for (ResourceDescriptor descriptor : resources.values()) {
                if (descriptor.getStatus() == ResourceStatus.ACTIVE) {
                    for (ResourceCleanupStrategy strategy : cleanupStrategies) {
                        if (strategy.shouldCleanup(descriptor)) {
                            resourcesToCleanup.add(descriptor);
                            break;
                        }
                    }
                }
            }
            
            // 执行清理
            int cleanedCount = 0;
            for (ResourceDescriptor descriptor : resourcesToCleanup) {
                if (disposeResource(descriptor)) {
                    resources.remove(descriptor.getResourceId());
                    cleanedCount++;
                }
            }
            
            if (cleanedCount > 0) {
                OptimizedLogUtil.info("Cleaned up {} resources", cleanedCount);
                // EventBus eventBus = new EventBus(null);
                // eventBus.publish(new PluginEvents.ResourceCleanupEvent(
                //     "ResourceManager", cleanedCount));
            }
            
        } finally {
            resourceLock.writeLock().unlock();
        }
    }
    
    /**
     * 清理弱引用
     */
    private void cleanupWeakReferences() {
        Iterator<Map.Entry<String, WeakReference<Object>>> iterator = weakReferences.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, WeakReference<Object>> entry = iterator.next();
            WeakReference<Object> ref = entry.getValue();
            
            if (ref.get() == null) {
                disposeResource(entry.getKey());
                iterator.remove();
            }
        }
    }
    
    /**
     * 清理软引用
     */
    private void cleanupSoftReferences() {
        Iterator<Map.Entry<String, SoftReference<Object>>> iterator = softReferences.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, SoftReference<Object>> entry = iterator.next();
            SoftReference<Object> ref = entry.getValue();
            
            if (ref.get() == null) {
                disposeResource(entry.getKey());
                iterator.remove();
            }
        }
    }
    
    /**
     * 生成资源ID
     */
    private String generateResourceId(ResourceType type) {
        return type.name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 估算内存使用量
     */
    private static long estimateMemoryUsage(Object resource) {
        if (resource == null) {
            return 0;
        }
        
        // 简单的内存估算，实际应用中可以使用更精确的方法
        if (resource instanceof String) {
            return ((String) resource).length() * 2L;
        } else if (resource instanceof byte[]) {
            return ((byte[]) resource).length;
        } else if (resource instanceof Collection) {
            return ((Collection<?>) resource).size() * 64L; // 假设每个元素64字节
        } else if (resource instanceof Map) {
            return ((Map<?, ?>) resource).size() * 128L; // 假设每个键值对128字节
        } else {
            return 1024L; // 默认1KB
        }
    }
    
    /**
     * 强制垃圾回收
     */
    public void forceGarbageCollection() {
        System.gc();
        System.runFinalization();
        
        // 等待垃圾回收完成
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        OptimizedLogUtil.info("Forced garbage collection completed");
    }
    
    /**
     * 关闭资源管理器
     */
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            OptimizedLogUtil.info("Shutting down ResourceManager");
            
            // 释放所有资源
            resourceLock.writeLock().lock();
            try {
                int disposedCount = 0;
                for (ResourceDescriptor descriptor : resources.values()) {
                    if (disposeResource(descriptor)) {
                        disposedCount++;
                    }
                }
                resources.clear();
                weakReferences.clear();
                softReferences.clear();
                
                OptimizedLogUtil.info("Disposed {} resources during shutdown", disposedCount);
                
            } finally {
                resourceLock.writeLock().unlock();
            }
            
            // 关闭清理执行器
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            OptimizedLogUtil.info("ResourceManager shutdown completed");
        }
    }
    
    /**
     * 检查是否已关闭
     */
    public boolean isShutdown() {
        return isShutdown.get();
    }
}
