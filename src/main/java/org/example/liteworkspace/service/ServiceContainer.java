package org.example.liteworkspace.service;

import com.intellij.openapi.project.Project;
import org.example.liteworkspace.service.impl.BeanAnalysisServiceImpl;
import org.example.liteworkspace.service.impl.ConfigurationServiceImpl;
import org.example.liteworkspace.util.LogUtil;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 服务容器
 * 负责管理插件服务的生命周期和依赖注入
 */
public final class ServiceContainer {
    
    private static final ConcurrentMap<String, Object> services = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, String> serviceTypes = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    
    static {
        // 注册服务类型
        serviceTypes.put(BeanAnalysisService.class, "beanAnalysisService");
        serviceTypes.put(ConfigurationService.class, "configurationService");
    }
    
    private ServiceContainer() {
        // 工具类，禁止实例化
    }
    
    /**
     * 初始化服务容器
     * 
     * @param project 项目
     */
    public static synchronized void initialize(Project project) {
        if (initialized) {
            return;
        }
        
        LogUtil.info("初始化服务容器");
        
        try {
            // 注册核心服务
            registerService("beanAnalysisService", new BeanAnalysisServiceImpl(project));
            registerService("configurationService", new ConfigurationServiceImpl());
            
            initialized = true;
            LogUtil.info("服务容器初始化完成");
            
        } catch (Exception e) {
            LogUtil.error("服务容器初始化失败", e);
            throw new RuntimeException("服务容器初始化失败", e);
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
        return getService("beanAnalysisService", BeanAnalysisService.class);
    }
    
    /**
     * 获取配置服务
     * 
     * @param project 项目
     * @return 配置服务
     */
    public static ConfigurationService getConfigurationService(Project project) {
        ensureInitialized(project);
        return getService("configurationService", ConfigurationService.class);
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
        
        return (T) getService(serviceName, serviceClass);
    }
    
    /**
     * 注册服务
     * 
     * @param serviceName 服务名称
     * @param service 服务实例
     * @param <T> 服务类型
     */
    public static <T> void registerService(String serviceName, T service) {
        Objects.requireNonNull(serviceName, "Service name cannot be null");
        Objects.requireNonNull(service, "Service cannot be null");
        
        services.put(serviceName, service);
        LogUtil.debug("注册服务: {}", serviceName);
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
     * 移除服务
     * 
     * @param serviceName 服务名称
     */
    public static void removeService(String serviceName) {
        Objects.requireNonNull(serviceName, "Service name cannot be null");
        
        Object removed = services.remove(serviceName);
        if (removed != null) {
            LogUtil.debug("移除服务: {}", serviceName);
        }
    }
    
    /**
     * 检查服务是否存在
     * 
     * @param serviceName 服务名称
     * @return 是否存在
     */
    public static boolean hasService(String serviceName) {
        return services.containsKey(serviceName);
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
     * 清理所有服务
     */
    public static synchronized void cleanup() {
        LogUtil.info("清理服务容器");
        
        services.clear();
        initialized = false;
        
        LogUtil.info("服务容器清理完成");
    }
    
    /**
     * 获取服务统计信息
     * 
     * @return 服务统计信息
     */
    public static ServiceStatistics getServiceStatistics() {
        return new ServiceStatistics(
            services.size(),
            serviceTypes.size(),
            initialized
        );
    }
    
    /**
     * 确保容器已初始化
     */
    private static void ensureInitialized(Project project) {
        if (!initialized) {
            initialize(project);
        }
    }
    
    /**
     * 获取服务
     */
    @SuppressWarnings("unchecked")
    private static <T> T getService(String serviceName, Class<T> serviceClass) {
        Object service = services.get(serviceName);
        if (service == null) {
            throw new IllegalStateException("服务不存在: " + serviceName);
        }
        
        if (!serviceClass.isInstance(service)) {
            throw new IllegalStateException("服务类型不匹配: " + serviceName + 
                                          ", 期望: " + serviceClass.getName() + 
                                          ", 实际: " + service.getClass().getName());
        }
        
        return (T) service;
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
}
