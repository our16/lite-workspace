package org.example.liteworkspace.event;

import java.util.List;

/**
 * 插件标准事件类型
 * 
 * 定义系统中各种标准事件
 */
public class PluginEvents {
    
    // ===================== 扫描相关事件 =====================
    
    /**
     * 扫描开始事件
     */
    public static class ScanStartedEvent extends Event {
        private final String scanType;
        private final String scope;
        
        public ScanStartedEvent(String source, String scanType, String scope) {
            super(source);
            this.scanType = scanType;
            this.scope = scope;
            addMetadata("scanType", scanType);
            addMetadata("scope", scope);
        }
        
        public String getScanType() { return scanType; }
        public String getScope() { return scope; }
        
        @Override
        public String getDescription() {
            return String.format("开始扫描: %s (范围: %s)", scanType, scope);
        }
        
        @Override
        public boolean isCritical() {
            return "BEAN_SCAN".equals(scanType);
        }
    }
    
    /**
     * 扫描完成事件
     */
    public static class ScanCompletedEvent extends Event {
        private final String scanType;
        private final int itemsFound;
        private final long duration;
        
        public ScanCompletedEvent(String source, String scanType, int itemsFound, long duration) {
            super(source);
            this.scanType = scanType;
            this.itemsFound = itemsFound;
            this.duration = duration;
            addMetadata("scanType", scanType);
            addMetadata("itemsFound", itemsFound);
            addMetadata("duration", duration);
        }
        
        public String getScanType() { return scanType; }
        public int getItemsFound() { return itemsFound; }
        public long getDuration() { return duration; }
        
        @Override
        public String getDescription() {
            return String.format("扫描完成: %s (发现 %d 项，耗时 %dms)", scanType, itemsFound, duration);
        }
    }
    
    /**
     * 扫描失败事件
     */
    public static class ScanFailedEvent extends Event {
        private final String scanType;
        private final String errorMessage;
        private final Throwable cause;
        
        public ScanFailedEvent(String source, String scanType, String errorMessage, Throwable cause) {
            super(source);
            this.scanType = scanType;
            this.errorMessage = errorMessage;
            this.cause = cause;
            addMetadata("scanType", scanType);
            addMetadata("errorMessage", errorMessage);
            if (cause != null) {
                addMetadata("causeType", cause.getClass().getSimpleName());
            }
        }
        
        public String getScanType() { return scanType; }
        public String getErrorMessage() { return errorMessage; }
        public Throwable getCause() { return cause; }
        
        @Override
        public String getDescription() {
            return String.format("扫描失败: %s - %s", scanType, errorMessage);
        }
        
        @Override
        public boolean isCritical() {
            return true;
        }
    }
    
    // ===================== 缓存相关事件 =====================
    
    /**
     * 缓存命中事件
     */
    public static class CacheHitEvent extends Event {
        private final String cacheKey;
        private final String cacheType;
        
        public CacheHitEvent(String source, String cacheKey, String cacheType) {
            super(source);
            this.cacheKey = cacheKey;
            this.cacheType = cacheType;
            addMetadata("cacheKey", cacheKey);
            addMetadata("cacheType", cacheType);
        }
        
        public String getCacheKey() { return cacheKey; }
        public String getCacheType() { return cacheType; }
        
        @Override
        public String getDescription() {
            return String.format("缓存命中: %s [%s]", cacheKey, cacheType);
        }
    }
    
    /**
     * 缓存未命中事件
     */
    public static class CacheMissEvent extends Event {
        private final String cacheKey;
        private final String cacheType;
        
        public CacheMissEvent(String source, String cacheKey, String cacheType) {
            super(source);
            this.cacheKey = cacheKey;
            this.cacheType = cacheType;
            addMetadata("cacheKey", cacheKey);
            addMetadata("cacheType", cacheType);
        }
        
        public String getCacheKey() { return cacheKey; }
        public String getCacheType() { return cacheType; }
        
        @Override
        public String getDescription() {
            return String.format("缓存未命中: %s [%s]", cacheKey, cacheType);
        }
    }
    
    /**
     * 缓存清理事件
     */
    public static class CacheClearedEvent extends Event {
        private final String cacheType;
        private final int clearedCount;
        
        public CacheClearedEvent(String source, String cacheType, int clearedCount) {
            super(source);
            this.cacheType = cacheType;
            this.clearedCount = clearedCount;
            addMetadata("cacheType", cacheType);
            addMetadata("clearedCount", clearedCount);
        }
        
        public String getCacheType() { return cacheType; }
        public int getClearedCount() { return clearedCount; }
        
        @Override
        public String getDescription() {
            return String.format("缓存清理: %s (清理 %d 项)", cacheType, clearedCount);
        }
    }
    
    // ===================== 任务相关事件 =====================
    
    /**
     * 任务提交事件
     */
    public static class TaskSubmittedEvent extends Event {
        private final String taskId;
        private final String taskName;
        private final String taskType;
        
        public TaskSubmittedEvent(String source, String taskId, String taskName, String taskType) {
            super(source);
            this.taskId = taskId;
            this.taskName = taskName;
            this.taskType = taskType;
            addMetadata("taskId", taskId);
            addMetadata("taskName", taskName);
            addMetadata("taskType", taskType);
        }
        
        public String getTaskId() { return taskId; }
        public String getTaskName() { return taskName; }
        public String getTaskType() { return taskType; }
        
        @Override
        public String getDescription() {
            return String.format("任务提交: %s [%s]", taskName, taskType);
        }
    }
    
    /**
     * 任务完成事件
     */
    public static class TaskCompletedEvent extends Event {
        private final String taskId;
        private final String taskName;
        private final long duration;
        private final boolean success;
        
        public TaskCompletedEvent(String source, String taskId, String taskName, long duration, boolean success) {
            super(source);
            this.taskId = taskId;
            this.taskName = taskName;
            this.duration = duration;
            this.success = success;
            addMetadata("taskId", taskId);
            addMetadata("taskName", taskName);
            addMetadata("duration", duration);
            addMetadata("success", success);
        }
        
        public String getTaskId() { return taskId; }
        public String getTaskName() { return taskName; }
        public long getDuration() { return duration; }
        public boolean isSuccess() { return success; }
        
        @Override
        public String getDescription() {
            return String.format("任务完成: %s (%s, 耗时 %dms)", 
                taskName, success ? "成功" : "失败", duration);
        }
    }
    
    // ===================== 配置相关事件 =====================
    
    /**
     * 配置变更事件
     */
    public static class ConfigurationChangedEvent extends Event {
        private final String configKey;
        private final Object oldValue;
        private final Object newValue;
        
        public ConfigurationChangedEvent(String source, String configKey, Object oldValue, Object newValue) {
            super(source);
            this.configKey = configKey;
            this.oldValue = oldValue;
            this.newValue = newValue;
            addMetadata("configKey", configKey);
            addMetadata("oldValue", oldValue);
            addMetadata("newValue", newValue);
        }
        
        public String getConfigKey() { return configKey; }
        public Object getOldValue() { return oldValue; }
        public Object getNewValue() { return newValue; }
        
        @Override
        public String getDescription() {
            return String.format("配置变更: %s = %s (原值: %s)", configKey, newValue, oldValue);
        }
    }
    
    // ===================== 系统相关事件 =====================
    
    /**
     * 系统启动事件
     */
    public static class SystemStartedEvent extends Event {
        private final long startupTime;
        
        public SystemStartedEvent(String source, long startupTime) {
            super(source);
            this.startupTime = startupTime;
            addMetadata("startupTime", startupTime);
        }
        
        public long getStartupTime() { return startupTime; }
        
        @Override
        public String getDescription() {
            return String.format("系统启动完成 (耗时 %dms)", startupTime);
        }
        
        @Override
        public boolean isCritical() {
            return true;
        }
    }
    
    /**
     * 系统关闭事件
     */
    public static class SystemShutdownEvent extends Event {
        private final String reason;
        
        public SystemShutdownEvent(String source, String reason) {
            super(source);
            this.reason = reason;
            addMetadata("reason", reason);
        }
        
        public String getReason() { return reason; }
        
        @Override
        public String getDescription() {
            return String.format("系统关闭: %s", reason);
        }
        
        @Override
        public boolean isCritical() {
            return true;
        }
    }
    
    /**
     * 性能警告事件
     */
    public static class PerformanceWarningEvent extends Event {
        private final String metric;
        private final double value;
        private final double threshold;
        
        public PerformanceWarningEvent(String source, String metric, double value, double threshold) {
            super(source);
            this.metric = metric;
            this.value = value;
            this.threshold = threshold;
            addMetadata("metric", metric);
            addMetadata("value", value);
            addMetadata("threshold", threshold);
        }
        
        public String getMetric() { return metric; }
        public double getValue() { return value; }
        public double getThreshold() { return threshold; }
        
        @Override
        public String getDescription() {
            return String.format("性能警告: %s = %.2f (阈值: %.2f)", metric, value, threshold);
        }
    }
    
    // ===================== 用户界面相关事件 =====================
    
    /**
     * UI 更新事件
     */
    public static class UIUpdateEvent extends Event {
        private final String component;
        private final String action;
        
        public UIUpdateEvent(String source, String component, String action) {
            super(source);
            this.component = component;
            this.action = action;
            addMetadata("component", component);
            addMetadata("action", action);
        }
        
        public String getComponent() { return component; }
        public String getAction() { return action; }
        
        @Override
        public String getDescription() {
            return String.format("UI更新: %s - %s", component, action);
        }
    }
    
    /**
     * 通知事件
     */
    public static class NotificationEvent extends Event {
        private final String title;
        private final String message;
        private final String type;
        
        public NotificationEvent(String source, String title, String message, String type) {
            super(source);
            this.title = title;
            this.message = message;
            this.type = type;
            addMetadata("title", title);
            addMetadata("message", message);
            addMetadata("type", type);
        }
        
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public String getType() { return type; }
        
        @Override
        public String getDescription() {
            return String.format("通知: %s - %s [%s]", title, message, type);
        }
    }
    
    // ===================== 错误相关事件 =====================
    
    /**
     * 错误事件
     */
    public static class ErrorEvent extends Event {
        private final String errorCode;
        private final String errorMessage;
        private final Throwable cause;
        private final String context;
        
        public ErrorEvent(String source, String errorCode, String errorMessage, Throwable cause, String context) {
            super(source);
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.cause = cause;
            this.context = context;
            addMetadata("errorCode", errorCode);
            addMetadata("errorMessage", errorMessage);
            addMetadata("context", context);
            if (cause != null) {
                addMetadata("causeType", cause.getClass().getSimpleName());
            }
        }
        
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public Throwable getCause() { return cause; }
        public String getContext() { return context; }
        
        @Override
        public String getDescription() {
            return String.format("错误: %s - %s [%s]", errorCode, errorMessage, context);
        }
        
        @Override
        public boolean isCritical() {
            return "SYSTEM_ERROR".equals(errorCode) || "FATAL_ERROR".equals(errorCode);
        }
    }
    
    // ===================== 资源相关事件 =====================
    
    /**
     * 资源注册事件
     */
    public static class ResourceRegisteredEvent extends Event {
        private final String resourceId;
        private final String resourceType;
        private final long memoryUsage;
        
        public ResourceRegisteredEvent(String source, String resourceId, String resourceType, long memoryUsage) {
            super(source);
            this.resourceId = resourceId;
            this.resourceType = resourceType;
            this.memoryUsage = memoryUsage;
            addMetadata("resourceId", resourceId);
            addMetadata("resourceType", resourceType);
            addMetadata("memoryUsage", memoryUsage);
        }
        
        public String getResourceId() { return resourceId; }
        public String getResourceType() { return resourceType; }
        public long getMemoryUsage() { return memoryUsage; }
        
        @Override
        public String getDescription() {
            return String.format("资源注册: %s [%s, %d bytes]", resourceId, resourceType, memoryUsage);
        }
    }
    
    /**
     * 资源释放事件
     */
    public static class ResourceDisposedEvent extends Event {
        private final String resourceId;
        private final String resourceType;
        private final long memoryFreed;
        
        public ResourceDisposedEvent(String source, String resourceId, String resourceType, long memoryFreed) {
            super(source);
            this.resourceId = resourceId;
            this.resourceType = resourceType;
            this.memoryFreed = memoryFreed;
            addMetadata("resourceId", resourceId);
            addMetadata("resourceType", resourceType);
            addMetadata("memoryFreed", memoryFreed);
        }
        
        public String getResourceId() { return resourceId; }
        public String getResourceType() { return resourceType; }
        public long getMemoryFreed() { return memoryFreed; }
        
        @Override
        public String getDescription() {
            return String.format("资源释放: %s [%s, %d bytes]", resourceId, resourceType, memoryFreed);
        }
    }
    
    /**
     * 资源泄漏检测事件
     */
    public static class ResourceLeakDetectedEvent extends Event {
        private final int leakCount;
        
        public ResourceLeakDetectedEvent(String source, int leakCount) {
            super(source);
            this.leakCount = leakCount;
            addMetadata("leakCount", leakCount);
        }
        
        public int getLeakCount() { return leakCount; }
        
        @Override
        public String getDescription() {
            return String.format("检测到资源泄漏: %d 个", leakCount);
        }
        
        @Override
        public boolean isCritical() {
            return leakCount > 10; // 超过10个泄漏认为是关键问题
        }
    }
    
    /**
     * 资源清理事件
     */
    public static class ResourceCleanupEvent extends Event {
        private final int cleanedCount;
        
        public ResourceCleanupEvent(String source, int cleanedCount) {
            super(source);
            this.cleanedCount = cleanedCount;
            addMetadata("cleanedCount", cleanedCount);
        }
        
        public int getCleanedCount() { return cleanedCount; }
        
        @Override
        public String getDescription() {
            return String.format("资源清理: %d 个资源", cleanedCount);
        }
    }
    
    /**
     * 内存压力警告事件
     */
    public static class MemoryPressureEvent extends Event {
        private final double memoryUsageRatio;
        private final long usedMemory;
        private final long maxMemory;
        
        public MemoryPressureEvent(String source, double memoryUsageRatio, long usedMemory, long maxMemory) {
            super(source);
            this.memoryUsageRatio = memoryUsageRatio;
            this.usedMemory = usedMemory;
            this.maxMemory = maxMemory;
            addMetadata("memoryUsageRatio", memoryUsageRatio);
            addMetadata("usedMemory", usedMemory);
            addMetadata("maxMemory", maxMemory);
        }
        
        public double getMemoryUsageRatio() { return memoryUsageRatio; }
        public long getUsedMemory() { return usedMemory; }
        public long getMaxMemory() { return maxMemory; }
        
        @Override
        public String getDescription() {
            return String.format("内存压力警告: %.1f%% (%d MB / %d MB)", 
                memoryUsageRatio * 100, usedMemory / 1024 / 1024, maxMemory / 1024 / 1024);
        }
        
        @Override
        public boolean isCritical() {
            return memoryUsageRatio > 0.9; // 超过90%认为是关键问题
        }
    }
}
