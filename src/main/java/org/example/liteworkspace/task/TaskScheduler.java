package org.example.liteworkspace.task;

import com.intellij.openapi.project.Project;
import org.example.liteworkspace.config.ConfigurationManager;
import org.example.liteworkspace.exception.TaskExecutionException;
import org.example.liteworkspace.util.LogUtil;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 任务调度器
 * 
 * 主要功能：
 * 1. 任务调度和执行管理
 * 2. 任务状态监控
 * 3. 任务优先级处理
 * 4. 任务结果缓存
 * 5. 异常处理和重试机制
 */
public class TaskScheduler {
    
    /**
     * 任务优先级
     */
    public enum TaskPriority {
        CRITICAL(1),    // 关键任务
        HIGH(2),        // 高优先级
        NORMAL(3),      // 普通优先级
        LOW(4),         // 低优先级
        BACKGROUND(5);  // 后台任务
        
        private final int value;
        
        TaskPriority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    /**
     * 任务状态
     */
    public enum TaskStatus {
        PENDING,        // 等待执行
        RUNNING,        // 正在执行
        COMPLETED,      // 已完成
        FAILED,         // 执行失败
        CANCELLED,      // 已取消
        RETRYING        // 重试中
    }
    
    /**
     * 任务包装器
     */
    private static class TaskWrapper implements Comparable<TaskWrapper> {
        private final String taskId;
        private final Task task;
        private final TaskPriority priority;
        private final long submitTime;
        private final AtomicInteger retryCount;
        private volatile TaskStatus status;
        private volatile long startTime;
        private volatile long endTime;
        private volatile Throwable lastError;
        private final CompletableFuture<TaskResult> future;
        
        public TaskWrapper(String taskId, Task task, TaskPriority priority) {
            this.taskId = taskId;
            this.task = task;
            this.priority = priority;
            this.submitTime = System.currentTimeMillis();
            this.retryCount = new AtomicInteger(0);
            this.status = TaskStatus.PENDING;
            this.future = new CompletableFuture<>();
        }
        
        @Override
        public int compareTo(TaskWrapper other) {
            // 首先按优先级排序
            int priorityCompare = Integer.compare(this.priority.getValue(), other.priority.getValue());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // 相同优先级按提交时间排序
            return Long.compare(this.submitTime, other.submitTime);
        }
        
        public void updateStatus(TaskStatus newStatus) {
            this.status = newStatus;
            switch (newStatus) {
                case RUNNING:
                    this.startTime = System.currentTimeMillis();
                    break;
                case COMPLETED:
                case FAILED:
                case CANCELLED:
                    this.endTime = System.currentTimeMillis();
                    break;
            }
        }
        
        // Getters
        public String getTaskId() { return taskId; }
        public Task getTask() { return task; }
        public TaskPriority getPriority() { return priority; }
        public TaskStatus getStatus() { return status; }
        public long getSubmitTime() { return submitTime; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public long getExecutionTime() { 
            return endTime > startTime ? endTime - startTime : 
                   startTime > 0 ? System.currentTimeMillis() - startTime : 0; 
        }
        public int getRetryCount() { return retryCount.get(); }
        public Throwable getLastError() { return lastError; }
        public CompletableFuture<TaskResult> getFuture() { return future; }
        
        public void incrementRetryCount() { retryCount.incrementAndGet(); }
        public void setLastError(Throwable error) { this.lastError = error; }
    }
    
    /**
     * 调度器统计信息
     */
    public static class SchedulerStatistics {
        private final AtomicLong totalTasksSubmitted = new AtomicLong(0);
        private final AtomicLong totalTasksCompleted = new AtomicLong(0);
        private final AtomicLong totalTasksFailed = new AtomicLong(0);
        private final AtomicLong totalTasksCancelled = new AtomicLong(0);
        private final AtomicLong totalExecutionTime = new AtomicLong(0);
        private final AtomicInteger currentRunningTasks = new AtomicInteger(0);
        private final AtomicInteger currentPendingTasks = new AtomicInteger(0);
        private final Map<TaskPriority, AtomicLong> tasksByPriority = new ConcurrentHashMap<>();
        private final Map<TaskStatus, AtomicLong> tasksByStatus = new ConcurrentHashMap<>();
        
        public SchedulerStatistics() {
            for (TaskPriority priority : TaskPriority.values()) {
                tasksByPriority.put(priority, new AtomicLong(0));
            }
            for (TaskStatus status : TaskStatus.values()) {
                tasksByStatus.put(status, new AtomicLong(0));
            }
        }
        
        public void recordTaskSubmitted(TaskPriority priority) {
            totalTasksSubmitted.incrementAndGet();
            tasksByPriority.get(priority).incrementAndGet();
            tasksByStatus.get(TaskStatus.PENDING).incrementAndGet();
            currentPendingTasks.incrementAndGet();
        }
        
        public void recordTaskStarted(TaskPriority priority, TaskStatus oldStatus) {
            tasksByStatus.get(oldStatus).decrementAndGet();
            tasksByStatus.get(TaskStatus.RUNNING).incrementAndGet();
            currentPendingTasks.decrementAndGet();
            currentRunningTasks.incrementAndGet();
        }
        
        public void recordTaskCompleted(TaskPriority priority, TaskStatus oldStatus, long executionTime) {
            totalTasksCompleted.incrementAndGet();
            totalExecutionTime.addAndGet(executionTime);
            tasksByStatus.get(oldStatus).decrementAndGet();
            tasksByStatus.get(TaskStatus.COMPLETED).incrementAndGet();
            currentRunningTasks.decrementAndGet();
        }
        
        public void recordTaskFailed(TaskPriority priority, TaskStatus oldStatus, long executionTime) {
            totalTasksFailed.incrementAndGet();
            totalExecutionTime.addAndGet(executionTime);
            tasksByStatus.get(oldStatus).decrementAndGet();
            tasksByStatus.get(TaskStatus.FAILED).incrementAndGet();
            currentRunningTasks.decrementAndGet();
        }
        
        public void recordTaskCancelled(TaskPriority priority, TaskStatus oldStatus) {
            totalTasksCancelled.incrementAndGet();
            tasksByStatus.get(oldStatus).decrementAndGet();
            tasksByStatus.get(TaskStatus.CANCELLED).incrementAndGet();
            currentPendingTasks.decrementAndGet();
        }
        
        public double getSuccessRate() {
            long total = totalTasksCompleted.get() + totalTasksFailed.get() + totalTasksCancelled.get();
            return total == 0 ? 0.0 : (double) totalTasksCompleted.get() / total;
        }
        
        public double getAverageExecutionTime() {
            long completed = totalTasksCompleted.get() + totalTasksFailed.get();
            return completed == 0 ? 0.0 : (double) totalExecutionTime.get() / completed;
        }
        
        @Override
        public String toString() {
            return String.format(
                "SchedulerStats{submitted=%d, completed=%d, failed=%d, cancelled=%d, " +
                "successRate=%.2f%%, avgTime=%.2fms, running=%d, pending=%d}",
                totalTasksSubmitted.get(), totalTasksCompleted.get(), totalTasksFailed.get(),
                totalTasksCancelled.get(), getSuccessRate() * 100, getAverageExecutionTime(),
                currentRunningTasks.get(), currentPendingTasks.get()
            );
        }
        
        // Getters
        public long getTotalTasksSubmitted() { return totalTasksSubmitted.get(); }
        public long getTotalTasksCompleted() { return totalTasksCompleted.get(); }
        public long getTotalTasksFailed() { return totalTasksFailed.get(); }
        public long getCurrentRunningTasks() { return currentRunningTasks.get(); }
        public long getCurrentPendingTasks() { return currentPendingTasks.get(); }
    }
    
    // 核心组件
    private final Project project;
    private final ConfigurationManager configManager;
    private final PriorityBlockingQueue<TaskWrapper> taskQueue;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    private final Map<String, TaskWrapper> activeTasks;
    private final SchedulerStatistics statistics;
    private final AtomicInteger taskIdGenerator;
    private volatile boolean isShutdown;
    
    // 配置参数
    private final int maxConcurrentTasks;
    private final int maxRetryAttempts;
    private final long retryDelay;
    
    public TaskScheduler(Project project) {
        this.project = project;
        this.configManager = ConfigurationManager.getInstance(project);
        this.taskQueue = new PriorityBlockingQueue<>();
        this.activeTasks = new ConcurrentHashMap<>();
        this.statistics = new SchedulerStatistics();
        this.taskIdGenerator = new AtomicInteger(0);
        this.isShutdown = false;
        
        // 配置参数
        this.maxConcurrentTasks = configManager.getMaxConcurrentTasks();
        this.maxRetryAttempts = configManager.getMaxRetryAttempts();
        this.retryDelay = configManager.getRetryDelay();
        
        // 创建线程池
        this.executorService = Executors.newFixedThreadPool(
            maxConcurrentTasks,
            r -> {
                Thread t = new Thread(r, "TaskScheduler-Worker");
                t.setDaemon(true);
                return t;
            }
        );
        
        this.scheduledExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "TaskScheduler-Monitor");
            t.setDaemon(true);
            return t;
        });
        
        // 启动任务处理器
        startTaskProcessor();
        
        // 启动监控任务
        startMonitoringTask();
        
        LogUtil.info("TaskScheduler 初始化完成，最大并发任务数: " + maxConcurrentTasks);
    }
    
    /**
     * 启动任务处理器
     */
    private void startTaskProcessor() {
        for (int i = 0; i < maxConcurrentTasks; i++) {
            executorService.submit(this::processTasks);
        }
    }
    
    /**
     * 任务处理循环
     */
    private void processTasks() {
        while (!isShutdown && !Thread.currentThread().isInterrupted()) {
            try {
                TaskWrapper taskWrapper = taskQueue.take();
                if (taskWrapper.getStatus() == TaskStatus.CANCELLED) {
                    continue;
                }
                
                executeTask(taskWrapper);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LogUtil.error("任务处理器异常", e);
            }
        }
    }
    
    /**
     * 执行单个任务
     */
    private void executeTask(TaskWrapper taskWrapper) {
        TaskStatus oldStatus = taskWrapper.getStatus();
        taskWrapper.updateStatus(TaskStatus.RUNNING);
        statistics.recordTaskStarted(taskWrapper.getPriority(), oldStatus);
        
        try {
            LogUtil.debug("开始执行任务: {} ({})", taskWrapper.getTaskId(), taskWrapper.getTask().getName());
            
            TaskResult result = taskWrapper.getTask().execute();
            
            if (result.isSuccess()) {
                taskWrapper.updateStatus(TaskStatus.COMPLETED);
                statistics.recordTaskCompleted(taskWrapper.getPriority(), TaskStatus.RUNNING, taskWrapper.getExecutionTime());
                taskWrapper.getFuture().complete(result);
                LogUtil.debug("任务执行成功: {} (耗时: {}ms)", taskWrapper.getTaskId(), taskWrapper.getExecutionTime());
            } else {
                handleTaskFailure(taskWrapper, result.getError().orElse(null));
            }
            
        } catch (Exception e) {
            handleTaskFailure(taskWrapper, e);
        } finally {
            activeTasks.remove(taskWrapper.getTaskId());
        }
    }
    
    /**
     * 处理任务失败
     */
    private void handleTaskFailure(TaskWrapper taskWrapper, Throwable error) {
        taskWrapper.setLastError(error);
        taskWrapper.incrementRetryCount();
        
        if (taskWrapper.getRetryCount() < maxRetryAttempts && taskWrapper.getTask().isRetryable()) {
            // 重试任务
            taskWrapper.updateStatus(TaskStatus.RETRYING);
            statistics.recordTaskCancelled(taskWrapper.getPriority(), TaskStatus.RUNNING);
            
            scheduledExecutor.schedule(() -> {
                taskWrapper.updateStatus(TaskStatus.PENDING);
                taskQueue.offer(taskWrapper);
                activeTasks.put(taskWrapper.getTaskId(), taskWrapper);
                LogUtil.info("任务重试: {} (第{}次)", taskWrapper.getTaskId(), taskWrapper.getRetryCount());
            }, retryDelay, TimeUnit.MILLISECONDS);
            
        } else {
            // 任务最终失败
            taskWrapper.updateStatus(TaskStatus.FAILED);
            statistics.recordTaskFailed(taskWrapper.getPriority(), TaskStatus.RUNNING, taskWrapper.getExecutionTime());
            taskWrapper.getFuture().completeExceptionally(
                new TaskExecutionException("任务执行失败: " + taskWrapper.getTaskId(), error)
            );
            LogUtil.error("任务执行失败: {} (重试{}次)", error, taskWrapper.getTaskId(), taskWrapper.getRetryCount());
        }
    }
    
    /**
     * 启动监控任务
     */
    private void startMonitoringTask() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                performHealthCheck();
                cleanupCompletedTasks();
            } catch (Exception e) {
                LogUtil.error("监控任务异常", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 健康检查
     */
    private void performHealthCheck() {
        long runningTasks = statistics.getCurrentRunningTasks();
        long pendingTasks = statistics.getCurrentPendingTasks();
        
        if (runningTasks > maxConcurrentTasks * 0.8) {
            LogUtil.warn("任务调度器负载较高，运行中任务数: {}/{}", runningTasks, maxConcurrentTasks);
        }
        
        if (pendingTasks > 100) {
            LogUtil.warn("任务队列积压严重，待处理任务数: {}", pendingTasks);
        }
        
        LogUtil.debug("任务调度器状态: {}", statistics.toString());
    }
    
    /**
     * 清理已完成的任务
     */
    private void cleanupCompletedTasks() {
        long cutoffTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10);
        
        activeTasks.entrySet().removeIf(entry -> {
            TaskWrapper wrapper = entry.getValue();
            TaskStatus status = wrapper.getStatus();
            return (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED || status == TaskStatus.CANCELLED)
                   && wrapper.getEndTime() < cutoffTime;
        });
    }
    
    /**
     * 提交任务
     */
    public CompletableFuture<TaskResult> submitTask(Task task, TaskPriority priority) {
        if (isShutdown) {
            return CompletableFuture.failedFuture(new IllegalStateException("任务调度器已关闭"));
        }
        
        String taskId = generateTaskId();
        TaskWrapper taskWrapper = new TaskWrapper(taskId, task, priority);
        
        activeTasks.put(taskId, taskWrapper);
        taskQueue.offer(taskWrapper);
        statistics.recordTaskSubmitted(priority);
        
        LogUtil.debug("任务已提交: {} ({}, 优先级: {})", taskId, task.getName(), priority);
        return taskWrapper.getFuture();
    }
    
    /**
     * 提交普通优先级任务
     */
    public CompletableFuture<TaskResult> submitTask(Task task) {
        return submitTask(task, TaskPriority.NORMAL);
    }
    
    /**
     * 批量提交任务
     */
    public List<CompletableFuture<TaskResult>> submitTasks(List<Task> tasks, TaskPriority priority) {
        return tasks.stream()
            .map(task -> submitTask(task, priority))
            .collect(Collectors.toList());
    }
    
    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        TaskWrapper taskWrapper = activeTasks.get(taskId);
        if (taskWrapper != null) {
            TaskStatus oldStatus = taskWrapper.getStatus();
            taskWrapper.updateStatus(TaskStatus.CANCELLED);
            statistics.recordTaskCancelled(taskWrapper.getPriority(), oldStatus);
            taskWrapper.getFuture().cancel(true);
            
            // 从队列中移除
            taskQueue.remove(taskWrapper);
            activeTasks.remove(taskId);
            
            LogUtil.info("任务已取消: {}", taskId);
            return true;
        }
        return false;
    }
    
    /**
     * 获取任务状态
     */
    public Optional<TaskStatus> getTaskStatus(String taskId) {
        TaskWrapper taskWrapper = activeTasks.get(taskId);
        return taskWrapper != null ? Optional.of(taskWrapper.getStatus()) : Optional.empty();
    }
    
    /**
     * 等待所有任务完成
     */
    public boolean awaitAllTasks(long timeout, TimeUnit unit) {
        long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
        
        while (System.currentTimeMillis() < endTime) {
            if (statistics.getCurrentRunningTasks() == 0 && statistics.getCurrentPendingTasks() == 0) {
                return true;
            }
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * 生成任务ID
     */
    private String generateTaskId() {
        return "task-" + taskIdGenerator.incrementAndGet() + "-" + System.currentTimeMillis();
    }
    
    /**
     * 获取统计信息
     */
    public SchedulerStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 获取活跃任务信息
     */
    public List<TaskInfo> getActiveTasks() {
        return activeTasks.values().stream()
            .map(wrapper -> new TaskInfo(
                wrapper.getTaskId(),
                wrapper.getTask().getName(),
                wrapper.getTask().getType(),
                wrapper.getPriority(),
                wrapper.getStatus(),
                wrapper.getSubmitTime(),
                wrapper.getStartTime(),
                wrapper.getEndTime(),
                wrapper.getExecutionTime(),
                wrapper.getRetryCount(),
                wrapper.getLastError()
            ))
            .collect(Collectors.toList());
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        isShutdown = true;
        
        LogUtil.info("正在关闭任务调度器...");
        
        // 取消所有待处理任务
        taskQueue.forEach(wrapper -> {
            if (wrapper.getStatus() == TaskStatus.PENDING) {
                cancelTask(wrapper.getTaskId());
            }
        });
        
        // 关闭执行器
        executorService.shutdown();
        scheduledExecutor.shutdown();
        
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LogUtil.info("任务调度器已关闭");
    }
    
    /**
     * 检查是否已关闭
     */
    public boolean isShutdown() {
        return isShutdown;
    }
}
