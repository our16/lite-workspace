package org.example.liteworkspace.event;

import com.intellij.openapi.project.Project;
import org.example.liteworkspace.util.LogUtil;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 标准事件监听器集合
 * 
 * 提供常用的事件监听器实现
 */
public class EventListeners {
    
    /**
     * 扫描事件监听器
     */
    public static class ScanEventListener implements EventBus.EventListener<PluginEvents.ScanStartedEvent> {
        private final AtomicLong scanCount = new AtomicLong(0);
        private final AtomicLong totalScanTime = new AtomicLong(0);
        
        @Override
        public void handle(PluginEvents.ScanStartedEvent event) {
            scanCount.incrementAndGet();
            LogUtil.info("开始扫描: {} (范围: {})", event.getScanType(), event.getScope());
        }
        
        @Override
        public String getName() {
            return "ScanEventListener";
        }
        
        @Override
        public int getPriority() {
            return 10; // 高优先级
        }
        
        public long getScanCount() {
            return scanCount.get();
        }
        
        public long getTotalScanTime() {
            return totalScanTime.get();
        }
    }
    
    /**
     * 扫描完成事件监听器
     */
    public static class ScanCompletedListener implements EventBus.EventListener<PluginEvents.ScanCompletedEvent> {
        private final AtomicLong completedCount = new AtomicLong(0);
        private final AtomicLong totalItemsFound = new AtomicLong(0);
        private final AtomicLong totalDuration = new AtomicLong(0);
        
        @Override
        public void handle(PluginEvents.ScanCompletedEvent event) {
            completedCount.incrementAndGet();
            totalItemsFound.addAndGet(event.getItemsFound());
            totalDuration.addAndGet(event.getDuration());
            
            LogUtil.info("扫描完成: {} (发现 {} 项，耗时 {}ms)", 
                event.getScanType(), event.getItemsFound(), event.getDuration());
        }
        
        @Override
        public String getName() {
            return "ScanCompletedListener";
        }
        
        @Override
        public int getPriority() {
            return 20;
        }
        
        public long getCompletedCount() {
            return completedCount.get();
        }
        
        public long getTotalItemsFound() {
            return totalItemsFound.get();
        }
        
        public double getAverageItemsFound() {
            long count = completedCount.get();
            return count == 0 ? 0.0 : (double) totalItemsFound.get() / count;
        }
        
        public double getAverageDuration() {
            long count = completedCount.get();
            return count == 0 ? 0.0 : (double) totalDuration.get() / count;
        }
    }
    
    /**
     * 缓存事件监听器
     */
    public static class CacheEventListener implements EventBus.EventListener<Event> {
        private final AtomicLong cacheHits = new AtomicLong(0);
        private final AtomicLong cacheMisses = new AtomicLong(0);
        private final AtomicLong cacheCleared = new AtomicLong(0);
        
        @Override
        public void handle(Event event) {
            if (event instanceof PluginEvents.CacheHitEvent) {
                cacheHits.incrementAndGet();
                PluginEvents.CacheHitEvent cacheEvent = (PluginEvents.CacheHitEvent) event;
                LogUtil.debug("缓存命中: {} [{}]", cacheEvent.getCacheKey(), cacheEvent.getCacheType());
                
            } else if (event instanceof PluginEvents.CacheMissEvent) {
                cacheMisses.incrementAndGet();
                PluginEvents.CacheMissEvent cacheEvent = (PluginEvents.CacheMissEvent) event;
                LogUtil.debug("缓存未命中: {} [{}]", cacheEvent.getCacheKey(), cacheEvent.getCacheType());
                
            } else if (event instanceof PluginEvents.CacheClearedEvent) {
                cacheCleared.incrementAndGet();
                PluginEvents.CacheClearedEvent cacheEvent = (PluginEvents.CacheClearedEvent) event;
                LogUtil.info("缓存清理: {} (清理 {} 项)", cacheEvent.getCacheType(), cacheEvent.getClearedCount());
            }
        }
        
        @Override
        public String getName() {
            return "CacheEventListener";
        }
        
        @Override
        public int getPriority() {
            return 30;
        }
        
        public double getCacheHitRate() {
            long total = cacheHits.get() + cacheMisses.get();
            return total == 0 ? 0.0 : (double) cacheHits.get() / total;
        }
        
        public long getCacheHits() {
            return cacheHits.get();
        }
        
        public long getCacheMisses() {
            return cacheMisses.get();
        }
        
        public long getCacheCleared() {
            return cacheCleared.get();
        }
    }
    
    /**
     * 任务事件监听器
     */
    public static class TaskEventListener implements EventBus.EventListener<Event> {
        private final AtomicLong tasksSubmitted = new AtomicLong(0);
        private final AtomicLong tasksCompleted = new AtomicLong(0);
        private final AtomicLong totalTaskTime = new AtomicLong(0);
        private final AtomicLong successfulTasks = new AtomicLong(0);
        
        @Override
        public void handle(Event event) {
            if (event instanceof PluginEvents.TaskSubmittedEvent) {
                tasksSubmitted.incrementAndGet();
                PluginEvents.TaskSubmittedEvent taskEvent = (PluginEvents.TaskSubmittedEvent) event;
                LogUtil.debug("任务提交: {} [{}]", taskEvent.getTaskName(), taskEvent.getTaskType());
                
            } else if (event instanceof PluginEvents.TaskCompletedEvent) {
                tasksCompleted.incrementAndGet();
                PluginEvents.TaskCompletedEvent taskEvent = (PluginEvents.TaskCompletedEvent) event;
                totalTaskTime.addAndGet(taskEvent.getDuration());
                
                if (taskEvent.isSuccess()) {
                    successfulTasks.incrementAndGet();
                }
                
                LogUtil.info("任务完成: {} ({}, 耗时 {}ms)", 
                    taskEvent.getTaskName(), taskEvent.isSuccess() ? "成功" : "失败", taskEvent.getDuration());
            }
        }
        
        @Override
        public String getName() {
            return "TaskEventListener";
        }
        
        @Override
        public int getPriority() {
            return 15;
        }
        
        public long getTasksSubmitted() {
            return tasksSubmitted.get();
        }
        
        public long getTasksCompleted() {
            return tasksCompleted.get();
        }
        
        public double getTaskSuccessRate() {
            long completed = tasksCompleted.get();
            return completed == 0 ? 0.0 : (double) successfulTasks.get() / completed;
        }
        
        public double getAverageTaskTime() {
            long completed = tasksCompleted.get();
            return completed == 0 ? 0.0 : (double) totalTaskTime.get() / completed;
        }
    }
    
    /**
     * 配置变更事件监听器
     */
    public static class ConfigurationChangeListener implements EventBus.EventListener<PluginEvents.ConfigurationChangedEvent> {
        private final AtomicLong changeCount = new AtomicLong(0);
        
        @Override
        public void handle(PluginEvents.ConfigurationChangedEvent event) {
            changeCount.incrementAndGet();
            LogUtil.info("配置变更: {} = {} (原值: {})", 
                event.getConfigKey(), event.getNewValue(), event.getOldValue());
        }
        
        @Override
        public String getName() {
            return "ConfigurationChangeListener";
        }
        
        @Override
        public int getPriority() {
            return 5; // 最高优先级
        }
        
        public long getChangeCount() {
            return changeCount.get();
        }
    }
    
    /**
     * 系统事件监听器
     */
    public static class SystemEventListener implements EventBus.EventListener<Event> {
        private volatile long systemStartTime = 0;
        private volatile String shutdownReason = null;
        
        @Override
        public void handle(Event event) {
            if (event instanceof PluginEvents.SystemStartedEvent) {
                systemStartTime = System.currentTimeMillis();
                PluginEvents.SystemStartedEvent sysEvent = (PluginEvents.SystemStartedEvent) event;
                LogUtil.info("系统启动完成 (耗时 {}ms)", sysEvent.getStartupTime());
                
            } else if (event instanceof PluginEvents.SystemShutdownEvent) {
                shutdownReason = ((PluginEvents.SystemShutdownEvent) event).getReason();
                LogUtil.info("系统关闭: {}", shutdownReason);
            }
        }
        
        @Override
        public String getName() {
            return "SystemEventListener";
        }
        
        @Override
        public int getPriority() {
            return 1; // 最高优先级
        }
        
        public long getSystemStartTime() {
            return systemStartTime;
        }
        
        public String getShutdownReason() {
            return shutdownReason;
        }
        
        public boolean isSystemRunning() {
            return systemStartTime > 0 && shutdownReason == null;
        }
    }
    
    /**
     * 性能监控事件监听器
     */
    public static class PerformanceMonitorListener implements EventBus.EventListener<PluginEvents.PerformanceWarningEvent> {
        private final AtomicLong warningCount = new AtomicLong(0);
        
        @Override
        public void handle(PluginEvents.PerformanceWarningEvent event) {
            warningCount.incrementAndGet();
            LogUtil.warn("性能警告: {} = {:.2f} (阈值: {:.2f})", 
                event.getMetric(), event.getValue(), event.getThreshold());
        }
        
        @Override
        public String getName() {
            return "PerformanceMonitorListener";
        }
        
        @Override
        public int getPriority() {
            return 50;
        }
        
        public long getWarningCount() {
            return warningCount.get();
        }
    }
    
    /**
     * 错误事件监听器
     */
    public static class ErrorEventListener implements EventBus.EventListener<PluginEvents.ErrorEvent> {
        private final AtomicLong errorCount = new AtomicLong(0);
        private final AtomicLong criticalErrorCount = new AtomicLong(0);
        
        @Override
        public void handle(PluginEvents.ErrorEvent event) {
            errorCount.incrementAndGet();
            
            if (event.isCritical()) {
                criticalErrorCount.incrementAndGet();
                LogUtil.error("关键错误: {} - {} [{}]", 
                    event.getCause(), event.getErrorCode(), event.getErrorMessage(), event.getContext());
            } else {
                LogUtil.warn("错误: {} - {} [{}]", 
                    event.getErrorCode(), event.getErrorMessage(), event.getContext());
            }
        }
        
        @Override
        public String getName() {
            return "ErrorEventListener";
        }
        
        @Override
        public int getPriority() {
            return 2; // 高优先级
        }
        
        public long getErrorCount() {
            return errorCount.get();
        }
        
        public long getCriticalErrorCount() {
            return criticalErrorCount.get();
        }
    }
    
    /**
     * UI事件监听器
     */
    public static class UIEventListener implements EventBus.EventListener<PluginEvents.UIUpdateEvent> {
        private final AtomicLong updateCount = new AtomicLong(0);
        
        @Override
        public void handle(PluginEvents.UIUpdateEvent event) {
            updateCount.incrementAndGet();
            LogUtil.debug("UI更新: {} - {}", event.getComponent(), event.getAction());
        }
        
        @Override
        public String getName() {
            return "UIEventListener";
        }
        
        @Override
        public int getPriority() {
            return 80;
        }
        
        @Override
        public boolean supportAsync() {
            return true; // UI更新可以异步处理
        }
        
        public long getUpdateCount() {
            return updateCount.get();
        }
    }
    
    /**
     * 通知事件监听器
     */
    public static class NotificationEventListener implements EventBus.EventListener<PluginEvents.NotificationEvent> {
        private final AtomicLong notificationCount = new AtomicLong(0);
        
        @Override
        public void handle(PluginEvents.NotificationEvent event) {
            notificationCount.incrementAndGet();
            LogUtil.info("通知: {} - {} [{}]", event.getTitle(), event.getMessage(), event.getType());
        }
        
        @Override
        public String getName() {
            return "NotificationEventListener";
        }
        
        @Override
        public int getPriority() {
            return 70;
        }
        
        @Override
        public boolean supportAsync() {
            return true; // 通知可以异步处理
        }
        
        public long getNotificationCount() {
            return notificationCount.get();
        }
    }
}
