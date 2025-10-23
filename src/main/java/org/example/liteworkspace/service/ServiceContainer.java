package org.example.liteworkspace.service;

import com.intellij.openapi.project.Project;
import org.example.liteworkspace.service.impl.BeanAnalysisServiceImpl;
import org.example.liteworkspace.service.impl.ConfigurationServiceImpl;
import org.example.liteworkspace.util.LogUtil;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 项目级服务容器
 * 负责管理插件服务的生命周期和依赖注入
 * 每个项目有独立的服务容器，避免项目间数据污染
 */
public final class ServiceContainer {
    
    // 项目级服务容器映射
    private static final ConcurrentMap<String, ProjectServiceContainer> projectContainers = new ConcurrentHashMap<>();
    
    // 全局服务类型注册（只读，线程安全）
    private static final ConcurrentMap<Class<?>, String> serviceTypes = new ConcurrentHashMap<>();
    
    static {
        // 注册服务类型
        serviceTypes.put(BeanAnalysisService.class, "beanAnalysisService");
        serviceTypes.put(ConfigurationService.class, "configurationService");
    }
    
    private ServiceContainer() {
        // 工具类，禁止实例化
    }
    
    /**
     * 初始化指定项目的服务容器
     * 
     * @param project 项目
     */
    public static synchronized void initialize(Project project) {
        Objects.requireNonNull(project, "Project cannot be null");
        
        String projectKey = getProjectKey(project);
        
        if (projectContainers.containsKey(projectKey)) {
            LogUtil.debug("项目 {} 的服务容器已初始化", project.getName());
            return;
        }
        
        LogUtil.info("初始化项目 {} 的服务容器", project.getName());
        
        try {
            ProjectServiceContainer container = new ProjectServiceContainer(project);
            projectContainers.put(projectKey, container);
            
            LogUtil.info("项目 {} 服务容器初始化完成", project.getName());
            
        } catch (Exception e) {
            LogUtil.error("项目 {} 服务容器初始化失败", e, project.getName());
            throw new RuntimeException("服务容器初始化失败: " + project.getName(), e);
        }
    }
    
    /**
     * 获取Bean分析服务
     * 
     * @param project 项目
     * @return Bean分析服务
     */
    public static BeanAnalysisService getBeanAnalysisService(Project project) {
        ensureInitialized(project);
        return getProjectContainer(project).getService("beanAnalysisService", BeanAnalysisService.class);
    }
    
    /**
     * 获取配置服务
     * 
     * @param project 项目
     * @return 配置服务
     */
    public static ConfigurationService getConfigurationService(Project project) {
        ensureInitialized(project);
        return getProjectContainer(project).getService("configurationService", ConfigurationService.class);
    }
    
    /**
     * 根据类型获取服务
     * 
     * @param serviceClass 服务类型
     * @param project 项目
     * @param <T> 服务类型
     * @return 服务实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getService(Class<T> serviceClass, Project project) {
        ensureInitialized(project);
        
        String serviceName = serviceTypes.get(serviceClass);
        if (serviceName == null) {
            throw new IllegalArgumentException("未注册的服务类型: " + serviceClass.getName());
        }
        
        return (T) getProjectContainer(project).getService(serviceName, serviceClass);
    }
    
    /**
     * 注册服务到指定项目
     * 
     * @param serviceName 服务名称
     * @param service 服务实例
     * @param project 项目
     * @param <T> 服务类型
     */
    public static <T> void registerService(String serviceName, T service, Project project) {
        Objects.requireNonNull(serviceName, "Service name cannot be null");
        Objects.requireNonNull(service, "Service cannot be null");
        Objects.requireNonNull(project, "Project cannot be null");
        
        ensureInitialized(project);
        getProjectContainer(project).registerService(serviceName, service);
        LogUtil.debug("为项目 {} 注册服务: {}", project.getName(), serviceName);
    }
    
    /**
     * 注册服务类型
     * 
     * @param serviceClass 服务类型
     * @param serviceName 服务名称
     * @param <T> 服务类型
     */
    public static <T> void registerServiceType(Class<T> serviceClass, String serviceName) {
        Objects.requireNonNull(serviceClass, "Service class cannot be null");
        Objects.requireNonNull(serviceName, "Service name cannot be null");
        
        serviceTypes.put(serviceClass, serviceName);
        LogUtil.debug("注册服务类型: {} -> {}", serviceClass.getSimpleName(), serviceName);
    }
    
    /**
     * 移除指定项目的服务
     * 
     * @param serviceName 服务名称
     * @param project 项目
     */
    public static void removeService(String serviceName, Project project) {
        Objects.requireNonNull(serviceName, "Service name cannot be null");
        Objects.requireNonNull(project, "Project cannot be null");
        
        ProjectServiceContainer container = projectContainers.get(getProjectKey(project));
        if (container != null) {
            Object removed = container.removeService(serviceName);
            if (removed != null) {
                LogUtil.debug("为项目 {} 移除服务: {}", project.getName(), serviceName);
            }
        }
    }
    
    /**
     * 检查指定项目的服务是否存在
     * 
     * @param serviceName 服务名称
     * @param project 项目
     * @return 是否存在
     */
    public static boolean hasService(String serviceName, Project project) {
        Objects.requireNonNull(project, "Project cannot be null");
        
        ProjectServiceContainer container = projectContainers.get(getProjectKey(project));
        return container != null && container.hasService(serviceName);
    }
    
    /**
     * 检查服务类型是否存在
     * 
     * @param serviceClass 服务类型
     * @return 是否存在
     */
    public static boolean hasServiceType(Class<?> serviceClass) {
        return serviceTypes.containsKey(serviceClass);
    }
    
    /**
     * 清理指定项目的服务容器
     * 
     * @param project 项目
     */
    public static synchronized void cleanup(Project project) {
        Objects.requireNonNull(project, "Project cannot be null");
        
        String projectKey = getProjectKey(project);
        ProjectServiceContainer removed = projectContainers.remove(projectKey);
        
        if (removed != null) {
            removed.cleanup();
            LogUtil.info("清理项目 {} 的服务容器", project.getName());
        }
    }
    
    /**
     * 清理所有项目的服务容器
     */
    public static synchronized void cleanupAll() {
        LogUtil.info("清理所有项目的服务容器");
        
        for (ProjectServiceContainer container : projectContainers.values()) {
            container.cleanup();
        }
        
        projectContainers.clear();
        LogUtil.info("所有项目服务容器清理完成");
    }
    
    /**
     * 获取指定项目的服务统计信息
     * 
     * @param project 项目
     * @return 服务统计信息
     */
    public static ServiceStatistics getServiceStatistics(Project project) {
        Objects.requireNonNull(project, "Project cannot be null");
        
        ProjectServiceContainer container = projectContainers.get(getProjectKey(project));
        if (container != null) {
            return container.getServiceStatistics();
        }
        
        return new ServiceStatistics(0, serviceTypes.size(), false);
    }
    
    /**
     * 获取所有项目的服务统计信息
     * 
     * @return 全局服务统计信息
     */
    public static GlobalServiceStatistics getGlobalServiceStatistics() {
        int totalServices = 0;
        int initializedProjects = 0;
        
        for (ProjectServiceContainer container : projectContainers.values()) {
            ServiceStatistics stats = container.getServiceStatistics();
            totalServices += stats.getServiceCount();
            if (stats.isInitialized()) {
                initializedProjects++;
            }
        }
        
        return new GlobalServiceStatistics(
            projectContainers.size(),
            initializedProjects,
            totalServices,
            serviceTypes.size()
        );
    }
    
    /**
     * 确保指定项目的容器已初始化
     */
    private static void ensureInitialized(Project project) {
        String projectKey = getProjectKey(project);
        if (!projectContainers.containsKey(projectKey)) {
            initialize(project);
        }
    }
    
    /**
     * 获取指定项目的服务容器
     */
    private static ProjectServiceContainer getProjectContainer(Project project) {
        String projectKey = getProjectKey(project);
        ProjectServiceContainer container = projectContainers.get(projectKey);
        if (container == null) {
            throw new IllegalStateException("项目 " + project.getName() + " 的服务容器未初始化");
        }
        return container;
    }
    
    /**
     * 获取项目的唯一标识
     */
    private static String getProjectKey(Project project) {
        // 使用项目路径和名称作为唯一标识
        return project.getLocationHash() + "_" + project.getName();
    }
    
    /**
     * 项目级服务容器
     * 每个项目有独立的服务实例，避免项目间数据污染
     */
    private static class ProjectServiceContainer {
        private final Project project;
        private final ConcurrentMap<String, Object> services;
        private volatile boolean initialized;
        
        public ProjectServiceContainer(Project project) {
            this.project = Objects.requireNonNull(project, "Project cannot be null");
            this.services = new ConcurrentHashMap<>();
            this.initialized = false;
            
            initializeServices();
        }
        
        /**
         * 初始化项目服务
         */
        private void initializeServices() {
            LogUtil.debug("初始化项目 {} 的服务实例", project.getName());
            
            try {
                // 注册项目特定的服务实例
                services.put("beanAnalysisService", new BeanAnalysisServiceImpl(project));
                services.put("configurationService", new ConfigurationServiceImpl());
                
                initialized = true;
                LogUtil.debug("项目 {} 服务实例初始化完成", project.getName());
                
            } catch (Exception e) {
                LogUtil.error("项目 {} 服务实例初始化失败", e, project.getName());
                throw new RuntimeException("服务实例初始化失败: " + project.getName(), e);
            }
        }
        
        /**
         * 获取服务
         */
        @SuppressWarnings("unchecked")
        public <T> T getService(String serviceName, Class<T> serviceClass) {
            Object service = services.get(serviceName);
            if (service == null) {
                throw new IllegalStateException("项目 " + project.getName() + " 中服务不存在: " + serviceName);
            }
            
            if (!serviceClass.isInstance(service)) {
                throw new IllegalStateException("项目 " + project.getName() + " 中服务类型不匹配: " + serviceName + 
                                              ", 期望: " + serviceClass.getName() + 
                                              ", 实际: " + service.getClass().getName());
            }
            
            return (T) service;
        }
        
        /**
         * 注册服务
         */
        public void registerService(String serviceName, Object service) {
            Objects.requireNonNull(serviceName, "Service name cannot be null");
            Objects.requireNonNull(service, "Service cannot be null");
            
            services.put(serviceName, service);
            LogUtil.debug("为项目 {} 注册服务: {}", project.getName(), serviceName);
        }
        
        /**
         * 移除服务
         */
        public Object removeService(String serviceName) {
            Objects.requireNonNull(serviceName, "Service name cannot be null");
            
            Object removed = services.remove(serviceName);
            if (removed != null) {
                LogUtil.debug("为项目 {} 移除服务: {}", project.getName(), serviceName);
            }
            return removed;
        }
        
        /**
         * 检查服务是否存在
         */
        public boolean hasService(String serviceName) {
            return services.containsKey(serviceName);
        }
        
        /**
         * 清理服务
         */
        public void cleanup() {
            LogUtil.debug("清理项目 {} 的服务容器", project.getName());
            
            services.clear();
            initialized = false;
            
            LogUtil.debug("项目 {} 服务容器清理完成", project.getName());
        }
        
        /**
         * 获取服务统计信息
         */
        public ServiceStatistics getServiceStatistics() {
            return new ServiceStatistics(
                services.size(),
                serviceTypes.size(),
                initialized
            );
        }
        
        /**
         * 获取项目
         */
        public Project getProject() {
            return project;
        }
        
        /**
         * 检查是否已初始化
         */
        public boolean isInitialized() {
            return initialized;
        }
    }
    
    /**
     * 服务统计信息
     */
    public static class ServiceStatistics {
        private final int serviceCount;
        private final int serviceTypeCount;
        private final boolean initialized;
        
        public ServiceStatistics(int serviceCount, int serviceTypeCount, boolean initialized) {
            this.serviceCount = serviceCount;
            this.serviceTypeCount = serviceTypeCount;
            this.initialized = initialized;
        }
        
        public int getServiceCount() {
            return serviceCount;
        }
        
        public int getServiceTypeCount() {
            return serviceTypeCount;
        }
        
        public boolean isInitialized() {
            return initialized;
        }
        
        @Override
        public String toString() {
            return String.format(
                "ServiceStatistics{serviceCount=%d, serviceTypeCount=%d, initialized=%s}",
                serviceCount, serviceTypeCount, initialized
            );
        }
    }
    
    /**
     * 全局服务统计信息
     */
    public static class GlobalServiceStatistics {
        private final int totalProjects;
        private final int initializedProjects;
        private final int totalServices;
        private final int serviceTypesCount;
        
        public GlobalServiceStatistics(int totalProjects, int initializedProjects, int totalServices, int serviceTypesCount) {
            this.totalProjects = totalProjects;
            this.initializedProjects = initializedProjects;
            this.totalServices = totalServices;
            this.serviceTypesCount = serviceTypesCount;
        }
        
        public int getTotalProjects() {
            return totalProjects;
        }
        
        public int getInitializedProjects() {
            return initializedProjects;
        }
        
        public int getTotalServices() {
            return totalServices;
        }
        
        public int getServiceTypesCount() {
            return serviceTypesCount;
        }
        
        @Override
        public String toString() {
            return String.format(
                "GlobalServiceStatistics{totalProjects=%d, initializedProjects=%d, totalServices=%d, serviceTypesCount=%d}",
                totalProjects, initializedProjects, totalServices, serviceTypesCount
            );
        }
    }
}
