package org.example.liteworkspace.task;

/**
 * 任务接口
 * 
 * 所有可执行的任务都需要实现此接口
 */
public interface Task {
    
    /**
     * 执行任务
     * 
     * @return 任务执行结果
     * @throws Exception 执行过程中的异常
     */
    TaskResult execute() throws Exception;
    
    /**
     * 获取任务名称
     * 
     * @return 任务名称
     */
    String getName();
    
    /**
     * 获取任务类型
     * 
     * @return 任务类型
     */
    TaskType getType();
    
    /**
     * 是否可重试
     * 
     * @return true 表示任务失败后可以重试
     */
    default boolean isRetryable() {
        return true;
    }
    
    /**
     * 获取任务描述
     * 
     * @return 任务描述
     */
    default String getDescription() {
        return "";
    }
    
    /**
     * 获取任务优先级
     * 
     * @return 任务优先级
     */
    default TaskScheduler.TaskPriority getPriority() {
        return TaskScheduler.TaskPriority.NORMAL;
    }
    
    /**
     * 获取任务超时时间（毫秒）
     * 
     * @return 超时时间，0表示无限制
     */
    default long getTimeout() {
        return 0;
    }
}
