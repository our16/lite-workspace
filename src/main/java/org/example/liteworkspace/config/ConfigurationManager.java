package org.example.liteworkspace.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.example.liteworkspace.exception.ExceptionHandler;
import org.example.liteworkspace.util.LogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 统一配置管理器
 * 提供配置的统一管理、验证、热更新和监听机制
 */
@State(name = "LiteWorkspaceConfiguration", storages = @Storage("LiteWorkspaceConfiguration.xml"))
public class ConfigurationManager implements PersistentStateComponent<ConfigurationManager.ConfigurationState> {
    
    /**
     * 配置状态类
     */
    public static class ConfigurationState {
        // LLM 相关配置
        public String apiKey = "";
        public String apiUrl = "http://localhost/v1/chat-messages";
        public String modelName = "local";
        
        // 项目相关配置
        public String javaHome = "";
        public String mavenHome = "";
        public String gradleHome = "";
        
        // 扫描配置
        public boolean enableAutoScan = true;
        public int scanTimeout = 30000; // 30秒
        public int maxScanDepth = 10;
        public boolean excludeTestClasses = true;
        
        // 缓存配置
        public boolean enableCache = true;
        public long cacheExpireTime = 30 * 60 * 1000; // 30分钟
        public int maxCacheSize = 1000;
        
        // 性能配置
        public int threadPoolSize = Runtime.getRuntime().availableProcessors();
        public boolean enableParallelScan = true;
        
        // 任务调度配置
        public int maxConcurrentTasks = Runtime.getRuntime().availableProcessors();
        public int maxRetryAttempts = 3;
        public long retryDelay = 1000; // 1秒
        
        // 日志配置
        public String logLevel = "INFO";
        public boolean enableDebugLog = false;
        
        // UI配置
        public boolean showProgressIndicator = true;
        public boolean enableNotifications = true;
    }
    
    private ConfigurationState state = new ConfigurationState();
    private final List<ConfigurationListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Object> runtimeCache = new ConcurrentHashMap<>();
    
    public ConfigurationManager() {
        // 初始化时验证配置
        validateConfiguration();
    }
    
    /**
     * 获取单例实例
     */
    public static ConfigurationManager getInstance() {
        return ApplicationManager.getApplication().getService(ConfigurationManager.class);
    }
    
    /**
     * 获取项目特定的配置管理器
     */
    public static ConfigurationManager getInstance(Project project) {
        return project.getService(ConfigurationManager.class);
    }
    
    @Override
    public @Nullable ConfigurationState getState() {
        return state;
    }
    
    @Override
    public void loadState(@NotNull ConfigurationState state) {
        ConfigurationState oldState = this.state;
        this.state = state;
        
        // 验证加载的配置
        validateConfiguration();
        
        // 通知配置变更
        notifyConfigurationChanged(oldState, this.state);
        
        LogUtil.info("配置状态已加载");
    }
    
    /**
     * 验证配置
     */
    private void validateConfiguration() {
        boolean hasErrors = false;
        
        // 验证 API URL
        if (state.apiUrl == null || !state.apiUrl.startsWith("http")) {
            LogUtil.warn("API URL 格式无效，使用默认值");
            state.apiUrl = "http://localhost/v1/chat-messages";
            hasErrors = true;
        }
        
        // 验证扫描超时
        if (state.scanTimeout <= 0 || state.scanTimeout > 300000) {
            LogUtil.warn("扫描超时时间无效，使用默认值");
            state.scanTimeout = 30000;
            hasErrors = true;
        }
        
        // 验证线程池大小
        if (state.threadPoolSize <= 0) {
            LogUtil.warn("线程池大小无效，使用默认值");
            state.threadPoolSize = Runtime.getRuntime().availableProcessors();
            hasErrors = true;
        }
        
        if (hasErrors) {
            LogUtil.info("配置验证完成，已应用默认值");
        }
    }
    
    /**
     * 通知配置变更
     */
    private void notifyConfigurationChanged(ConfigurationState oldState, ConfigurationState newState) {
        ConfigurationEvent event = new ConfigurationEvent(oldState, newState);
        
        for (ConfigurationListener listener : listeners) {
            try {
                listener.onConfigurationChanged(event);
            } catch (Exception e) {
                ExceptionHandler.handleSilently(e, "配置监听器执行失败");
            }
        }
    }
    
    /**
     * 添加配置监听器
     */
    public void addConfigurationListener(ConfigurationListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }
    
    /**
     * 移除配置监听器
     */
    public void removeConfigurationListener(ConfigurationListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 更新配置（支持热更新）
     */
    public void updateConfiguration(Runnable updater) {
        ConfigurationState oldState = copyState(state);
        
        try {
            updater.run();
            validateConfiguration();
            notifyConfigurationChanged(oldState, state);
            LogUtil.info("配置已更新");
        } catch (RuntimeException e) {
            // 回滚配置
            this.state = oldState;
            ExceptionHandler.handle(null, e, "配置更新失败");
            LogUtil.error("配置更新失败，已回滚: " + e.getMessage(), e);
        }
    }
    
    /**
     * 复制配置状态
     */
    private ConfigurationState copyState(ConfigurationState original) {
        ConfigurationState copy = new ConfigurationState();
        copy.apiKey = original.apiKey;
        copy.apiUrl = original.apiUrl;
        copy.modelName = original.modelName;
        copy.javaHome = original.javaHome;
        copy.mavenHome = original.mavenHome;
        copy.gradleHome = original.gradleHome;
        copy.enableAutoScan = original.enableAutoScan;
        copy.scanTimeout = original.scanTimeout;
        copy.maxScanDepth = original.maxScanDepth;
        copy.excludeTestClasses = original.excludeTestClasses;
        copy.enableCache = original.enableCache;
        copy.cacheExpireTime = original.cacheExpireTime;
        copy.maxCacheSize = original.maxCacheSize;
        copy.threadPoolSize = original.threadPoolSize;
        copy.enableParallelScan = original.enableParallelScan;
        copy.maxConcurrentTasks = original.maxConcurrentTasks;
        copy.maxRetryAttempts = original.maxRetryAttempts;
        copy.retryDelay = original.retryDelay;
        copy.logLevel = original.logLevel;
        copy.enableDebugLog = original.enableDebugLog;
        copy.showProgressIndicator = original.showProgressIndicator;
        copy.enableNotifications = original.enableNotifications;
        return copy;
    }
    
    /**
     * 获取运行时缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T getRuntimeValue(String key, Class<T> type) {
        Object value = runtimeCache.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 设置运行时缓存值
     */
    public <T> void setRuntimeValue(String key, T value) {
        if (value != null) {
            runtimeCache.put(key, value);
        } else {
            runtimeCache.remove(key);
        }
    }
    
    /**
     * 获取配置统计信息
     */
    public ConfigurationStatistics getStatistics() {
        return new ConfigurationStatistics(
            listeners.size(),
            runtimeCache.size()
        );
    }
    
    // Getter 方法
    public String getApiKey() { return state.apiKey; }
    public String getApiUrl() { return state.apiUrl; }
    public String getModelName() { return state.modelName; }
    public String getJavaHome() { return state.javaHome; }
    public String getMavenHome() { return state.mavenHome; }
    public String getGradleHome() { return state.gradleHome; }
    public boolean isEnableAutoScan() { return state.enableAutoScan; }
    public int getScanTimeout() { return state.scanTimeout; }
    public int getMaxScanDepth() { return state.maxScanDepth; }
    public boolean isExcludeTestClasses() { return state.excludeTestClasses; }
    public boolean isEnableCache() { return state.enableCache; }
    public long getCacheExpireTime() { return state.cacheExpireTime; }
    public int getMaxCacheSize() { return state.maxCacheSize; }
    public int getThreadPoolSize() { return state.threadPoolSize; }
    public boolean isEnableParallelScan() { return state.enableParallelScan; }
    public String getLogLevel() { return state.logLevel; }
    public boolean isEnableDebugLog() { return state.enableDebugLog; }
    public boolean isShowProgressIndicator() { return state.showProgressIndicator; }
    public boolean isEnableNotifications() { return state.enableNotifications; }
    public int getMaxConcurrentTasks() { return state.maxConcurrentTasks; }
    public int getMaxRetryAttempts() { return state.maxRetryAttempts; }
    public long getRetryDelay() { return state.retryDelay; }
    
    // Setter 方法（支持热更新）
    public void setApiKey(String apiKey) {
        updateConfiguration(() -> state.apiKey = apiKey);
    }
    
    public void setApiUrl(String apiUrl) {
        updateConfiguration(() -> state.apiUrl = apiUrl);
    }
    
    public void setModelName(String modelName) {
        updateConfiguration(() -> state.modelName = modelName);
    }
    
    public void setJavaHome(String javaHome) {
        updateConfiguration(() -> state.javaHome = javaHome);
    }
    
    public void setMavenHome(String mavenHome) {
        updateConfiguration(() -> state.mavenHome = mavenHome);
    }
    
    public void setGradleHome(String gradleHome) {
        updateConfiguration(() -> state.gradleHome = gradleHome);
    }
    
    public void setEnableAutoScan(boolean enableAutoScan) {
        updateConfiguration(() -> state.enableAutoScan = enableAutoScan);
    }
    
    public void setScanTimeout(int scanTimeout) {
        updateConfiguration(() -> state.scanTimeout = scanTimeout);
    }
    
    public void setMaxScanDepth(int maxScanDepth) {
        updateConfiguration(() -> state.maxScanDepth = maxScanDepth);
    }
    
    public void setExcludeTestClasses(boolean excludeTestClasses) {
        updateConfiguration(() -> state.excludeTestClasses = excludeTestClasses);
    }
    
    public void setEnableCache(boolean enableCache) {
        updateConfiguration(() -> state.enableCache = enableCache);
    }
    
    public void setCacheExpireTime(long cacheExpireTime) {
        updateConfiguration(() -> state.cacheExpireTime = cacheExpireTime);
    }
    
    public void setMaxCacheSize(int maxCacheSize) {
        updateConfiguration(() -> state.maxCacheSize = maxCacheSize);
    }
    
    public void setThreadPoolSize(int threadPoolSize) {
        updateConfiguration(() -> state.threadPoolSize = threadPoolSize);
    }
    
    public void setEnableParallelScan(boolean enableParallelScan) {
        updateConfiguration(() -> state.enableParallelScan = enableParallelScan);
    }
    
    public void setLogLevel(String logLevel) {
        updateConfiguration(() -> state.logLevel = logLevel);
    }
    
    public void setEnableDebugLog(boolean enableDebugLog) {
        updateConfiguration(() -> state.enableDebugLog = enableDebugLog);
    }
    
    public void setShowProgressIndicator(boolean showProgressIndicator) {
        updateConfiguration(() -> state.showProgressIndicator = showProgressIndicator);
    }
    
    public void setEnableNotifications(boolean enableNotifications) {
        updateConfiguration(() -> state.enableNotifications = enableNotifications);
    }
    
    public void setMaxConcurrentTasks(int maxConcurrentTasks) {
        updateConfiguration(() -> state.maxConcurrentTasks = maxConcurrentTasks);
    }
    
    public void setMaxRetryAttempts(int maxRetryAttempts) {
        updateConfiguration(() -> state.maxRetryAttempts = maxRetryAttempts);
    }
    
    public void setRetryDelay(long retryDelay) {
        updateConfiguration(() -> state.retryDelay = retryDelay);
    }
    
    /**
     * 配置监听器接口
     */
    public interface ConfigurationListener {
        void onConfigurationChanged(ConfigurationEvent event);
    }
    
    /**
     * 配置事件
     */
    public static class ConfigurationEvent {
        private final ConfigurationState oldState;
        private final ConfigurationState newState;
        
        public ConfigurationEvent(ConfigurationState oldState, ConfigurationState newState) {
            this.oldState = oldState;
            this.newState = newState;
        }
        
        public ConfigurationState getOldState() { return oldState; }
        public ConfigurationState getNewState() { return newState; }
    }
    
    /**
     * 配置统计信息
     */
    public static class ConfigurationStatistics {
        private final int listenerCount;
        private final int runtimeCacheSize;
        
        public ConfigurationStatistics(int listenerCount, int runtimeCacheSize) {
            this.listenerCount = listenerCount;
            this.runtimeCacheSize = runtimeCacheSize;
        }
        
        public int getListenerCount() { return listenerCount; }
        public int getRuntimeCacheSize() { return runtimeCacheSize; }
        
        @Override
        public String toString() {
            return String.format(
                "ConfigurationStatistics{listeners=%d, runtimeCache=%d}",
                listenerCount, runtimeCacheSize
            );
        }
    }
}
