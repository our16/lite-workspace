package org.example.liteworkspace.notification;

import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.JBUI;
import org.example.liteworkspace.event.EventBus;
import org.example.liteworkspace.event.Event;
import org.example.liteworkspace.event.PluginEvents;
import org.example.liteworkspace.util.OptimizedLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通知管理器
 * 
 * 主要功能：
 * 1. 分级通知机制
 * 2. 快速操作按钮
 * 3. 通知样式和时机优化
 * 4. 通知历史记录
 * 5. 通知规则配置
 */
public class NotificationManager implements EventBus.EventListener<PluginEvents.NotificationEvent> {
    
    /**
     * 通知级别
     */
    public enum NotificationLevel {
        INFO("信息", NotificationType.INFORMATION, new Color(33, 150, 243)),
        SUCCESS("成功", NotificationType.INFORMATION, new Color(76, 175, 80)),
        WARNING("警告", NotificationType.WARNING, new Color(255, 152, 0)),
        ERROR("错误", NotificationType.ERROR, new Color(244, 67, 54)),
        CRITICAL("严重", NotificationType.ERROR, new Color(211, 47, 47));
        
        private final String displayName;
        private final NotificationType notificationType;
        private final Color color;
        
        NotificationLevel(String displayName, NotificationType notificationType, Color color) {
            this.displayName = displayName;
            this.notificationType = notificationType;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public NotificationType getNotificationType() { return notificationType; }
        public Color getColor() { return color; }
    }
    
    /**
     * 通知规则
     */
    public static class NotificationRule {
        private final String eventType;
        private final NotificationLevel level;
        private final boolean enabled;
        private final boolean showBalloon;
        private final boolean logToFile;
        private final boolean playSound;
        
        public NotificationRule(String eventType, NotificationLevel level, boolean enabled, 
                              boolean showBalloon, boolean logToFile, boolean playSound) {
            this.eventType = eventType;
            this.level = level;
            this.enabled = enabled;
            this.showBalloon = showBalloon;
            this.logToFile = logToFile;
            this.playSound = playSound;
        }
        
        // Getters
        public String getEventType() { return eventType; }
        public NotificationLevel getLevel() { return level; }
        public boolean isEnabled() { return enabled; }
        public boolean shouldShowBalloon() { return showBalloon; }
        public boolean shouldLogToFile() { return logToFile; }
        public boolean shouldPlaySound() { return playSound; }
    }
    
    /**
     * 通知操作
     */
    public static class NotificationAction {
        private final String name;
        private final Runnable action;
        private final String description;
        
        public NotificationAction(String name, Runnable action, String description) {
            this.name = name;
            this.action = action;
            this.description = description;
        }
        
        public String getName() { return name; }
        public Runnable getAction() { return action; }
        public String getDescription() { return description; }
    }
    
    /**
     * 通知历史记录
     */
    public static class NotificationHistory {
        private final long timestamp;
        private final String title;
        private final String message;
        private final NotificationLevel level;
        private final String eventType;
        private final boolean wasRead;
        
        public NotificationHistory(long timestamp, String title, String message, 
                                 NotificationLevel level, String eventType, boolean wasRead) {
            this.timestamp = timestamp;
            this.title = title;
            this.message = message;
            this.level = level;
            this.eventType = eventType;
            this.wasRead = wasRead;
        }
        
        // Getters
        public long getTimestamp() { return timestamp; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public NotificationLevel getLevel() { return level; }
        public String getEventType() { return eventType; }
        public boolean wasRead() { return wasRead; }
    }
    
    // 单例实例
    private static volatile NotificationManager instance;
    
    // 核心组件
    private final NotificationGroup notificationGroup;
    private final EventBus eventBus;
    private final AtomicLong notificationCounter;
    
    // 配置和状态
    private final ConcurrentHashMap<String, NotificationRule> notificationRules;
    private final ConcurrentHashMap<String, NotificationHistory> notificationHistory;
    private volatile boolean enableNotifications = true;
    private volatile boolean enableSounds = true;
    private volatile boolean enableBalloon = true;
    private volatile int maxHistorySize = 100;
    
    private NotificationManager() {
        this.notificationGroup = new NotificationGroup(
            "LiteWorkspace Notifications",
            NotificationDisplayType.BALLOON,
            true
        );
        this.eventBus = new EventBus(null);
        this.notificationCounter = new AtomicLong(0);
        this.notificationRules = new ConcurrentHashMap<>();
        this.notificationHistory = new ConcurrentHashMap<>();
        
        initializeDefaultRules();
        registerEventListener();
    }
    
    /**
     * 获取单例实例
     */
    public static NotificationManager getInstance() {
        if (instance == null) {
            synchronized (NotificationManager.class) {
                if (instance == null) {
                    instance = new NotificationManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 初始化默认通知规则
     */
    private void initializeDefaultRules() {
        // 扫描相关通知
        addNotificationRule(new NotificationRule(
            "SCAN_STARTED", NotificationLevel.INFO, true, true, false, false
        ));
        addNotificationRule(new NotificationRule(
            "SCAN_COMPLETED", NotificationLevel.SUCCESS, true, true, false, false
        ));
        addNotificationRule(new NotificationRule(
            "SCAN_FAILED", NotificationLevel.ERROR, true, true, true, true
        ));
        
        // 缓存相关通知
        addNotificationRule(new NotificationRule(
            "CACHE_HIT", NotificationLevel.INFO, false, false, false, false
        ));
        addNotificationRule(new NotificationRule(
            "CACHE_MISS", NotificationLevel.WARNING, false, false, false, false
        ));
        addNotificationRule(new NotificationRule(
            "CACHE_CLEARED", NotificationLevel.INFO, true, true, false, false
        ));
        
        // 配置相关通知
        addNotificationRule(new NotificationRule(
            "CONFIG_CHANGED", NotificationLevel.INFO, true, true, false, false
        ));
        addNotificationRule(new NotificationRule(
            "CONFIG_ERROR", NotificationLevel.ERROR, true, true, true, true
        ));
        
        // 系统相关通知
        addNotificationRule(new NotificationRule(
            "SYSTEM_STARTED", NotificationLevel.SUCCESS, true, true, false, false
        ));
        addNotificationRule(new NotificationRule(
            "SYSTEM_ERROR", NotificationLevel.CRITICAL, true, true, true, true
        ));
        addNotificationRule(new NotificationRule(
            "PERFORMANCE_WARNING", NotificationLevel.WARNING, true, true, false, false
        ));
        
        // 资源相关通知
        addNotificationRule(new NotificationRule(
            "RESOURCE_LEAK", NotificationLevel.ERROR, true, true, true, true
        ));
        addNotificationRule(new NotificationRule(
            "MEMORY_PRESSURE", NotificationLevel.WARNING, true, true, false, false
        ));
    }
    
    /**
     * 注册事件监听器
     */
    private void registerEventListener() {
        eventBus.register(PluginEvents.NotificationEvent.class, this);
    }
    
    /**
     * 处理通知事件
     */
    @Override
    public void handle(PluginEvents.NotificationEvent event) {
        if (!enableNotifications) {
            return;
        }
        
        NotificationRule rule = notificationRules.get(event.getType());
        if (rule == null || !rule.isEnabled()) {
            return;
        }
        
        showNotification(
            event.getTitle(),
            event.getMessage(),
            rule.getLevel(),
            event.getType(),
            null
        );
    }
    
    /**
     * 显示通知
     */
    public void showNotification(@NotNull String title, @NotNull String message, 
                               @NotNull NotificationLevel level, @Nullable String eventType,
                               @Nullable NotificationAction[] actions) {
        if (!enableNotifications) {
            return;
        }
        
        try {
            // 检查通知规则
            NotificationRule rule = eventType != null ? notificationRules.get(eventType) : null;
            if (rule != null && !rule.isEnabled()) {
                return;
            }
            
            // 创建通知
            Notification notification = notificationGroup.createNotification(
                title,
                message,
                level.getNotificationType(),
                null
            );
            
            // 添加操作按钮
            if (actions != null && actions.length > 0) {
                for (NotificationAction action : actions) {
                    notification.addAction(new AnAction(action.getName()) {
                        @Override
                        public void actionPerformed(@NotNull AnActionEvent e) {
                            try {
                                action.getAction().run();
                            } catch (Exception ex) {
                                OptimizedLogUtil.error("执行通知操作失败: " + action.getName(), ex);
                            }
                        }
                    });
                }
            }
            
            // 添加默认操作
            addDefaultActions(notification, level, eventType);
            
            // 显示通知
            if (enableBalloon && (rule == null || rule.shouldShowBalloon())) {
                notification.notify(null);
            } else {
                // 只记录到日志，不显示弹窗
                OptimizedLogUtil.info("通知: " + title + " - " + message);
            }
            
            // 播放声音
            if (enableSounds && (rule == null || rule.shouldPlaySound())) {
                playNotificationSound(level);
            }
            
            // 记录历史
            recordNotificationHistory(title, message, level, eventType);
            
            // 记录到文件
            if (rule != null && rule.shouldLogToFile()) {
                logNotificationToFile(title, message, level, eventType);
            }
            
        } catch (Exception e) {
            OptimizedLogUtil.error("显示通知失败", e);
        }
    }
    
    /**
     * 添加默认操作
     */
    private void addDefaultActions(@NotNull Notification notification, @NotNull NotificationLevel level, 
                                  @Nullable String eventType) {
        // 添加"查看详情"操作
        notification.addAction(new AnAction("查看详情") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                showNotificationDetails(notification, eventType);
            }
        });
        
        // 根据级别添加特定操作
        if (level == NotificationLevel.ERROR || level == NotificationLevel.CRITICAL) {
            notification.addAction(new AnAction("报告问题") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    reportIssue(notification, eventType);
                }
            });
        }
        
        // 添加"关闭"操作
        notification.addAction(new AnAction("关闭") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                notification.expire();
            }
        });
    }
    
    /**
     * 显示通知详情
     */
    private void showNotificationDetails(@NotNull Notification notification, @Nullable String eventType) {
        StringBuilder details = new StringBuilder();
        details.append("通知详情\n");
        details.append("========\n\n");
        details.append("标题: ").append(notification.getTitle()).append("\n");
        details.append("内容: ").append(notification.getContent()).append("\n");
        details.append("类型: ").append(eventType != null ? eventType : "未知").append("\n");
        details.append("时间: ").append(new java.util.Date()).append("\n");
        details.append("级别: ").append(getNotificationLevel(notification).getDisplayName()).append("\n");
        
        Messages.showInfoMessage(details.toString(), "通知详情");
    }
    
    /**
     * 报告问题
     */
    private void reportIssue(@NotNull Notification notification, @Nullable String eventType) {
        StringBuilder issueReport = new StringBuilder();
        issueReport.append("问题报告\n");
        issueReport.append("========\n\n");
        issueReport.append("通知标题: ").append(notification.getTitle()).append("\n");
        issueReport.append("通知内容: ").append(notification.getContent()).append("\n");
        issueReport.append("事件类型: ").append(eventType != null ? eventType : "未知").append("\n");
        issueReport.append("时间: ").append(new java.util.Date()).append("\n");
        issueReport.append("级别: ").append(getNotificationLevel(notification).getDisplayName()).append("\n\n");
        issueReport.append("系统信息:\n");
        issueReport.append("Java版本: ").append(System.getProperty("java.version")).append("\n");
        issueReport.append("操作系统: ").append(System.getProperty("os.name")).append("\n");
        
        // 这里可以集成到问题跟踪系统
        Messages.showInfoMessage("问题报告已生成，请联系技术支持。\n\n" + issueReport.toString(), "问题报告");
    }
    
    /**
     * 获取通知级别
     */
    private NotificationLevel getNotificationLevel(@NotNull Notification notification) {
        // 根据通知类型推断级别
        if (notification.getType() == NotificationType.ERROR) {
            return NotificationLevel.ERROR;
        } else if (notification.getType() == NotificationType.WARNING) {
            return NotificationLevel.WARNING;
        } else {
            return NotificationLevel.INFO;
        }
    }
    
    /**
     * 播放通知声音
     */
    private void playNotificationSound(@NotNull NotificationLevel level) {
        try {
            // 使用系统默认声音
            Toolkit.getDefaultToolkit().beep();
        } catch (Exception e) {
            OptimizedLogUtil.debug("播放通知声音失败", e);
        }
    }
    
    /**
     * 记录通知历史
     */
    private void recordNotificationHistory(@NotNull String title, @NotNull String message, 
                                          @NotNull NotificationLevel level, @Nullable String eventType) {
        long id = notificationCounter.incrementAndGet();
        NotificationHistory history = new NotificationHistory(
            System.currentTimeMillis(),
            title,
            message,
            level,
            eventType != null ? eventType : "UNKNOWN",
            false
        );
        
        notificationHistory.put(String.valueOf(id), history);
        
        // 限制历史记录大小
        if (notificationHistory.size() > maxHistorySize) {
            // 移除最旧的记录
            String oldestKey = notificationHistory.keySet().stream()
                .min(String::compareTo)
                .orElse(null);
            if (oldestKey != null) {
                notificationHistory.remove(oldestKey);
            }
        }
    }
    
    /**
     * 记录通知到文件
     */
    private void logNotificationToFile(@NotNull String title, @NotNull String message, 
                                     @NotNull NotificationLevel level, @Nullable String eventType) {
        try {
            String logEntry = String.format("[%s] [%s] [%s] %s - %s%n",
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()),
                level.getDisplayName(),
                eventType != null ? eventType : "UNKNOWN",
                title,
                message
            );
            
            // 这里可以实现文件日志记录
            OptimizedLogUtil.info("通知日志: " + logEntry.trim());
            
        } catch (Exception e) {
            OptimizedLogUtil.error("记录通知到文件失败", e);
        }
    }
    
    /**
     * 添加通知规则
     */
    public void addNotificationRule(@NotNull NotificationRule rule) {
        notificationRules.put(rule.getEventType(), rule);
        OptimizedLogUtil.debug("添加通知规则: " + rule.getEventType());
    }
    
    /**
     * 移除通知规则
     */
    public void removeNotificationRule(@NotNull String eventType) {
        notificationRules.remove(eventType);
        OptimizedLogUtil.debug("移除通知规则: " + eventType);
    }
    
    /**
     * 获取通知规则
     */
    @Nullable
    public NotificationRule getNotificationRule(@NotNull String eventType) {
        return notificationRules.get(eventType);
    }
    
    /**
     * 获取所有通知规则
     */
    public java.util.Collection<NotificationRule> getAllNotificationRules() {
        return new java.util.ArrayList<>(notificationRules.values());
    }
    
    /**
     * 获取通知历史
     */
    public java.util.Collection<NotificationHistory> getNotificationHistory() {
        return new java.util.ArrayList<>(notificationHistory.values());
    }
    
    /**
     * 清除通知历史
     */
    public void clearNotificationHistory() {
        notificationHistory.clear();
        OptimizedLogUtil.info("通知历史已清除");
    }
    
    /**
     * 便捷方法：显示信息通知
     */
    public void showInfo(@NotNull String title, @NotNull String message) {
        showNotification(title, message, NotificationLevel.INFO, null, null);
    }
    
    /**
     * 便捷方法：显示成功通知
     */
    public void showSuccess(@NotNull String title, @NotNull String message) {
        showNotification(title, message, NotificationLevel.SUCCESS, null, null);
    }
    
    /**
     * 便捷方法：显示警告通知
     */
    public void showWarning(@NotNull String title, @NotNull String message) {
        showNotification(title, message, NotificationLevel.WARNING, null, null);
    }
    
    /**
     * 便捷方法：显示错误通知
     */
    public void showError(@NotNull String title, @NotNull String message) {
        showNotification(title, message, NotificationLevel.ERROR, null, null);
    }
    
    /**
     * 便捷方法：显示严重错误通知
     */
    public void showCritical(@NotNull String title, @NotNull String message) {
        showNotification(title, message, NotificationLevel.CRITICAL, null, null);
    }
    
    /**
     * 便捷方法：显示带操作的通知
     */
    public void showNotificationWithActions(@NotNull String title, @NotNull String message, 
                                          @NotNull NotificationLevel level, @Nullable String eventType,
                                          @NotNull NotificationAction... actions) {
        showNotification(title, message, level, eventType, actions);
    }
    
    /**
     * 发布通知事件
     */
    public void publishNotificationEvent(@NotNull String title, @NotNull String message, 
                                       @NotNull String type) {
        eventBus.publish(new PluginEvents.NotificationEvent(
            "NotificationManager", title, message, type
        ));
    }
    
    // 配置方法
    public boolean isEnableNotifications() { return enableNotifications; }
    public void setEnableNotifications(boolean enableNotifications) { 
        this.enableNotifications = enableNotifications; 
    }
    
    public boolean isEnableSounds() { return enableSounds; }
    public void setEnableSounds(boolean enableSounds) { 
        this.enableSounds = enableSounds; 
    }
    
    public boolean isEnableBalloon() { return enableBalloon; }
    public void setEnableBalloon(boolean enableBalloon) { 
        this.enableBalloon = enableBalloon; 
    }
    
    public int getMaxHistorySize() { return maxHistorySize; }
    public void setMaxHistorySize(int maxHistorySize) { 
        this.maxHistorySize = maxHistorySize; 
    }
}
