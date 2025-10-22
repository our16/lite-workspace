package org.example.liteworkspace.bean.core.context;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.example.liteworkspace.bean.core.DatasourceConfig;
import org.example.liteworkspace.bean.core.enums.BuildToolType;
import org.example.liteworkspace.cache.CacheVersionChecker;
import org.example.liteworkspace.datasource.DataSourceConfigLoader;
import org.example.liteworkspace.datasource.SqlSessionConfig;
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.dto.MethodSignatureDTO;
import org.example.liteworkspace.dto.PsiToDtoConverter;
import org.example.liteworkspace.util.OptimizedLogUtil;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 线程安全的项目上下文
 * 
 * 主要改进：
 * 1. 不可变字段设计
 * 2. 延迟初始化和缓存
 * 3. 读写锁保护复合操作
 * 4. 线程安全的统计信息
 */
public class ThreadSafeLiteProjectContext {
    
    /**
     * 上下文统计信息
     */
    public static class ContextStatistics {
        private volatile long psiClassLookups = 0;
        private volatile long psiMethodLookups = 0;
        private volatile long datasourceRefreshes = 0;
        private volatile long configFileSearches = 0;
        private final Map<String, Long> operationCounts = new ConcurrentHashMap<>();
        
        public void recordPsiClassLookup() {
            psiClassLookups++;
        }
        
        public void recordPsiMethodLookup() {
            psiMethodLookups++;
        }
        
        public void recordDatasourceRefresh() {
            datasourceRefreshes++;
        }
        
        public void recordConfigFileSearch() {
            configFileSearches++;
        }
        
        public void recordOperation(String operation) {
            operationCounts.merge(operation, 1L, Long::sum);
        }
        
        // Getters
        public long getPsiClassLookups() { return psiClassLookups; }
        public long getPsiMethodLookups() { return psiMethodLookups; }
        public long getDatasourceRefreshes() { return datasourceRefreshes; }
        public long getConfigFileSearches() { return configFileSearches; }
        public Map<String, Long> getOperationCounts() { return new HashMap<>(operationCounts); }
        
        @Override
        public String toString() {
            return String.format(
                "ContextStats{psiClassLookups=%d, psiMethodLookups=%d, datasourceRefreshes=%d, configFileSearches=%d}",
                psiClassLookups, psiMethodLookups, datasourceRefreshes, configFileSearches
            );
        }
    }
    
    // 不可变的核心字段
    private final Project project;
    private final List<Module> modules;
    private final boolean multiModule;
    private final BuildToolType buildToolType;
    private final ClassSignatureDTO targetClassDto;
    private final MethodSignatureDTO targetMethodDto;
    private final CacheVersionChecker versionChecker;
    
    // 可变但线程安全的字段
    private volatile DatasourceConfig datasourceConfig;
    private volatile SpringContext springContext;
    private volatile MyBatisContext myBatisContext;
    private volatile List<SqlSessionConfig> sqlSessionConfigList;
    
    // 缓存和锁
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final ReadWriteLock contextLock = new ReentrantReadWriteLock();
    private final ContextStatistics statistics = new ContextStatistics();
    private volatile boolean isInitialized = false;
    private volatile boolean isShutdown = false;
    
    /**
     * 构造函数
     */
    public ThreadSafeLiteProjectContext(Project project, PsiClass targetClass, PsiMethod targetMethod, ProgressIndicator indicator) {
        OptimizedLogUtil.info("开始初始化 ThreadSafeLiteProjectContext, 项目名称: {}", project.getName());
        
        this.project = project;
        this.modules = Collections.unmodifiableList(Arrays.asList(ModuleManager.getInstance(project).getModules()));
        this.multiModule = modules.size() > 1;
        this.buildToolType = detectBuildToolType(project);
        this.targetClassDto = PsiToDtoConverter.convertToClassSignature(targetClass);
        this.targetMethodDto = PsiToDtoConverter.convertToMethodSignature(targetMethod);
        this.versionChecker = new CacheVersionChecker();
        
        OptimizedLogUtil.info("检测到模块数量: {}, 是否为多模块项目: {}, 构建工具: {}", 
            modules.size(), multiModule, buildToolType);
        
        // 初始化上下文
        initializeContext(indicator);
        this.isInitialized = true;
        
        OptimizedLogUtil.info("ThreadSafeLiteProjectContext 初始化完成");
    }
    
    /**
     * 初始化上下文
     */
    private void initializeContext(ProgressIndicator indicator) {
        contextLock.writeLock().lock();
        try {
            if (isShutdown) {
                return;
            }
            
            // 数据源配置
            this.datasourceConfig = refreshDatasourceConfigInternal();
            statistics.recordDatasourceRefresh();
            
            // Spring 上下文
            OptimizedLogUtil.info("开始初始化 Spring 上下文");
            this.springContext = new SpringContext(project, indicator);
            this.springContext.refresh(null);
            OptimizedLogUtil.info("Spring 上下文初始化完成");
            
            // MyBatis 配置
            OptimizedLogUtil.info("开始加载数据源配置");
            this.sqlSessionConfigList = DataSourceConfigLoader.load(project);
            OptimizedLogUtil.info("sqlSessionConfigList: {}", sqlSessionConfigList.size());
            
            // MyBatis 上下文
            OptimizedLogUtil.info("开始初始化 MyBatis 上下文");
            this.myBatisContext = new MyBatisContext(project, sqlSessionConfigList);
            this.myBatisContext.refresh();
            OptimizedLogUtil.info("MyBatis 上下文初始化完成");
            
        } finally {
            contextLock.writeLock().unlock();
        }
    }
    
    /**
     * 检测构建工具类型
     */
    private BuildToolType detectBuildToolType(Project project) {
        OptimizedLogUtil.debug("开始检测项目构建工具类型, 项目名称: {}", project.getName());
        
        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        if (roots == null || roots.length == 0) {
            OptimizedLogUtil.warn("未找到项目根目录, 返回未知构建工具类型");
            return BuildToolType.UNKNOWN;
        }
        
        for (VirtualFile root : roots) {
            if (root.findChild("pom.xml") != null) {
                OptimizedLogUtil.info("检测到 Maven 项目 (pom.xml)");
                return BuildToolType.MAVEN;
            }
            if (root.findChild("build.gradle") != null) {
                OptimizedLogUtil.info("检测到 Gradle 项目 (build.gradle)");
                return BuildToolType.GRADLE;
            }
            if (root.findChild("build.gradle.kts") != null) {
                OptimizedLogUtil.info("检测到 Gradle Kotlin 项目 (build.gradle.kts)");
                return BuildToolType.GRADLE;
            }
        }
        
        OptimizedLogUtil.warn("未检测到支持的构建工具, 返回未知类型");
        return BuildToolType.UNKNOWN;
    }
    
    /**
     * 刷新数据源配置
     */
    public DatasourceConfig refreshDatasourceConfig() {
        contextLock.writeLock().lock();
        try {
            if (isShutdown) {
                return datasourceConfig;
            }
            DatasourceConfig newConfig = refreshDatasourceConfigInternal();
            this.datasourceConfig = newConfig;
            statistics.recordDatasourceRefresh();
            return newConfig;
        } finally {
            contextLock.writeLock().unlock();
        }
    }
    
    /**
     * 内部刷新数据源配置方法
     */
    private DatasourceConfig refreshDatasourceConfigInternal() {
        OptimizedLogUtil.info("开始刷新数据源配置");
        
        String configFile = findTestDatasourceXml();
        if (configFile != null) {
            OptimizedLogUtil.info("找到测试数据源配置文件: {}, 使用导入配置", configFile);
            return DatasourceConfig.createImportedConfig(configFile);
        }
        
        OptimizedLogUtil.warn("未找到测试数据源配置文件, 使用默认配置");
        return DatasourceConfig.createDefaultConfig(
            "jdbc:mysql://localhost:3306/default_db",
            "root",
            "123456",
            "com.mysql.cj.jdbc.Driver"
        );
    }
    
    /**
     * 查找测试数据源配置文件
     */
    private String findTestDatasourceXml() {
        String cacheKey = "testDatasourceXml";
        
        // 检查缓存
        String cached = (String) cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        statistics.recordConfigFileSearch();
        String relativePath = "configs/datasource.xml";
        
        // 1. 遍历所有模块 Source Roots
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
            for (VirtualFile root : sourceRoots) {
                if (root.getPath().contains("test")) {
                    VirtualFile file = root.findFileByRelativePath(relativePath);
                    if (file != null && file.exists() && file.isValid()) {
                        OptimizedLogUtil.info("在模块 {} 的测试资源目录中找到配置文件: {}", 
                            module.getName(), file.getPath());
                        cache.put(cacheKey, relativePath);
                        return relativePath;
                    }
                }
            }
        }
        
        // 2. 全局索引搜索兜底
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName("datasource.xml", scope);
        
        for (VirtualFile file : files) {
            if (file.getPath().contains("configs")) {
                OptimizedLogUtil.info("通过全局索引找到配置文件: {}", file.getPath());
                cache.put(cacheKey, relativePath);
                return relativePath;
            }
        }
        
        // 3. 类加载器兜底
        try {
            URL resourceUrl = getClass().getClassLoader().getResource("configs/datasource.xml");
            if (resourceUrl != null) {
                OptimizedLogUtil.info("通过类加载器找到配置文件: {}", resourceUrl);
                cache.put(cacheKey, relativePath);
                return relativePath;
            }
        } catch (Exception ignored) {
            OptimizedLogUtil.debug("使用类加载器查找配置文件时发生异常", ignored);
        }
        
        cache.put(cacheKey, null);
        return null;
    }
    
    /**
     * 刷新 Spring 上下文
     */
    public void refreshSpringContext(ProgressIndicator indicator) {
        contextLock.writeLock().lock();
        try {
            if (isShutdown) {
                return;
            }
            
            OptimizedLogUtil.info("开始刷新 Spring 上下文");
            this.springContext = new SpringContext(project, indicator);
            this.springContext.refresh(null);
            OptimizedLogUtil.info("Spring 上下文刷新完成");
            
            statistics.recordOperation("springContextRefresh");
        } finally {
            contextLock.writeLock().unlock();
        }
    }
    
    /**
     * 刷新 MyBatis 上下文
     */
    public void refreshMyBatisContext() {
        contextLock.writeLock().lock();
        try {
            if (isShutdown) {
                return;
            }
            
            OptimizedLogUtil.info("开始刷新 MyBatis 上下文");
            this.sqlSessionConfigList = DataSourceConfigLoader.load(project);
            this.myBatisContext = new MyBatisContext(project, sqlSessionConfigList);
            this.myBatisContext.refresh();
            OptimizedLogUtil.info("MyBatis 上下文刷新完成");
            
            statistics.recordOperation("myBatisContextRefresh");
        } finally {
            contextLock.writeLock().unlock();
        }
    }
    
    // Getter 方法
    public Project getProject() {
        return project;
    }
    
    public List<Module> getModules() {
        return modules;
    }
    
    public boolean isMultiModule() {
        return multiModule;
    }
    
    public BuildToolType getBuildToolType() {
        return buildToolType;
    }
    
    public ClassSignatureDTO getTargetClassDto() {
        return targetClassDto;
    }
    
    public MethodSignatureDTO getTargetMethodDto() {
        return targetMethodDto;
    }
    
    public CacheVersionChecker getVersionChecker() {
        return versionChecker;
    }
    
    public DatasourceConfig getDatasourceConfig() {
        contextLock.readLock().lock();
        try {
            return datasourceConfig;
        } finally {
            contextLock.readLock().unlock();
        }
    }
    
    public SpringContext getSpringContext() {
        contextLock.readLock().lock();
        try {
            return springContext;
        } finally {
            contextLock.readLock().unlock();
        }
    }
    
    public MyBatisContext getMyBatisContext() {
        contextLock.readLock().lock();
        try {
            return myBatisContext;
        } finally {
            contextLock.readLock().unlock();
        }
    }
    
    public List<SqlSessionConfig> getSqlSessionConfigList() {
        contextLock.readLock().lock();
        try {
            return sqlSessionConfigList != null ? 
                Collections.unmodifiableList(sqlSessionConfigList) : 
                Collections.emptyList();
        } finally {
            contextLock.readLock().unlock();
        }
    }
    
    /**
     * 查找目标类
     */
    public PsiClass findTargetClass() {
        if (targetClassDto == null || targetClassDto.getQualifiedName() == null) {
            return null;
        }
        
        String cacheKey = "targetClass_" + targetClassDto.getQualifiedName();
        PsiClass cached = (PsiClass) cache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            return cached;
        }
        
        statistics.recordPsiClassLookup();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass psiClass = facade.findClass(targetClassDto.getQualifiedName(), GlobalSearchScope.allScope(project));
        
        if (psiClass != null && psiClass.isValid()) {
            cache.put(cacheKey, psiClass);
        }
        
        return psiClass;
    }
    
    /**
     * 查找目标方法
     */
    public PsiMethod findTargetMethod() {
        PsiClass targetClass = findTargetClass();
        if (targetClass == null || targetMethodDto == null) {
            return null;
        }
        
        String cacheKey = "targetMethod_" + targetClass.getQualifiedName() + "_" + targetMethodDto.getMethodName();
        PsiMethod cached = (PsiMethod) cache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            return cached;
        }
        
        statistics.recordPsiMethodLookup();
        
        for (PsiMethod method : targetClass.getMethods()) {
            if (targetMethodDto.getMethodName().equals(method.getName())) {
                // 检查参数类型是否匹配
                List<String> dtoParamTypes = targetMethodDto.getParameterTypes();
                PsiParameter[] methodParams = method.getParameterList().getParameters();
                
                if (dtoParamTypes.size() != methodParams.length) {
                    continue;
                }
                
                boolean paramsMatch = true;
                for (int i = 0; i < dtoParamTypes.size(); i++) {
                    String dtoParamType = dtoParamTypes.get(i);
                    String methodParamType = methodParams[i].getType().getCanonicalText();
                    if (!dtoParamType.equals(methodParamType)) {
                        paramsMatch = false;
                        break;
                    }
                }
                
                if (paramsMatch) {
                    cache.put(cacheKey, method);
                    return method;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 获取统计信息
     */
    public ContextStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        contextLock.writeLock().lock();
        try {
            cache.clear();
            statistics.recordOperation("cacheClear");
        } finally {
            contextLock.writeLock().unlock();
        }
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * 关闭上下文
     */
    public void shutdown() {
        contextLock.writeLock().lock();
        try {
            if (isShutdown) {
                return;
            }
            
            isShutdown = true;
            cache.clear();
            
            // 关闭上下文
            if (springContext != null) {
                // SpringContext 可能没有关闭方法，这里只是清理引用
                springContext = null;
            }
            
            if (myBatisContext != null) {
                // MyBatisContext 可能没有关闭方法，这里只是清理引用
                myBatisContext = null;
            }
            
            OptimizedLogUtil.info("ThreadSafeLiteProjectContext 已关闭");
        } finally {
            contextLock.writeLock().unlock();
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
            return "ThreadSafeLiteProjectContext{shutdown=true}";
        }
        
        return String.format(
            "ThreadSafeLiteProjectContext{project=%s, modules=%d, multiModule=%s, buildTool=%s, initialized=%s}",
            project.getName(), modules.size(), multiModule, buildToolType, isInitialized
        );
    }
}
