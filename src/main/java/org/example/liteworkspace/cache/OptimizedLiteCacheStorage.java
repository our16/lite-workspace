package org.example.liteworkspace.cache;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.DatasourceConfig;
import org.example.liteworkspace.config.ConfigurationManager;
import org.example.liteworkspace.datasource.SqlSessionConfig;
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.exception.ConfigurationException;
import org.example.liteworkspace.util.LogUtil;
import org.example.liteworkspace.util.MybatisBeanDto;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.zip.*;

/**
 * 优化的缓存存储系统
 * 
 * 主要优化：
 * 1. 基于文件修改时间的智能缓存失效
 * 2. 缓存压缩和序列化优化
 * 3. 分层缓存（内存+磁盘）
 * 4. 版本控制和一致性保证
 * 5. 缓存统计和监控
 */
public class OptimizedLiteCacheStorage {
    
    /**
     * 缓存条目元数据
     */
    private static class CacheEntryMetadata {
        private final long creationTime;
        private final long lastAccessTime;
        private final long lastModifiedTime;
        private final String checksum;
        private final long size;
        private final int version;
        
        public CacheEntryMetadata(long creationTime, long lastModifiedTime, String checksum, long size, int version) {
            this.creationTime = creationTime;
            this.lastAccessTime = System.currentTimeMillis();
            this.lastModifiedTime = lastModifiedTime;
            this.checksum = checksum;
            this.size = size;
            this.version = version;
        }
        
        public boolean isValid(long currentFileTime, String currentChecksum) {
            return lastModifiedTime == currentFileTime && 
                   Objects.equals(checksum, currentChecksum);
        }
        
        public void updateAccessTime() {
            // 更新访问时间
        }
        
        // Getters
        public long getCreationTime() { return creationTime; }
        public long getLastAccessTime() { return lastAccessTime; }
        public long getLastModifiedTime() { return lastModifiedTime; }
        public String getChecksum() { return checksum; }
        public long getSize() { return size; }
        public int getVersion() { return version; }
    }
    
    /**
     * 内存缓存条目
     */
    private static class MemoryCacheEntry<T> {
        private final T data;
        private final CacheEntryMetadata metadata;
        private volatile long lastAccessTime;
        
        public MemoryCacheEntry(T data, CacheEntryMetadata metadata) {
            this.data = data;
            this.metadata = metadata;
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        public T getData() {
            lastAccessTime = System.currentTimeMillis();
            return data;
        }
        
        public CacheEntryMetadata getMetadata() {
            return metadata;
        }
        
        public long getLastAccessTime() {
            return lastAccessTime;
        }
    }
    
    /**
     * 缓存统计信息
     */
    public static class CacheStatistics {
        private volatile long memoryHits = 0;
        private volatile long diskHits = 0;
        private volatile long misses = 0;
        private volatile long evictions = 0;
        private volatile long compressions = 0;
        private volatile long decompressions = 0;
        private volatile long totalSize = 0;
        private volatile int memoryEntries = 0;
        
        public void recordMemoryHit() { memoryHits++; }
        public void recordDiskHit() { diskHits++; }
        public void recordMiss() { misses++; }
        public void recordEviction() { evictions++; }
        public void recordCompression() { compressions++; }
        public void recordDecompression() { decompressions++; }
        public void updateTotalSize(long size) { totalSize = size; }
        public void updateMemoryEntries(int count) { memoryEntries = count; }
        
        public double getHitRate() {
            long total = memoryHits + diskHits + misses;
            return total == 0 ? 0.0 : (double)(memoryHits + diskHits) / total;
        }
        
        public double getMemoryHitRate() {
            long total = memoryHits + diskHits + misses;
            return total == 0 ? 0.0 : (double)memoryHits / total;
        }
        
        @Override
        public String toString() {
            return String.format(
                "CacheStats{memoryHits=%d, diskHits=%d, misses=%d, hitRate=%.2f%%, " +
                "memoryHitRate=%.2f%%, evictions=%d, totalSize=%dKB, memoryEntries=%d}",
                memoryHits, diskHits, misses, getHitRate() * 100, getMemoryHitRate() * 100,
                evictions, totalSize / 1024, memoryEntries
            );
        }
    }
    
    // 配置常量
    private static final int CURRENT_CACHE_VERSION = 2;
    private static final long MEMORY_CACHE_EXPIRE_TIME = 10 * 60 * 1000; // 10分钟
    private static final long DISK_CACHE_EXPIRE_TIME = 24 * 60 * 60 * 1000; // 24小时
    private static final int MAX_MEMORY_ENTRIES = 1000;
    private static final long MAX_MEMORY_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int COMPRESSION_THRESHOLD = 1024; // 1KB以上压缩
    
    // 核心组件
    private final Path cacheDir;
    private final Project project;
    private final ConfigurationManager configManager;
    private final Map<String, MemoryCacheEntry<?>> memoryCache;
    private final Map<String, CacheEntryMetadata> metadataCache;
    private final ReadWriteLock cacheLock;
    private final CacheStatistics statistics;
    private final Timer cleanupTimer;
    
    public OptimizedLiteCacheStorage(Project project) {
        this.project = project;
        this.configManager = ConfigurationManager.getInstance(project);
        this.memoryCache = new ConcurrentHashMap<>();
        this.metadataCache = new ConcurrentHashMap<>();
        this.cacheLock = new ReentrantReadWriteLock();
        this.statistics = new CacheStatistics();
        
        // 创建缓存目录
        String projectId = DigestUtils.md5Hex(project.getBasePath());
        this.cacheDir = Paths.get(System.getProperty("user.home"), ".liteworkspace_cache", projectId);
        try {
            Files.createDirectories(cacheDir);
            createCacheStructure();
        } catch (IOException e) {
            throw new RuntimeException("创建缓存目录失败", e);
        }
        
        // 启动清理定时器
        this.cleanupTimer = new Timer("CacheCleanup", true);
        startCleanupTask();
        
        LogUtil.info("OptimizedLiteCacheStorage 初始化完成，缓存目录: " + cacheDir);
    }
    
    /**
     * 创建缓存目录结构
     */
    private void createCacheStructure() throws IOException {
        Files.createDirectories(cacheDir.resolve("metadata"));
        Files.createDirectories(cacheDir.resolve("data"));
        Files.createDirectories(cacheDir.resolve("compressed"));
    }
    
    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        cleanupTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    cleanupExpiredEntries();
                    optimizeMemoryCache();
                } catch (Exception e) {
                    LogUtil.error("缓存清理任务异常", e);
                }
            }
        }, 5 * 60 * 1000, 5 * 60 * 1000); // 每5分钟执行一次
    }
    
    /**
     * 清理过期条目
     */
    private void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        
        cacheLock.writeLock().lock();
        try {
            // 清理内存缓存
            memoryCache.entrySet().removeIf(entry -> {
                MemoryCacheEntry<?> memoryEntry = entry.getValue();
                if (currentTime - memoryEntry.getLastAccessTime() > MEMORY_CACHE_EXPIRE_TIME) {
                    statistics.recordEviction();
                    return true;
                }
                return false;
            });
            
            // 清理磁盘缓存
            try {
                Files.walk(cacheDir.resolve("data"))
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            long lastModified = Files.getLastModifiedTime(path).toMillis();
                            if (currentTime - lastModified > DISK_CACHE_EXPIRE_TIME) {
                                Files.delete(path);
                                statistics.recordEviction();
                            }
                        } catch (IOException e) {
                            LogUtil.warn("删除过期缓存文件失败: " + path, e);
                        }
                    });
            } catch (IOException e) {
                LogUtil.warn("清理磁盘缓存失败", e);
            }
            
            updateStatistics();
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * 优化内存缓存
     */
    private void optimizeMemoryCache() {
        if (memoryCache.size() <= MAX_MEMORY_ENTRIES) {
            return;
        }
        
        cacheLock.writeLock().lock();
        try {
            // 按访问时间排序，移除最久未访问的条目
            List<Map.Entry<String, MemoryCacheEntry<?>>> entries = new ArrayList<>(memoryCache.entrySet());
            entries.sort(Comparator.comparingLong(e -> e.getValue().getLastAccessTime()));
            
            int toRemove = memoryCache.size() - MAX_MEMORY_ENTRIES;
            for (int i = 0; i < toRemove && i < entries.size(); i++) {
                memoryCache.remove(entries.get(i).getKey());
                statistics.recordEviction();
            }
            
            updateStatistics();
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * 更新统计信息
     */
    private void updateStatistics() {
        statistics.updateMemoryEntries(memoryCache.size());
        
        long totalSize = memoryCache.values().stream()
            .mapToLong(entry -> {
                try {
                    return estimateSize(entry.getData());
                } catch (Exception e) {
                    return 0;
                }
            })
            .sum();
        statistics.updateTotalSize(totalSize);
    }
    
    /**
     * 估算对象大小
     */
    private long estimateSize(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof String) return ((String) obj).length() * 2;
        if (obj instanceof Collection) return ((Collection<?>) obj).size() * 64; // 估算
        if (obj instanceof Map) return ((Map<?, ?>) obj).size() * 128; // 估算
        return 256; // 默认估算
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String type, String key) {
        return type + ":" + DigestUtils.md5Hex(key);
    }
    
    /**
     * 计算文件校验和
     */
    private String calculateChecksum(VirtualFile file) {
        try {
            if (file == null || !file.exists()) {
                return "null";
            }
            
            String content = file.getName() + file.getTimeStamp() + file.getLength();
            return DigestUtils.md5Hex(content);
        } catch (Exception e) {
            LogUtil.warn("计算文件校验和失败", e);
            return "error";
        }
    }
    
    /**
     * 压缩数据
     */
    private byte[] compress(byte[] data) throws IOException {
        if (data.length < COMPRESSION_THRESHOLD) {
            return data;
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
        }
        
        byte[] compressed = baos.toByteArray();
        statistics.recordCompression();
        
        LogUtil.debug(String.format("数据压缩: %d -> %d bytes (%.2f%%)", 
            data.length, compressed.length, (1.0 - (double)compressed.length / data.length) * 100));
        
        return compressed;
    }
    
    /**
     * 解压数据
     */
    private byte[] decompress(byte[] compressed) throws IOException {
        // 检查是否是压缩数据
        if (compressed.length < 2 || 
            (compressed[0] & 0xff) != 0x1f || 
            (compressed[1] & 0xff) != 0x8b) {
            return compressed; // 不是压缩数据
        }
        
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (GZIPInputStream gzis = new GZIPInputStream(bais)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }
        
        statistics.recordDecompression();
        return baos.toByteArray();
    }
    
    /**
     * 保存数据到缓存
     */
    public <T> void saveToCache(String type, String key, T data, VirtualFile sourceFile) {
        String cacheKey = generateCacheKey(type, key);
        long currentTime = System.currentTimeMillis();
        String checksum = calculateChecksum(sourceFile);
        long fileTime = sourceFile != null ? sourceFile.getTimeStamp() : currentTime;
        
        CacheEntryMetadata metadata = new CacheEntryMetadata(
            currentTime, fileTime, checksum, estimateSize(data), CURRENT_CACHE_VERSION
        );
        
        cacheLock.writeLock().lock();
        try {
            // 保存到内存缓存
            memoryCache.put(cacheKey, new MemoryCacheEntry<>(data, metadata));
            
            // 异步保存到磁盘
            saveToDiskAsync(cacheKey, data, metadata);
            
            updateStatistics();
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * 异步保存到磁盘
     */
    private <T> void saveToDiskAsync(String cacheKey, T data, CacheEntryMetadata metadata) {
        CompletableFuture.runAsync(() -> {
            try {
                saveToDisk(cacheKey, data, metadata);
            } catch (Exception e) {
                LogUtil.error("异步保存缓存到磁盘失败: " + cacheKey, e);
            }
        });
    }
    
    /**
     * 保存到磁盘
     */
    private <T> void saveToDisk(String cacheKey, T data, CacheEntryMetadata metadata) throws IOException {
        // 序列化数据
        byte[] serializedData = serialize(data);
        
        // 压缩数据
        byte[] compressedData = compress(serializedData);
        
        // 保存数据文件
        Path dataFile = cacheDir.resolve("data").resolve(cacheKey + ".dat");
        Files.write(dataFile, compressedData);
        
        // 保存元数据
        Path metadataFile = cacheDir.resolve("metadata").resolve(cacheKey + ".meta");
        saveMetadata(metadataFile, metadata);
    }
    
    /**
     * 序列化对象
     */
    private <T> byte[] serialize(T obj) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            return baos.toByteArray();
        }
    }
    
    /**
     * 反序列化对象
     */
    @SuppressWarnings("unchecked")
    private <T> T deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        }
    }
    
    /**
     * 保存元数据
     */
    private void saveMetadata(Path metadataFile, CacheEntryMetadata metadata) throws IOException {
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put("creationTime", metadata.getCreationTime());
        metaMap.put("lastModifiedTime", metadata.getLastModifiedTime());
        metaMap.put("checksum", metadata.getChecksum());
        metaMap.put("size", metadata.getSize());
        metaMap.put("version", metadata.getVersion());
        
        String json = GsonProvider.gson.toJson(metaMap);
        Files.writeString(metadataFile, json);
    }
    
    /**
     * 加载元数据
     */
    private CacheEntryMetadata loadMetadata(Path metadataFile) throws IOException {
        if (!Files.exists(metadataFile)) {
            return null;
        }
        
        String json = Files.readString(metadataFile);
        Map<String, Object> metaMap = GsonProvider.gson.fromJson(json, Map.class);
        
        return new CacheEntryMetadata(
            ((Number) metaMap.get("creationTime")).longValue(),
            ((Number) metaMap.get("lastModifiedTime")).longValue(),
            (String) metaMap.get("checksum"),
            ((Number) metaMap.get("size")).longValue(),
            ((Number) metaMap.get("version")).intValue()
        );
    }
    
    /**
     * 从缓存加载数据
     */
    public <T> T loadFromCache(String type, String key, Class<T> clazz, VirtualFile sourceFile) {
        String cacheKey = generateCacheKey(type, key);
        String currentChecksum = calculateChecksum(sourceFile);
        long currentFileTime = sourceFile != null ? sourceFile.getTimeStamp() : System.currentTimeMillis();
        
        // 首先检查内存缓存
        cacheLock.readLock().lock();
        try {
            MemoryCacheEntry<?> memoryEntry = memoryCache.get(cacheKey);
            if (memoryEntry != null) {
                CacheEntryMetadata metadata = memoryEntry.getMetadata();
                if (metadata.isValid(currentFileTime, currentChecksum)) {
                    statistics.recordMemoryHit();
                    return clazz.cast(memoryEntry.getData());
                } else {
                    // 缓存失效，从内存中移除
                    memoryCache.remove(cacheKey);
                }
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        
        // 检查磁盘缓存
        return loadFromDisk(cacheKey, clazz, currentFileTime, currentChecksum);
    }
    
    /**
     * 从磁盘加载
     */
    private <T> T loadFromDisk(String cacheKey, Class<T> clazz, long currentFileTime, String currentChecksum) {
        try {
            Path dataFile = cacheDir.resolve("data").resolve(cacheKey + ".dat");
            Path metadataFile = cacheDir.resolve("metadata").resolve(cacheKey + ".meta");
            
            if (!Files.exists(dataFile) || !Files.exists(metadataFile)) {
                statistics.recordMiss();
                return null;
            }
            
            // 加载元数据
            CacheEntryMetadata metadata = loadMetadata(metadataFile);
            if (metadata == null || !metadata.isValid(currentFileTime, currentChecksum)) {
                statistics.recordMiss();
                // 删除无效的缓存文件
                try {
                    Files.deleteIfExists(dataFile);
                    Files.deleteIfExists(metadataFile);
                } catch (IOException e) {
                    LogUtil.warn("删除无效缓存文件失败", e);
                }
                return null;
            }
            
            // 加载数据
            byte[] compressedData = Files.readAllBytes(dataFile);
            byte[] serializedData = decompress(compressedData);
            T data = deserialize(serializedData);
            
            // 加载到内存缓存
            cacheLock.writeLock().lock();
            try {
                memoryCache.put(cacheKey, new MemoryCacheEntry<>(data, metadata));
                updateStatistics();
            } finally {
                cacheLock.writeLock().unlock();
            }
            
            statistics.recordDiskHit();
            return data;
            
        } catch (Exception e) {
            LogUtil.error("从磁盘加载缓存失败: " + cacheKey, e);
            statistics.recordMiss();
            return null;
        }
    }
    
    /**
     * 保存 Spring @Configuration 类
     */
    public void saveConfigurationClasses(Map<String, ClassSignatureDTO> configClasses) {
        // 为每个类找到对应的源文件
        Map<String, VirtualFile> classToFiles = configClasses.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> findSourceFile(entry.getValue().getQualifiedName())
            ));
        
        for (Map.Entry<String, ClassSignatureDTO> entry : configClasses.entrySet()) {
            String className = entry.getKey();
            ClassSignatureDTO dto = entry.getValue();
            VirtualFile sourceFile = classToFiles.get(className);
            saveToCache("config_class", className, dto, sourceFile);
        }
    }
    
    /**
     * 加载 Spring @Configuration 类
     */
    public Map<String, ClassSignatureDTO> loadConfigurationClasses() {
        Map<String, ClassSignatureDTO> result = new HashMap<>();
        
        try {
            Files.walk(cacheDir.resolve("metadata"))
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith("config_class:"))
                .forEach(path -> {
                    String cacheKey = path.getFileName().toString().replace(".meta", "");
                    String className = extractKeyFromCacheKey(cacheKey);
                    VirtualFile sourceFile = findSourceFile(className);
                    
                    ClassSignatureDTO dto = loadFromCache("config_class", className, ClassSignatureDTO.class, sourceFile);
                    if (dto != null) {
                        result.put(className, dto);
                    }
                });
        } catch (IOException e) {
            LogUtil.warn("加载配置类缓存失败", e);
        }
        
        return result;
    }
    
    /**
     * 查找源文件
     */
    private VirtualFile findSourceFile(String className) {
        // 简化实现，实际应该根据类名查找对应的源文件
        return null;
    }
    
    /**
     * 从缓存键提取原始键
     */
    private String extractKeyFromCacheKey(String cacheKey) {
        int colonIndex = cacheKey.indexOf(':');
        return colonIndex > 0 ? cacheKey.substring(colonIndex + 1) : cacheKey;
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 清空所有缓存
     */
    public void clearAllCache() {
        cacheLock.writeLock().lock();
        try {
            memoryCache.clear();
            metadataCache.clear();
            
            // 删除磁盘缓存
            try {
                Files.walk(cacheDir)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            LogUtil.warn("删除缓存文件失败: " + path, e);
                        }
                    });
            } catch (IOException e) {
                LogUtil.warn("清空磁盘缓存失败", e);
            }
            
            updateStatistics();
            LogUtil.info("已清空所有缓存");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * 关闭缓存存储
     */
    public void shutdown() {
        cleanupTimer.cancel();
        
        cacheLock.writeLock().lock();
        try {
            // 保存内存缓存到磁盘
            for (Map.Entry<String, MemoryCacheEntry<?>> entry : memoryCache.entrySet()) {
                try {
                    saveToDisk(entry.getKey(), entry.getValue().getData(), entry.getValue().getMetadata());
                } catch (Exception e) {
                    LogUtil.warn("保存内存缓存到磁盘失败: " + entry.getKey(), e);
                }
            }
            
            memoryCache.clear();
            metadataCache.clear();
        } finally {
            cacheLock.writeLock().unlock();
        }
        
        LogUtil.info("OptimizedLiteCacheStorage 已关闭");
    }
}
