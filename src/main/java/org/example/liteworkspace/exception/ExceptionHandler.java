package org.example.liteworkspace.exception;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import org.example.liteworkspace.util.LogUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异常处理器
 * 提供统一的异常处理、日志记录和用户通知机制
 */
public final class ExceptionHandler {
    
    private static final String NOTIFICATION_GROUP_ID = "LiteWorkspace Error";
    private static final ConcurrentMap<String, AtomicLong> errorCounters = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Long> lastErrorTime = new ConcurrentHashMap<>();
    private static final long ERROR_THROTTLE_INTERVAL = 5000; // 5秒内相同错误只显示一次
    
    private ExceptionHandler() {
        // 工具类，禁止实例化
    }
    
    /**
     * 处理插件异常
     * 
     * @param project 项目
     * @param exception 异常
     */
    public static void handle(Project project, PluginException exception) {
        if (exception == null) {
            return;
        }
        
        // 记录错误日志
        logError(exception);
        
        // 显示用户通知
        showErrorNotification(project, exception);
        
        // 更新错误统计
        updateErrorStatistics(exception);
    }
    
    /**
     * 处理通用异常
     * 
     * @param project 项目
     * @param exception 异常
     * @param context 上下文信息
     */
    public static void handle(Project project, Throwable exception, String context) {
        if (exception == null) {
            return;
        }
        
        // 将通用异常包装为插件异常
        PluginException pluginException = wrapException(exception, context);
        handle(project, pluginException);
    }
    
    /**
     * 处理异常但不显示通知（仅记录日志）
     * 
     * @param exception 异常
     * @param context 上下文信息
     */
    public static void handleSilently(Throwable exception, String context) {
        if (exception == null) {
            return;
        }
        
        PluginException pluginException = wrapException(exception, context);
        logError(pluginException);
        updateErrorStatistics(pluginException);
    }
    
    /**
     * 将通用异常包装为插件异常
     */
    private static PluginException wrapException(Throwable exception, String context) {
        if (exception instanceof PluginException) {
            return (PluginException) exception;
        }
        
        String errorCode = "UNKNOWN_ERROR";
        String message = exception.getMessage();
        if (message == null) {
            message = exception.getClass().getSimpleName();
        }
        
        String fullMessage = context != null ? context + ": " + message : message;
        
        // 根据异常类型确定严重程度
        PluginException.ErrorSeverity severity = PluginException.ErrorSeverity.MEDIUM;
        if (exception instanceof RuntimeException) {
            severity = PluginException.ErrorSeverity.HIGH;
        } else if (exception instanceof IllegalArgumentException) {
            severity = PluginException.ErrorSeverity.MEDIUM;
        } else if (exception instanceof IllegalStateException) {
            severity = PluginException.ErrorSeverity.HIGH;
        }
        
        return new PluginException(errorCode, fullMessage, severity, fullMessage, exception);
    }
    
    /**
     * 记录错误日志
     */
    private static void logError(PluginException exception) {
        switch (exception.getSeverity()) {
            case LOW:
                LogUtil.warn("插件警告: {}", exception.toString());
                break;
            case MEDIUM:
                LogUtil.error("插件错误: " + exception.toString(), exception.getCause());
                break;
            case HIGH:
                LogUtil.error("插件严重错误: " + exception.toString(), exception.getCause());
                break;
            case FATAL:
                LogUtil.error("插件致命错误: " + exception.toString(), exception.getCause());
                break;
        }
    }
    
    /**
     * 显示错误通知
     */
    private static void showErrorNotification(Project project, PluginException exception) {
        // 检查是否需要节流（避免重复通知）
        if (shouldThrottleError(exception)) {
            return;
        }
        
        String title = "LiteWorkspace " + exception.getSeverity().getDescription();
        String message = exception.getUserMessage();
        
        NotificationType notificationType;
        switch (exception.getSeverity()) {
            case LOW:
                notificationType = NotificationType.WARNING;
                break;
            case MEDIUM:
                notificationType = NotificationType.ERROR;
                break;
            case HIGH:
            case FATAL:
                notificationType = NotificationType.ERROR;
                break;
            default:
                notificationType = NotificationType.INFORMATION;
        }
        
        Notification notification = new Notification(
            NOTIFICATION_GROUP_ID,
            title,
            message,
            notificationType
        );
        
        // 添加错误详情
        if (exception.getTechnicalDetails() != null) {
            // 注意：这里需要创建一个 AnAction 实例，简化起见暂时注释
            // notification = notification.addAction(new ShowErrorDetailsAction(exception));
        }
        
        Notifications.Bus.notify(notification, project);
    }
    
    /**
     * 检查是否需要节流错误通知
     */
    private static boolean shouldThrottleError(PluginException exception) {
        String errorKey = exception.getErrorCode() + "_" + exception.getMessage();
        long currentTime = System.currentTimeMillis();
        
        Long lastTime = lastErrorTime.get(errorKey);
        if (lastTime != null && (currentTime - lastTime) < ERROR_THROTTLE_INTERVAL) {
            return true; // 需要节流
        }
        
        lastErrorTime.put(errorKey, currentTime);
        return false;
    }
    
    /**
     * 更新错误统计
     */
    private static void updateErrorStatistics(PluginException exception) {
        String errorCode = exception.getErrorCode();
        errorCounters.computeIfAbsent(errorCode, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * 获取错误统计信息
     */
    public static ErrorStatistics getErrorStatistics() {
        return new ErrorStatistics(new ConcurrentHashMap<>(errorCounters));
    }
    
    /**
     * 清理错误统计
     */
    public static void clearErrorStatistics() {
        errorCounters.clear();
        lastErrorTime.clear();
    }
    
    /**
     * 错误统计信息
     */
    public static class ErrorStatistics {
        private final ConcurrentMap<String, AtomicLong> errorCounters;
        
        public ErrorStatistics(ConcurrentMap<String, AtomicLong> errorCounters) {
            this.errorCounters = errorCounters;
        }
        
        /**
         * 获取特定错误代码的计数
         */
        public long getErrorCount(String errorCode) {
            AtomicLong counter = errorCounters.get(errorCode);
            return counter != null ? counter.get() : 0;
        }
        
        /**
         * 获取总错误数
         */
        public long getTotalErrorCount() {
            return errorCounters.values().stream()
                    .mapToLong(AtomicLong::get)
                    .sum();
        }
        
        /**
         * 获取不同错误类型的数量
         */
        public int getUniqueErrorTypes() {
            return errorCounters.size();
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ErrorStatistics{totalErrors=").append(getTotalErrorCount());
            sb.append(", uniqueTypes=").append(getUniqueErrorTypes());
            sb.append(", errors={");
            
            errorCounters.forEach((code, count) -> 
                sb.append(code).append("=").append(count.get()).append(", "));
            
            if (!errorCounters.isEmpty()) {
                sb.setLength(sb.length() - 2); // 移除最后的 ", "
            }
            
            sb.append("}}");
            return sb.toString();
        }
    }
}
