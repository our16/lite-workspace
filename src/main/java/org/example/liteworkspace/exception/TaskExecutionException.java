package org.example.liteworkspace.exception;

/**
 * 任务执行异常
 * 
 * 用于表示任务执行过程中发生的异常
 */
public class TaskExecutionException extends PluginException {
    
    private final String taskId;
    private final String taskName;
    private final String taskType;
    
    public TaskExecutionException(String message) {
        super("TASK_EXECUTION_ERROR", message);
        this.taskId = null;
        this.taskName = null;
        this.taskType = null;
    }
    
    public TaskExecutionException(String message, Throwable cause) {
        super("TASK_EXECUTION_ERROR", message, cause);
        this.taskId = null;
        this.taskName = null;
        this.taskType = null;
    }
    
    public TaskExecutionException(String errorCode, String message) {
        super(errorCode, message);
        this.taskId = null;
        this.taskName = null;
        this.taskType = null;
    }
    
    public TaskExecutionException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.taskId = null;
        this.taskName = null;
        this.taskType = null;
    }
    
    public TaskExecutionException(String taskId, String taskName, String taskType, String message) {
        super("TASK_EXECUTION_ERROR", message);
        this.taskId = taskId;
        this.taskName = taskName;
        this.taskType = taskType;
    }
    
    public TaskExecutionException(String taskId, String taskName, String taskType, String message, Throwable cause) {
        super("TASK_EXECUTION_ERROR", message, cause);
        this.taskId = taskId;
        this.taskName = taskName;
        this.taskType = taskType;
    }
    
    /**
     * 创建任务超时异常
     */
    public static TaskExecutionException timeout(String taskId, String taskName, long timeoutMs) {
        return new TaskExecutionException(
            taskId, taskName, null,
            String.format("任务执行超时，超时时间: %dms", timeoutMs)
        );
    }
    
    /**
     * 创建任务取消异常
     */
    public static TaskExecutionException cancelled(String taskId, String taskName) {
        return new TaskExecutionException(
            taskId, taskName, null,
            "任务已被取消"
        );
    }
    
    /**
     * 创建任务重试失败异常
     */
    public static TaskExecutionException retryFailed(String taskId, String taskName, int retryCount) {
        return new TaskExecutionException(
            taskId, taskName, null,
            String.format("任务重试失败，已重试 %d 次", retryCount)
        );
    }
    
    /**
     * 创建任务配置错误异常
     */
    public static TaskExecutionException configurationError(String taskName, String configIssue) {
        return new TaskExecutionException(
            "TASK_CONFIG_ERROR",
            String.format("任务配置错误 [%s]: %s", taskName, configIssue)
        );
    }
    
    /**
     * 创建任务资源不足异常
     */
    public static TaskExecutionException resourceInsufficient(String taskName, String resource) {
        return new TaskExecutionException(
            "TASK_RESOURCE_INSUFFICIENT",
            String.format("任务资源不足 [%s]: 缺少 %s", taskName, resource)
        );
    }
    
    /**
     * 创建任务依赖异常
     */
    public static TaskExecutionException dependencyError(String taskName, String dependency) {
        return new TaskExecutionException(
            "TASK_DEPENDENCY_ERROR",
            String.format("任务依赖错误 [%s]: 依赖 %s 不可用", taskName, dependency)
        );
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public String getTaskName() {
        return taskName;
    }
    
    public String getTaskType() {
        return taskType;
    }
    
    public boolean hasTaskInfo() {
        return taskId != null || taskName != null || taskType != null;
    }
    
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder(getMessage());
        
        if (hasTaskInfo()) {
            sb.append(" | 任务信息: ");
            if (taskId != null) sb.append("ID=").append(taskId).append(" ");
            if (taskName != null) sb.append("名称=").append(taskName).append(" ");
            if (taskType != null) sb.append("类型=").append(taskType);
        }
        
        return sb.toString();
    }
}
