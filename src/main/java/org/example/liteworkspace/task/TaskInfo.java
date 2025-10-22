package org.example.liteworkspace.task;

/**
 * 任务信息
 * 
 * 用于展示任务的状态和详细信息
 */
public class TaskInfo {
    
    private final String taskId;
    private final String taskName;
    private final TaskType taskType;
    private final TaskScheduler.TaskPriority priority;
    private final TaskScheduler.TaskStatus status;
    private final long submitTime;
    private final long startTime;
    private final long endTime;
    private final long executionTime;
    private final int retryCount;
    private final Throwable lastError;
    
    public TaskInfo(String taskId, String taskName, TaskType taskType, 
                   TaskScheduler.TaskPriority priority, TaskScheduler.TaskStatus status,
                   long submitTime, long startTime, long endTime, long executionTime,
                   int retryCount, Throwable lastError) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.taskType = taskType;
        this.priority = priority;
        this.status = status;
        this.submitTime = submitTime;
        this.startTime = startTime;
        this.endTime = endTime;
        this.executionTime = executionTime;
        this.retryCount = retryCount;
        this.lastError = lastError;
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public String getTaskName() {
        return taskName;
    }
    
    public TaskType getTaskType() {
        return taskType;
    }
    
    public TaskScheduler.TaskPriority getPriority() {
        return priority;
    }
    
    public TaskScheduler.TaskStatus getStatus() {
        return status;
    }
    
    public long getSubmitTime() {
        return submitTime;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public long getExecutionTime() {
        return executionTime;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public Throwable getLastError() {
        return lastError;
    }
    
    /**
     * 获取等待时间
     */
    public long getWaitTime() {
        return startTime > 0 ? startTime - submitTime : 
               System.currentTimeMillis() - submitTime;
    }
    
    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return status == TaskScheduler.TaskStatus.RUNNING;
    }
    
    /**
     * 是否已完成
     */
    public boolean isCompleted() {
        return status == TaskScheduler.TaskStatus.COMPLETED;
    }
    
    /**
     * 是否失败
     */
    public boolean isFailed() {
        return status == TaskScheduler.TaskStatus.FAILED;
    }
    
    /**
     * 是否已取消
     */
    public boolean isCancelled() {
        return status == TaskScheduler.TaskStatus.CANCELLED;
    }
    
    @Override
    public String toString() {
        return String.format(
            "TaskInfo{id='%s', name='%s', type=%s, priority=%s, status=%s, " +
            "executionTime=%dms, retryCount=%d}",
            taskId, taskName, taskType, priority, status, executionTime, retryCount
        );
    }
}
