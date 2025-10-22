package org.example.liteworkspace.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import org.example.liteworkspace.cache.OptimizedLiteCacheStorage;
import org.example.liteworkspace.config.ConfigurationManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 优化的搜索范围管理器
 * 
 * 主要优化：
 * 1. 智能搜索范围计算 - 基于模块依赖和包结构
 * 2. 基于模块依赖关系限制搜索范围
 * 3. 搜索结果缓存 - 避免重复搜索
 * 4. 搜索性能监控和统计
 */
public class OptimizedSearchScopeManager {
    
    /**
     * 搜索范围类型
     */
    public enum SearchScopeType {
        PROJECT_WIDE,      // 项目范围
        MODULE_DEPENDENT,  // 模块依赖范围
        PACKAGE_SPECIFIC,  // 特定包范围
        SOURCE_ONLY,       // 仅源码范围
        OPTIMIZED          // 智能优化范围
    }
    
    /**
     * 搜索统计信息
     */
    public static class SearchStatistics {
        private volatile long totalSearches = 0;
        private volatile long cacheHits = 0;
        private volatile long scopeOptimizations = 0;
        private volatile long averageSearchTime = 0;
        private volatile long totalSearchTime = 0;
        private final Map<String, Long> searchTypeCounts = new ConcurrentHashMap<>();
        private final Map<String, Long> moduleSearchCounts = new ConcurrentHashMap<>();
        
        public void recordSearch(String searchType, String moduleName, long searchTime) {
            totalSearches++;
            totalSearchTime += searchTime;
            averageSearchTime = totalSearchTime / totalSearches;
            
            searchTypeCounts.merge(searchType, 1L, Long::sum);
            moduleSearchCounts.merge(moduleName, 1L, Long::sum);
        }
        
        public void recordCacheHit() { cacheHits++; }
        public void recordScopeOptimization() { scopeOptimizations++; }
        
        public double getCacheHitRate() {
            return totalSearches == 0 ? 0.0 : (double) cacheHits / totalSearches;
        }
        
        public double getOptimizationRate() {
            return totalSearches == 0 ? 0.0 : (double) scopeOptimizations / totalSearches;
        }
        
        @Override
        public String toString() {
            return String.format(
                "SearchStats{total=%d, cacheHitRate=%.2f%%, optimizationRate=%.2f%%, " +
                "avgTime=%dms, types=%s, modules=%s}",
                totalSearches, getCacheHitRate() * 100, getOptimizationRate() * 100,
                averageSearchTime, searchTypeCounts, moduleSearchCounts
            );
        }
    }
    
    /**
     * 搜索范围缓存条目
     */
    private static class ScopeCacheEntry {
        private final GlobalSearchScope scope;
        private final long creationTime;
        private final String checksum;
        private volatile long lastAccessTime;
        
        public ScopeCacheEntry(GlobalSearchScope scope, String checksum) {
            this.scope = scope;
            this.checksum = checksum;
            this.creationTime = System.currentTimeMillis();
            this.lastAccessTime = creationTime;
        }
        
        public GlobalSearchScope getScope() {
            lastAccessTime = System.currentTimeMillis();
            return scope;
        }
        
        public boolean isValid(String currentChecksum) {
            return Objects.equals(checksum, currentChecksum);
        }
        
        public long getLastAccessTime() { return lastAccessTime; }
    }
    
    // 配置常量
    private static final long SCOPE_CACHE_EXPIRE_TIME = 30 * 60 * 1000; // 30分钟
    private static final int MAX_SCOPE_CACHE_SIZE = 100;
    
    // 核心组件
    private final Project project;
    private final ConfigurationManager configManager;
    private final OptimizedLiteCacheStorage cacheStorage;
    private final Map<String, ScopeCacheEntry> scopeCache;
    private final ReadWriteLock cacheLock;
    private final SearchStatistics statistics;
    
    // 模块依赖缓存
    private final Map<String, Set<String>> moduleDependencyCache;
    private final Map<String, GlobalSearchScope> moduleScopeCache;
    
    public OptimizedSearchScopeManager(Project project) {
        this.project = project;
        this.configManager = ConfigurationManager.getInstance(project);
        this.cacheStorage = new OptimizedLiteCacheStorage(project);
        this.scopeCache = new ConcurrentHashMap<>();
        this.cacheLock = new ReentrantReadWriteLock();
        this.statistics = new SearchStatistics();
        this.moduleDependencyCache = new ConcurrentHashMap<>();
        this.moduleScopeCache = new ConcurrentHashMap<>();
        
        // 初始化模块依赖信息
        initializeModuleDependencies();
        
        LogUtil.info("OptimizedSearchScopeManager 初始化完成");
    }
    
    /**
     * 初始化模块依赖信息
     */
    private void initializeModuleDependencies() {
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        for (Module module : moduleManager.getModules()) {
            Set<String> dependencies = analyzeModuleDependencies(module);
            moduleDependencyCache.put(module.getName(), dependencies);
            
            // 为每个模块创建搜索范围
            GlobalSearchScope moduleScope = createModuleSearchScope(module);
            moduleScopeCache.put(module.getName(), moduleScope);
        }
        
        LogUtil.info("模块依赖分析完成，共分析 {} 个模块", moduleDependencyCache.size());
    }
    
    /**
     * 分析模块依赖关系
     */
    private Set<String> analyzeModuleDependencies(Module module) {
        Set<String> dependencies = new HashSet<>();
        
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        
        // 获取模块依赖
        for (Module dependentModule : rootManager.getDependencies()) {
            dependencies.add(dependentModule.getName());
        }
        
        // 获取库依赖
        for (OrderEntry orderEntry : rootManager.getOrderEntries()) {
            if (orderEntry instanceof LibraryOrderEntry) {
                LibraryOrderEntry libraryEntry = (LibraryOrderEntry) orderEntry;
                dependencies.add("LIB:" + libraryEntry.getLibraryName());
            }
        }
        
        return dependencies;
    }
    
    /**
     * 创建模块搜索范围
     */
    private GlobalSearchScope createModuleSearchScope(Module module) {
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        
        // 获取模块的源码根目录
        List<VirtualFile> sourceRoots = Arrays.asList(rootManager.getSourceRoots());
        
        if (sourceRoots.isEmpty()) {
            return GlobalSearchScope.EMPTY_SCOPE;
        }
        
        // 创建包含源码和依赖的搜索范围
        return GlobalSearchScope.union(new GlobalSearchScope[]{
            GlobalSearchScope.filesScope(project, sourceRoots),
            createDependencyScope(module)
        });
    }
    
    /**
     * 创建依赖搜索范围
     */
    private GlobalSearchScope createDependencyScope(Module module) {
        Set<String> dependencies = moduleDependencyCache.get(module.getName());
        if (dependencies == null || dependencies.isEmpty()) {
            return GlobalSearchScope.EMPTY_SCOPE;
        }
        
        List<GlobalSearchScope> dependencyScopes = new ArrayList<>();
        
        for (String dependency : dependencies) {
            if (dependency.startsWith("LIB:")) {
                // 库依赖范围
                String libraryName = dependency.substring(4);
                dependencyScopes.add(createLibraryScope(libraryName));
            } else {
                // 模块依赖范围
                Module depModule = ModuleManager.getInstance(project).findModuleByName(dependency);
                if (depModule != null) {
                    dependencyScopes.add(moduleScopeCache.get(dependency));
                }
            }
        }
        
        return dependencyScopes.isEmpty() ? 
               GlobalSearchScope.EMPTY_SCOPE : 
               GlobalSearchScope.union(dependencyScopes.toArray(new GlobalSearchScope[0]));
    }
    
    /**
     * 创建库搜索范围
     */
    private GlobalSearchScope createLibraryScope(String libraryName) {
        // 简化实现，实际应该根据库名创建精确的搜索范围
        return GlobalSearchScope.allScope(project);
    }
    
    /**
     * 创建智能优化的搜索范围
     */
    public GlobalSearchScope createOptimizedSearchScope(String packagePrefix, SearchScopeType scopeType) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 生成缓存键
            String cacheKey = generateScopeCacheKey(packagePrefix, scopeType);
            String checksum = calculateScopeChecksum(packagePrefix, scopeType);
            
            // 检查缓存
            GlobalSearchScope cachedScope = getCachedScope(cacheKey, checksum);
            if (cachedScope != null) {
                statistics.recordCacheHit();
                return cachedScope;
            }
            
            // 创建新的搜索范围
            GlobalSearchScope scope = createSearchScopeInternal(packagePrefix, scopeType);
            
            // 缓存搜索范围
            cacheScope(cacheKey, scope, checksum);
            
            statistics.recordScopeOptimization();
            return scope;
            
        } finally {
            long searchTime = System.currentTimeMillis() - startTime;
            statistics.recordSearch(scopeType.name(), "optimized", searchTime);
        }
    }
    
    /**
     * 内部搜索范围创建逻辑
     */
    private GlobalSearchScope createSearchScopeInternal(String packagePrefix, SearchScopeType scopeType) {
        switch (scopeType) {
            case PROJECT_WIDE:
                return GlobalSearchScope.projectScope(project);
                
            case MODULE_DEPENDENT:
                return createModuleDependentScope(packagePrefix);
                
            case PACKAGE_SPECIFIC:
                return createPackageSpecificScope(packagePrefix);
                
            case SOURCE_ONLY:
                return createSourceOnlyScope(packagePrefix);
                
            case OPTIMIZED:
            default:
                return createIntelligentOptimizedScope(packagePrefix);
        }
    }
    
    /**
     * 创建模块依赖范围
     */
    private GlobalSearchScope createModuleDependentScope(String packagePrefix) {
        // 找到包含指定包的模块
        List<Module> relevantModules = findModulesContainingPackage(packagePrefix);
        
        if (relevantModules.isEmpty()) {
            LogUtil.warn("未找到包含包 {} 的模块", packagePrefix);
            return GlobalSearchScope.EMPTY_SCOPE;
        }
        
        // 合并相关模块的搜索范围
        List<GlobalSearchScope> moduleScopes = relevantModules.stream()
            .map(module -> moduleScopeCache.get(module.getName()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        return moduleScopes.isEmpty() ? 
               GlobalSearchScope.EMPTY_SCOPE : 
               GlobalSearchScope.union(moduleScopes.toArray(new GlobalSearchScope[0]));
    }
    
    /**
     * 查找包含指定包的模块
     */
    private List<Module> findModulesContainingPackage(String packagePrefix) {
        List<Module> result = new ArrayList<>();
        
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            if (moduleContainsPackage(module, packagePrefix)) {
                result.add(module);
            }
        }
        
        return result;
    }
    
    /**
     * 检查模块是否包含指定包
     */
    private boolean moduleContainsPackage(Module module, String packagePrefix) {
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        
        for (VirtualFile sourceRoot : rootManager.getSourceRoots()) {
            String packagePath = packagePrefix.replace('.', '/');
            VirtualFile packageDir = sourceRoot.findFileByRelativePath(packagePath);
            
            if (packageDir != null && packageDir.exists()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 创建特定包范围
     */
    private GlobalSearchScope createPackageSpecificScope(String packagePrefix) {
        PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(packagePrefix);
        if (psiPackage == null) {
            LogUtil.warn("未找到包: {}", packagePrefix);
            return GlobalSearchScope.EMPTY_SCOPE;
        }
        
        return new PackageScope(psiPackage, true, true);
    }
    
    /**
     * 创建仅源码范围
     */
    private GlobalSearchScope createSourceOnlyScope(String packagePrefix) {
        List<Module> relevantModules = findModulesContainingPackage(packagePrefix);
        
        List<GlobalSearchScope> sourceScopes = new ArrayList<>();
        for (Module module : relevantModules) {
            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            List<VirtualFile> sourceRoots = Arrays.asList(rootManager.getSourceRoots());
            
            if (!sourceRoots.isEmpty()) {
                sourceScopes.add(GlobalSearchScope.filesScope(project, sourceRoots));
            }
        }
        
        return sourceScopes.isEmpty() ? 
               GlobalSearchScope.EMPTY_SCOPE : 
               GlobalSearchScope.union(sourceScopes.toArray(new GlobalSearchScope[0]));
    }
    
    /**
     * 创建智能优化范围
     */
    private GlobalSearchScope createIntelligentOptimizedScope(String packagePrefix) {
        // 1. 分析包的复杂度和使用频率
        PackageAnalysis analysis = analyzePackage(packagePrefix);
        
        // 2. 根据分析结果选择最优搜索策略
        if (analysis.isHighFrequencyPackage()) {
            // 高频包：使用模块依赖范围
            return createModuleDependentScope(packagePrefix);
        } else if (analysis.isSimplePackage()) {
            // 简单包：使用特定包范围
            return createPackageSpecificScope(packagePrefix);
        } else {
            // 复杂包：使用混合策略
            return createHybridScope(packagePrefix, analysis);
        }
    }
    
    /**
     * 包分析结果
     */
    private static class PackageAnalysis {
        private final boolean highFrequency;
        private final boolean simplePackage;
        private final int estimatedClassCount;
        private final Set<String> dependentModules;
        
        public PackageAnalysis(boolean highFrequency, boolean simplePackage, 
                             int estimatedClassCount, Set<String> dependentModules) {
            this.highFrequency = highFrequency;
            this.simplePackage = simplePackage;
            this.estimatedClassCount = estimatedClassCount;
            this.dependentModules = dependentModules;
        }
        
        public boolean isHighFrequencyPackage() { return highFrequency; }
        public boolean isSimplePackage() { return simplePackage; }
        public int getEstimatedClassCount() { return estimatedClassCount; }
        public Set<String> getDependentModules() { return dependentModules; }
    }
    
    /**
     * 分析包的特征
     */
    private PackageAnalysis analyzePackage(String packagePrefix) {
        // 简化实现，实际应该基于历史数据和静态分析
        boolean isHighFrequency = isHighFrequencyPackage(packagePrefix);
        boolean isSimple = isSimplePackage(packagePrefix);
        int estimatedCount = estimateClassCount(packagePrefix);
        Set<String> dependentModules = findModulesContainingPackage(packagePrefix).stream()
            .map(Module::getName)
            .collect(Collectors.toSet());
        
        return new PackageAnalysis(isHighFrequency, isSimple, estimatedCount, dependentModules);
    }
    
    /**
     * 判断是否为高频包
     */
    private boolean isHighFrequencyPackage(String packagePrefix) {
        // 常见的高频包前缀
        return packagePrefix.startsWith("org.springframework") ||
               packagePrefix.startsWith("java.") ||
               packagePrefix.startsWith("javax.") ||
               packagePrefix.startsWith("org.apache") ||
               packagePrefix.startsWith("com.google");
    }
    
    /**
     * 判断是否为简单包
     */
    private boolean isSimplePackage(String packagePrefix) {
        // 简单包的特征：包名较短，层级较少
        String[] parts = packagePrefix.split("\\.");
        return parts.length <= 3 && !packagePrefix.contains("internal") && !packagePrefix.contains("impl");
    }
    
    /**
     * 估算类数量
     */
    private int estimateClassCount(String packagePrefix) {
        // 简化估算，实际应该基于文件系统扫描
        return packagePrefix.length() < 20 ? 50 : 200;
    }
    
    /**
     * 创建混合搜索范围
     */
    private GlobalSearchScope createHybridScope(String packagePrefix, PackageAnalysis analysis) {
        List<GlobalSearchScope> scopes = new ArrayList<>();
        
        // 1. 包范围
        scopes.add(createPackageSpecificScope(packagePrefix));
        
        // 2. 相关模块范围
        if (!analysis.getDependentModules().isEmpty()) {
            scopes.add(createModuleDependentScope(packagePrefix));
        }
        
        // 3. 如果类数量较少，包含源码范围
        if (analysis.getEstimatedClassCount() < 100) {
            scopes.add(createSourceOnlyScope(packagePrefix));
        }
        
        return scopes.isEmpty() ? 
               GlobalSearchScope.EMPTY_SCOPE : 
               GlobalSearchScope.union(scopes.toArray(new GlobalSearchScope[0]));
    }
    
    /**
     * 在优化范围内查找实现类
     */
    public Collection<PsiClass> findImplementationsInOptimizedScope(PsiClass interfaceClass, 
                                                                   String packagePrefix, 
                                                                   SearchScopeType scopeType) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 创建优化的搜索范围
            GlobalSearchScope scope = createOptimizedSearchScope(packagePrefix, scopeType);
            
            // 检查结果缓存
            String resultCacheKey = generateResultCacheKey(interfaceClass, scope);
            Collection<PsiClass> cachedResult = getCachedSearchResult(resultCacheKey);
            if (cachedResult != null) {
                statistics.recordCacheHit();
                return cachedResult;
            }
            
            // 执行搜索
            Collection<PsiClass> implementations = ClassInheritorsSearch
                .search(interfaceClass, scope, true)
                .findAll();
            
            // 缓存搜索结果
            cacheSearchResult(resultCacheKey, implementations);
            
            LogUtil.info("在优化范围内找到 {} 个实现类，包: {}, 范围类型: {}", 
                implementations.size(), packagePrefix, scopeType);
            
            return implementations;
            
        } finally {
            long searchTime = System.currentTimeMillis() - startTime;
            statistics.recordSearch("implementations", packagePrefix, searchTime);
        }
    }
    
    /**
     * 生成范围缓存键
     */
    private String generateScopeCacheKey(String packagePrefix, SearchScopeType scopeType) {
        return "scope:" + packagePrefix + ":" + scopeType.name();
    }
    
    /**
     * 生成结果缓存键
     */
    private String generateResultCacheKey(PsiClass interfaceClass, GlobalSearchScope scope) {
        return "result:" + interfaceClass.getQualifiedName() + ":" + scope.hashCode();
    }
    
    /**
     * 计算范围校验和
     */
    private String calculateScopeChecksum(String packagePrefix, SearchScopeType scopeType) {
        return packagePrefix + ":" + scopeType.name() + ":" + System.currentTimeMillis();
    }
    
    /**
     * 获取缓存的范围
     */
    private GlobalSearchScope getCachedScope(String cacheKey, String checksum) {
        cacheLock.readLock().lock();
        try {
            ScopeCacheEntry entry = scopeCache.get(cacheKey);
            if (entry != null && entry.isValid(checksum)) {
                return entry.getScope();
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        return null;
    }
    
    /**
     * 缓存范围
     */
    private void cacheScope(String cacheKey, GlobalSearchScope scope, String checksum) {
        cacheLock.writeLock().lock();
        try {
            // 清理过期缓存
            cleanupExpiredScopeCache();
            
            // 添加新缓存
            scopeCache.put(cacheKey, new ScopeCacheEntry(scope, checksum));
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * 清理过期的范围缓存
     */
    private void cleanupExpiredScopeCache() {
        long currentTime = System.currentTimeMillis();
        
        scopeCache.entrySet().removeIf(entry -> {
            ScopeCacheEntry cacheEntry = entry.getValue();
            return currentTime - cacheEntry.getLastAccessTime() > SCOPE_CACHE_EXPIRE_TIME;
        });
        
        // 如果缓存过多，移除最久未访问的
        if (scopeCache.size() > MAX_SCOPE_CACHE_SIZE) {
            List<Map.Entry<String, ScopeCacheEntry>> entries = new ArrayList<>(scopeCache.entrySet());
            entries.sort(Comparator.comparingLong(e -> e.getValue().getLastAccessTime()));
            
            int toRemove = scopeCache.size() - MAX_SCOPE_CACHE_SIZE;
            for (int i = 0; i < toRemove && i < entries.size(); i++) {
                scopeCache.remove(entries.get(i).getKey());
            }
        }
    }
    
    /**
     * 获取缓存的搜索结果
     */
    @SuppressWarnings("unchecked")
    private Collection<PsiClass> getCachedSearchResult(String cacheKey) {
        return cacheStorage.loadFromCache("search_result", cacheKey, Collection.class, null);
    }
    
    /**
     * 缓存搜索结果
     */
    private void cacheSearchResult(String cacheKey, Collection<PsiClass> implementations) {
        cacheStorage.saveToCache("search_result", cacheKey, implementations, null);
    }
    
    /**
     * 获取搜索统计信息
     */
    public SearchStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 清空所有缓存
     */
    public void clearAllCaches() {
        cacheLock.writeLock().lock();
        try {
            scopeCache.clear();
            cacheStorage.clearAllCache();
            LogUtil.info("已清空所有搜索缓存");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * 关闭搜索范围管理器
     */
    public void shutdown() {
        clearAllCaches();
        cacheStorage.shutdown();
        LogUtil.info("OptimizedSearchScopeManager 已关闭");
    }
}
