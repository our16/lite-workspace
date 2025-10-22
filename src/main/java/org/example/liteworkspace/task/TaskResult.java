package org.example.liteworkspace.task;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 任务执行结果
 * 
 * 封装任务执行的结果数据、状态和元信息
 */
public class TaskResult {
    
    private final boolean success;
    private final String message;
    private final Throwable error;
    private final Map<String, Object> data;
    private final long executionTime;
    private final String taskId;
    
    private TaskResult(Builder builder) {
        this.success = builder.success;
        this.message = builder.message;
        this.error = builder.error;
        this.data = new HashMap<>(builder.data);
        this.executionTime = builder.executionTime;
        this.taskId = builder.taskId;
    }
    
    /**
     * 创建成功结果
     */
    public static TaskResult success() {
        return new Builder().success(true).build();
    }
    
    /**
     * 创建成功结果（带消息）
     */
    public static TaskResult success(String message) {
        return new Builder().success(true).message(message).build();
    }
    
    /**
     * 创建成功结果（带数据）
     */
    public static TaskResult success(String message, Map<String, Object> data) {
        return new Builder().success(true).message(message).data(data).build();
    }
    
    /**
     * 创建失败结果
     */
    public static TaskResult failure(String message) {
        return new Builder().success(false).message(message).build();
    }
    
    /**
     * 创建失败结果（带异常）
     */
    public static TaskResult failure(String message, Throwable error) {
        return new Builder().success(false).message(message).error(error).build();
    }
    
    /**
     * 创建失败结果（仅异常）
     */
    public static TaskResult failure(Throwable error) {
        return new Builder().success(false).message(error.getMessage()).error(error).build();
    }
    
    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * 是否失败
     */
    public boolean isFailure() {
        return !success;
    }
    
    /**
     * 获取消息
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * 获取错误信息
     */
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }
    
    /**
     * 获取数据
     */
    public Map<String, Object> getData() {
        return new HashMap<>(data);
    }
    
    /**
     * 获取指定键的数据
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getData(String key) {
        return Optional.ofNullable((T) data.get(key));
    }
    
    /**
     * 获取执行时间
     */
    public long getExecutionTime() {
        return executionTime;
    }
    
    /**
     * 获取任务ID
     */
    public String getTaskId() {
        return taskId;
    }
    
    @Override
    public String toString() {
        return String.format(
            "TaskResult{success=%s, message='%s', executionTime=%dms, taskId='%s'}",
            success, message, executionTime, taskId
        );
    }
    
    /**
     * 结果构建器
     */
    public static class Builder {
        private boolean success = false;
        private String message = "";
        private Throwable error;
        private Map<String, Object> data = new HashMap<>();
        private long executionTime = 0;
        private String taskId = "";
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message != null ? message : "";
            return this;
        }
        
        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }
        
        public Builder data(Map<String, Object> data) {
            this.data = data != null ? new HashMap<>(data) : new HashMap<>();
            return this;
        }
        
        public Builder data(String key, Object value) {
            this.data.put(key, value);
            return this;
        }
        
        public Builder executionTime(long executionTime) {
            this.executionTime = executionTime;
            return this;
        }
        
        public Builder taskId(String taskId) {
            this.taskId = taskId != null ? taskId : "";
            return this;
        }
        
        public TaskResult build() {
            return new TaskResult(this);
        }
    }
}
