package org.example.liteworkspace.event;

import com.intellij.openapi.project.Project;
import org.example.liteworkspace.config.ConfigurationManager;
import org.example.liteworkspace.util.LogUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件总线
 * 
 * 主要功能：
 * 1. 事件发布和订阅
 * 2. 异步事件处理
 * 3. 事件监听器管理
 * 4. 事件过滤和路由
 * 5. 性能监控和统计
 */
public class EventBus {
    
    /**
     * 事件监听器接口
     */
    public interface EventListener<T extends Event> {
        /**
         * 处理事件
         */
        void handle(T event);
        
        /**
         * 获取监听器名称
         */
        default String getName() {
            return this.getClass().getSimpleName();
        }
        
        /**
         * 获取优先级（数值越小优先级越高）
         */
        default int getPriority() {
            return 100;
        }
        
        /**
         * 是否支持异步处理
         */
        default boolean supportAsync() {
            return true;
        }
    }
    
    /**
     * 事件过滤器接口
     */
    public interface EventFilter<T extends Event> {
        /**
         * 是否接受事件
         */
        boolean accept(T event);
    }
    
    /**
     * 事件统计信息
     */
    public static class EventStatistics {
        private final AtomicLong totalEventsPublished = new AtomicLong(0);
        private final AtomicLong totalEventsProcessed = new AtomicLong(0);
        private final AtomicLong totalEventsFailed = new AtomicLong(0);
        private final AtomicLong totalProcessingTime = new AtomicLong(0);
        private final Map<Class<? extends Event>, AtomicLong> eventCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> listenerCounts = new ConcurrentHashMap<>();
        
        public void recordEventPublished(Class<? extends Event> eventType) {
            totalEventsPublished.incrementAndGet();
            eventCounts.computeIfAbsent(eventType, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        public void recordEventProcessed(String listenerName, long processingTime) {
            totalEventsProcessed.incrementAndGet();
            totalProcessingTime.addAndGet(processingTime);
            listenerCounts.computeIfAbsent(listenerName, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        public void recordEventFailed() {
            totalEventsFailed.incrementAndGet();
        }
        
        public double getSuccessRate() {
            long total = totalEventsProcessed.get() + totalEventsFailed.get();
            return total == 0 ? 0.0 : (double) totalEventsProcessed.get() / total;
        }
        
        public double getAverageProcessingTime() {
            long processed = totalEventsProcessed.get();
            return processed == 0 ? 0.0 : (double) totalProcessingTime.get() / processed;
        }
        
        @Override
        public String toString() {
            return String.format(
                "EventStats{published=%d, processed=%d, failed=%d, successRate=%.2f%%, " +
                "avgTime=%.2fms, eventTypes=%d, listeners=%d}",
                totalEventsPublished.get(), totalEventsProcessed.get(), totalEventsFailed.get(),
                getSuccessRate() * 100, getAverageProcessingTime(),
                eventCounts.size(), listenerCounts.size()
            );
        }
        
        // Getters
        public long getTotalEventsPublished() { return totalEventsPublished.get(); }
        public long getTotalEventsProcessed() { return totalEventsProcessed.get(); }
        public long getTotalEventsFailed() { return totalEventsFailed.get(); }
        public Map<Class<? extends Event>, AtomicLong> getEventCounts() { 
            return new HashMap<>(eventCounts); 
        }
        public Map<String, AtomicLong> getListenerCounts() { 
            return new HashMap<>(listenerCounts); 
        }
    }
    
    /**
     * 监听器包装器
     */
    private static class ListenerWrapper<T extends Event> implements Comparable<ListenerWrapper<T>> {
        private final EventListener<T> listener;
        private final Class<T> eventType;
        private final EventFilter<T> filter;
        private final boolean async;
        
        public ListenerWrapper(EventListener<T> listener, Class<T> eventType, 
                             EventFilter<T> filter, boolean async) {
            this.listener = listener;
            this.eventType = eventType;
            this.filter = filter;
            this.async = async;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public int compareTo(ListenerWrapper<T> other) {
            return Integer.compare(this.listener.getPriority(), other.listener.getPriority());
        }
        
        public boolean canHandle(Event event) {
            if (!eventType.isInstance(event)) {
                return false;
            }
            if (filter != null) {
                return filter.accept((T) event);
            }
            return true;
        }
        
        @SuppressWarnings("unchecked")
        public void handle(Event event) {
            if (canHandle(event)) {
                listener.handle((T) event);
            }
        }
        
        // Getters
        public EventListener<T> getListener() { return listener; }
        public Class<T> getEventType() { return eventType; }
        public boolean isAsync() { return async; }
        public String getListenerName() { return listener.getName(); }
    }
    
    // 核心组件
    private final Project project;
    private final ConfigurationManager configManager;
    private final Map<Class<? extends Event>, List<ListenerWrapper<? extends Event>>> listeners;
    private final ExecutorService asyncExecutor;
    private final EventStatistics statistics;
    private volatile boolean isShutdown;
    
    public EventBus(Project project) {
        this.project = project;
        this.configManager = ConfigurationManager.getInstance(project);
        this.listeners = new ConcurrentHashMap<>();
        this.asyncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "EventBus-Async");
            t.setDaemon(true);
            return t;
        });
        this.statistics = new EventStatistics();
        this.isShutdown = false;
        
        LogUtil.info("EventBus 初始化完成");
    }
    
    /**
     * 注册事件监听器
     */
    public <T extends Event> void register(Class<T> eventType, EventListener<T> listener) {
        register(eventType, listener, null, true);
    }
    
    /**
     * 注册事件监听器（带过滤器）
     */
    public <T extends Event> void register(Class<T> eventType, EventListener<T> listener, 
                                         EventFilter<T> filter) {
        register(eventType, listener, filter, true);
    }
    
    /**
     * 注册事件监听器（完整参数）
     */
    public <T extends Event> void register(Class<T> eventType, EventListener<T> listener, 
                                         EventFilter<T> filter, boolean async) {
        if (isShutdown) {
            LogUtil.warn("EventBus 已关闭，无法注册监听器: {}", listener.getName());
            return;
        }
        
        ListenerWrapper<T> wrapper = new ListenerWrapper<>(listener, eventType, filter, async);
        
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(wrapper);
        
        // 按优先级排序
//        listeners.get(eventType).sort(Comparator.naturalOrder());
        
        LogUtil.debug("注册事件监听器: {} -> {}", eventType.getSimpleName(), listener.getName());
    }
    
    /**
     * 取消注册事件监听器
     */
    public <T extends Event> void unregister(Class<T> eventType, EventListener<T> listener) {
        List<ListenerWrapper<? extends Event>> listenerList = listeners.get(eventType);
        if (listenerList != null) {
            listenerList.removeIf(wrapper -> wrapper.getListener().equals(listener));
            if (listenerList.isEmpty()) {
                listeners.remove(eventType);
            }
            LogUtil.debug("取消注册事件监听器: {} -> {}", eventType.getSimpleName(), listener.getName());
        }
    }
    
    /**
     * 发布事件
     */
    public void publish(Event event) {
        if (isShutdown) {
            LogUtil.warn("EventBus 已关闭，无法发布事件: {}", event.getClass().getSimpleName());
            return;
        }
        
        statistics.recordEventPublished(event.getClass());
        
        List<ListenerWrapper<? extends Event>> listenerList = listeners.get(event.getClass());
        if (listenerList == null || listenerList.isEmpty()) {
            LogUtil.debug("没有监听器处理事件: {}", event.getClass().getSimpleName());
            return;
        }
        
        LogUtil.debug("发布事件: {} 到 {} 个监听器", event.getClass().getSimpleName(), listenerList.size());
        
        for (ListenerWrapper<? extends Event> wrapper : listenerList) {
            if (wrapper.canHandle(event)) {
                if (wrapper.isAsync() && wrapper.getListener().supportAsync()) {
                    publishAsync(wrapper, event);
                } else {
                    publishSync(wrapper, event);
                }
            }
        }
    }
    
    /**
     * 同步发布事件
     */
    private void publishSync(ListenerWrapper<? extends Event> wrapper, Event event) {
        long startTime = System.currentTimeMillis();
        try {
            wrapper.handle(event);
            recordListenerSuccess(wrapper.getListenerName(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            recordListenerFailure(wrapper.getListenerName(), e);
            LogUtil.error("同步事件处理失败: {}", e, wrapper.getListenerName(), e.getMessage());
        }
    }
    
    /**
     * 异步发布事件
     */
    private void publishAsync(ListenerWrapper<? extends Event> wrapper, Event event) {
        asyncExecutor.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                wrapper.handle(event);
                recordListenerSuccess(wrapper.getListenerName(), System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                recordListenerFailure(wrapper.getListenerName(), e);
                LogUtil.error("异步事件处理失败: {}", e, wrapper.getListenerName(), e.getMessage());
            }
        });
    }
    
    /**
     * 记录监听器处理成功
     */
    private void recordListenerSuccess(String listenerName, long processingTime) {
        statistics.recordEventProcessed(listenerName, processingTime);
    }
    
    /**
     * 记录监听器处理失败
     */
    private void recordListenerFailure(String listenerName, Exception e) {
        statistics.recordEventFailed();
    }
    
    /**
     * 获取事件类型监听器数量
     */
    public int getListenerCount(Class<? extends Event> eventType) {
        List<ListenerWrapper<? extends Event>> listenerList = listeners.get(eventType);
        return listenerList != null ? listenerList.size() : 0;
    }
    
    /**
     * 获取所有监听器数量
     */
    public int getTotalListenerCount() {
        return listeners.values().stream()
            .mapToInt(List::size)
            .sum();
    }
    
    /**
     * 获取统计信息
     */
    public EventStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 清空所有监听器
     */
    public void clear() {
        listeners.clear();
        LogUtil.info("已清空所有事件监听器");
    }
    
    /**
     * 清空指定事件类型的监听器
     */
    public void clear(Class<? extends Event> eventType) {
        listeners.remove(eventType);
        LogUtil.info("已清空事件类型的监听器: {}", eventType.getSimpleName());
    }
    
    /**
     * 关闭事件总线
     */
    public void shutdown() {
        isShutdown = true;
        
        LogUtil.info("正在关闭 EventBus...");
        
        // 清空监听器
        clear();
        
        // 关闭异步执行器
        asyncExecutor.shutdown();
        
        try {
            if (!asyncExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LogUtil.info("EventBus 已关闭");
    }
    
    /**
     * 检查是否已关闭
     */
    public boolean isShutdown() {
        return isShutdown;
    }
    
    /**
     * 获取项目实例
     */
    public Project getProject() {
        return project;
    }
}
