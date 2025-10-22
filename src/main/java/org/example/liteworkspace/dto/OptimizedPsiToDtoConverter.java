package org.example.liteworkspace.dto;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.example.liteworkspace.util.LogUtil;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 优化的 PSI 对象到 DTO 对象转换工具类
 * 
 * 主要优化：
 * 1. 智能缓存机制 - 避免重复转换
 * 2. 弱引用缓存 - 防止内存泄漏
 * 3. 批量转换优化 - 提升大量转换的性能
 * 4. 线程安全设计 - 支持并发访问
 * 5. 内存管理 - 自动清理失效的缓存项
 */
public class OptimizedPsiToDtoConverter {
    
    /**
     * 缓存条目，包含弱引用和版本信息
     */
    private static class CacheEntry {
        private final WeakReference<Object> psiReference;
        private final Object dto;
        private final long timestamp;
        private final int psiHashCode;
        
        public CacheEntry(Object psiObject, Object dto, long timestamp) {
            this.psiReference = new WeakReference<>(psiObject);
            this.dto = dto;
            this.timestamp = timestamp;
            this.psiHashCode = psiObject != null ? psiObject.hashCode() : 0;
        }
        
        public boolean isValid(Object psiObject) {
            if (psiObject == null) return false;
            
            Object cachedPsi = psiReference.get();
            return cachedPsi != null && 
                   cachedPsi == psiObject && 
                   cachedPsi.hashCode() == psiHashCode;
        }
        
        public Object getDto() { return dto; }
        public long getTimestamp() { return timestamp; }
    }
    
    // 缓存存储
    private final ConcurrentHashMap<String, CacheEntry> classCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry> methodCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry> fieldCache = new ConcurrentHashMap<>();
    
    // 引用队列，用于监控被垃圾回收的对象
    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    
    // 读写锁，保护缓存操作
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // 配置参数
    private static final long CACHE_EXPIRE_TIME = 5 * 60 * 1000; // 5分钟
    private static final int MAX_CACHE_SIZE = 10000;
    private static final int CLEANUP_THRESHOLD = 1000; // 清理阈值
    
    // 统计信息
    private static class CacheStatistics {
        volatile long hitCount = 0;
        volatile long missCount = 0;
        volatile long cleanupCount = 0;
        volatile long conversionCount = 0;
        
        double getHitRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) hitCount / total;
        }
    }
    
    private final CacheStatistics stats = new CacheStatistics();
    
    // 单例实例
    private static volatile OptimizedPsiToDtoConverter instance;
    
    public static OptimizedPsiToDtoConverter getInstance() {
        if (instance == null) {
            synchronized (OptimizedPsiToDtoConverter.class) {
                if (instance == null) {
                    instance = new OptimizedPsiToDtoConverter();
                }
            }
        }
        return instance;
    }
    
    private OptimizedPsiToDtoConverter() {
        // 启动后台清理线程
        startCleanupThread();
    }
    
    /**
     * 将 PsiClass 转换为 ClassSignatureDTO（优化版本）
     */
    public ClassSignatureDTO convertToClassSignature(PsiClass psiClass) {
        if (psiClass == null) {
            return null;
        }
        
        stats.conversionCount++;
        
        String cacheKey = generateClassKey(psiClass);
        
        // 尝试从缓存获取
        cacheLock.readLock().lock();
        try {
            CacheEntry entry = classCache.get(cacheKey);
            if (entry != null && entry.isValid(psiClass)) {
                stats.hitCount++;
                return (ClassSignatureDTO) entry.getDto();
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        
        stats.missCount++;
        
        // 缓存未命中，执行转换
        ClassSignatureDTO dto = performClassConversion(psiClass);
        
        // 存入缓存
        cacheLock.writeLock().lock();
        try {
            classCache.put(cacheKey, new CacheEntry(psiClass, dto, System.currentTimeMillis()));
            
            // 检查是否需要清理
            if (classCache.size() > MAX_CACHE_SIZE) {
                cleanupExpiredEntries();
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
        
        return dto;
    }
    
    /**
     * 将 PsiMethod 转换为 MethodSignatureDTO（优化版本）
     */
    public MethodSignatureDTO convertToMethodSignature(PsiMethod psiMethod) {
        if (psiMethod == null) {
            return null;
        }
        
        stats.conversionCount++;
        
        String cacheKey = generateMethodKey(psiMethod);
        
        // 尝试从缓存获取
        cacheLock.readLock().lock();
        try {
            CacheEntry entry = methodCache.get(cacheKey);
            if (entry != null && entry.isValid(psiMethod)) {
                stats.hitCount++;
                return (MethodSignatureDTO) entry.getDto();
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        
        stats.missCount++;
        
        // 缓存未命中，执行转换
        MethodSignatureDTO dto = performMethodConversion(psiMethod);
        
        // 存入缓存
        cacheLock.writeLock().lock();
        try {
            methodCache.put(cacheKey, new CacheEntry(psiMethod, dto, System.currentTimeMillis()));
            
            // 检查是否需要清理
            if (methodCache.size() > MAX_CACHE_SIZE) {
                cleanupExpiredEntries();
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
        
        return dto;
    }
    
    /**
     * 将 PsiField 转换为 FieldSignatureDTO（优化版本）
     */
    public FieldSignatureDTO convertToFieldSignature(PsiField psiField) {
        if (psiField == null) {
            return null;
        }
        
        stats.conversionCount++;
        
        String cacheKey = generateFieldKey(psiField);
        
        // 尝试从缓存获取
        cacheLock.readLock().lock();
        try {
            CacheEntry entry = fieldCache.get(cacheKey);
            if (entry != null && entry.isValid(psiField)) {
                stats.hitCount++;
                return (FieldSignatureDTO) entry.getDto();
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        
        stats.missCount++;
        
        // 缓存未命中，执行转换
        FieldSignatureDTO dto = performFieldConversion(psiField);
        
        // 存入缓存
        cacheLock.writeLock().lock();
        try {
            fieldCache.put(cacheKey, new CacheEntry(psiField, dto, System.currentTimeMillis()));
            
            // 检查是否需要清理
            if (fieldCache.size() > MAX_CACHE_SIZE) {
                cleanupExpiredEntries();
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
        
        return dto;
    }
    
    /**
     * 批量转换 PsiClass 列表（优化版本）
     */
    public List<ClassSignatureDTO> convertClassesToDto(List<PsiClass> psiClasses) {
        if (psiClasses == null || psiClasses.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 使用并行流处理大量转换
        return psiClasses.parallelStream()
                .map(this::convertToClassSignature)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 批量转换 PsiMethod 列表（优化版本）
     */
    public List<MethodSignatureDTO> convertMethodsToDto(List<PsiMethod> psiMethods) {
        if (psiMethods == null || psiMethods.isEmpty()) {
            return new ArrayList<>();
        }
        
        return psiMethods.parallelStream()
                .map(this::convertToMethodSignature)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 批量转换 PsiField 列表（优化版本）
     */
    public List<FieldSignatureDTO> convertFieldsToDto(List<PsiField> psiFields) {
        if (psiFields == null || psiFields.isEmpty()) {
            return new ArrayList<>();
        }
        
        return psiFields.parallelStream()
                .map(this::convertToFieldSignature)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 执行实际的类转换逻辑
     */
    private ClassSignatureDTO performClassConversion(PsiClass psiClass) {
        String qualifiedName = psiClass.getQualifiedName();
        String simpleName = psiClass.getName();
        
        // 获取实现的接口
        List<String> interfaceNames = Arrays.stream(psiClass.getImplementsListTypes())
                .map(PsiClassType::resolve)
                .filter(Objects::nonNull)
                .map(PsiClass::getQualifiedName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        // 获取父类
        String superClassName = null;
        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null && !"java.lang.Object".equals(superClass.getQualifiedName())) {
            superClassName = superClass.getQualifiedName();
        }
        
        // 获取包名
        String packageName = "";
        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile instanceof PsiJavaFile) {
            packageName = ((PsiJavaFile) containingFile).getPackageName();
            if (packageName == null) {
                packageName = "";
            }
        }

        return new ClassSignatureDTO(
                qualifiedName,
                simpleName,
                interfaceNames,
                superClassName,
                psiClass.isInterface(),
                psiClass.isAnnotationType(),
                psiClass.isEnum(),
                packageName
        );
    }
    
    /**
     * 执行实际的方法转换逻辑
     */
    private MethodSignatureDTO performMethodConversion(PsiMethod psiMethod) {
        PsiClass containingClass = psiMethod.getContainingClass();
        String classFqn = containingClass != null ? containingClass.getQualifiedName() : null;
        String methodName = psiMethod.getName();
        
        // 获取参数类型
        List<String> parameterTypes = Arrays.stream(psiMethod.getParameterList().getParameters())
                .map(PsiParameter::getType)
                .map(this::getTypeName)
                .collect(Collectors.toList());
        
        // 获取返回类型
        String returnType = getTypeName(psiMethod.getReturnType());

        return new MethodSignatureDTO(classFqn, methodName, parameterTypes, returnType);
    }
    
    /**
     * 执行实际字段转换逻辑
     */
    private FieldSignatureDTO performFieldConversion(PsiField psiField) {
        PsiClass containingClass = psiField.getContainingClass();
        String classFqn = containingClass != null ? containingClass.getQualifiedName() : null;
        String fieldName = psiField.getName();
        String fieldType = getTypeName(psiField.getType());
        
        boolean isStatic = psiField.hasModifierProperty(PsiModifier.STATIC);
        boolean isFinal = psiField.hasModifierProperty(PsiModifier.FINAL);
        boolean isTransient = psiField.hasModifierProperty(PsiModifier.TRANSIENT);
        boolean isVolatile = psiField.hasModifierProperty(PsiModifier.VOLATILE);

        return new FieldSignatureDTO(classFqn, fieldName, fieldType, isStatic, isFinal, isTransient, isVolatile);
    }
    
    /**
     * 获取 PsiType 的类型名称
     */
    private String getTypeName(PsiType type) {
        if (type == null) {
            return "void";
        }
        
        if (type instanceof PsiPrimitiveType) {
            return type.getPresentableText();
        }
        
        if (type instanceof PsiArrayType) {
            PsiArrayType arrayType = (PsiArrayType) type;
            return getTypeName(arrayType.getComponentType()) + "[]";
        }
        
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiClass psiClass = classType.resolve();
            if (psiClass != null) {
                return psiClass.getQualifiedName();
            }
        }
        
        return type.getPresentableText();
    }
    
    /**
     * 生成类缓存键
     */
    private String generateClassKey(PsiClass psiClass) {
        return "class:" + psiClass.getQualifiedName() + ":" + psiClass.getContainingFile().getModificationStamp();
    }
    
    /**
     * 生成方法缓存键
     */
    private String generateMethodKey(PsiMethod psiMethod) {
        PsiClass containingClass = psiMethod.getContainingClass();
        String classKey = containingClass != null ? containingClass.getQualifiedName() : "unknown";
        return "method:" + classKey + ":" + psiMethod.getName() + ":" + psiMethod.getParameterList().getParametersCount();
    }
    
    /**
     * 生成字段缓存键
     */
    private String generateFieldKey(PsiField psiField) {
        PsiClass containingClass = psiField.getContainingClass();
        String classKey = containingClass != null ? containingClass.getQualifiedName() : "unknown";
        return "field:" + classKey + ":" + psiField.getName();
    }
    
    /**
     * 清理过期的缓存条目
     */
    private void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        
        cacheLock.writeLock().lock();
        try {
            // 清理类缓存
            classCache.entrySet().removeIf(entry -> {
                CacheEntry cacheEntry = entry.getValue();
                return (currentTime - cacheEntry.getTimestamp()) > CACHE_EXPIRE_TIME || 
                       cacheEntry.psiReference.get() == null;
            });
            
            // 清理方法缓存
            methodCache.entrySet().removeIf(entry -> {
                CacheEntry cacheEntry = entry.getValue();
                return (currentTime - cacheEntry.getTimestamp()) > CACHE_EXPIRE_TIME || 
                       cacheEntry.psiReference.get() == null;
            });
            
            // 清理字段缓存
            fieldCache.entrySet().removeIf(entry -> {
                CacheEntry cacheEntry = entry.getValue();
                return (currentTime - cacheEntry.getTimestamp()) > CACHE_EXPIRE_TIME || 
                       cacheEntry.psiReference.get() == null;
            });
            
            stats.cleanupCount++;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * 启动后台清理线程
     */
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 每分钟清理一次
                    Thread.sleep(60000);
                    
                    // 处理引用队列中的被回收对象
                    Reference<?> ref;
                    while ((ref = referenceQueue.poll()) != null) {
                        // 清理相关的缓存条目
                        cleanupExpiredEntries();
                    }
                    
                    // 定期清理过期条目
                    if (classCache.size() + methodCache.size() + fieldCache.size() > CLEANUP_THRESHOLD) {
                        cleanupExpiredEntries();
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LogUtil.error("缓存清理线程异常", e);
                }
            }
        });
        
        cleanupThread.setDaemon(true);
        cleanupThread.setName("PsiToDtoConverter-Cleanup");
        cleanupThread.start();
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStatistics getStatistics() {
        CacheStatistics result = new CacheStatistics();
        result.hitCount = stats.hitCount;
        result.missCount = stats.missCount;
        result.cleanupCount = stats.cleanupCount;
        result.conversionCount = stats.conversionCount;
        return result;
    }
    
    /**
     * 清空所有缓存
     */
    public void clearAllCache() {
        cacheLock.writeLock().lock();
        try {
            classCache.clear();
            methodCache.clear();
            fieldCache.clear();
            LogUtil.info("已清空所有 PSI 转换缓存");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取缓存大小信息
     */
    public String getCacheInfo() {
        cacheLock.readLock().lock();
        try {
            return String.format(
                "CacheInfo{class=%d, method=%d, field=%d, hitRate=%.2f%%, conversions=%d}",
                classCache.size(),
                methodCache.size(),
                fieldCache.size(),
                stats.getHitRate() * 100,
                stats.conversionCount
            );
        } finally {
            cacheLock.readLock().unlock();
        }
    }
}
