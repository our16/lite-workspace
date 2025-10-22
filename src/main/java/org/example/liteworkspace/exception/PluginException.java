package org.example.liteworkspace.exception;

/**
 * 插件基础异常类
 * 所有插件相关的异常都应该继承自这个类
 */
public class PluginException extends Exception {
    
    private final String errorCode;
    private final ErrorSeverity severity;
    private final String userMessage;
    private final String technicalDetails;
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误代码
     * @param message 错误消息
     */
    public PluginException(String errorCode, String message) {
        this(errorCode, message, ErrorSeverity.MEDIUM, null, null);
    }
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误代码
     * @param message 错误消息
     * @param cause 原因异常
     */
    public PluginException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, ErrorSeverity.MEDIUM, null, cause);
    }
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误代码
     * @param message 错误消息
     * @param severity 错误严重程度
     * @param userMessage 用户友好的消息
     * @param cause 原因异常
     */
    public PluginException(String errorCode, String message, ErrorSeverity severity, String userMessage, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.severity = severity != null ? severity : ErrorSeverity.MEDIUM;
        this.userMessage = userMessage != null ? userMessage : message;
        this.technicalDetails = cause != null ? cause.toString() : null;
    }
    
    /**
     * 获取错误代码
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * 获取错误严重程度
     */
    public ErrorSeverity getSeverity() {
        return severity;
    }
    
    /**
     * 获取用户友好的消息
     */
    public String getUserMessage() {
        return userMessage;
    }
    
    /**
     * 获取技术细节
     */
    public String getTechnicalDetails() {
        return technicalDetails;
    }
    
    /**
     * 是否为致命错误
     */
    public boolean isFatal() {
        return severity == ErrorSeverity.FATAL;
    }
    
    /**
     * 是否为警告
     */
    public boolean isWarning() {
        return severity == ErrorSeverity.LOW;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PluginException{");
        sb.append("errorCode='").append(errorCode).append('\'');
        sb.append(", severity=").append(severity);
        sb.append(", message='").append(getMessage()).append('\'');
        sb.append(", userMessage='").append(userMessage).append('\'');
        if (technicalDetails != null) {
            sb.append(", technicalDetails='").append(technicalDetails).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
    
    /**
     * 错误严重程度枚举
     */
    public enum ErrorSeverity {
        /**
         * 低严重程度 - 警告级别，不影响主要功能
         */
        LOW("警告"),
        
        /**
         * 中等严重程度 - 影响部分功能，但可以继续使用
         */
        MEDIUM("错误"),
        
        /**
         * 高严重程度 - 影响主要功能，需要立即处理
         */
        HIGH("严重错误"),
        
        /**
         * 致命错误 - 导致插件无法正常工作
         */
        FATAL("致命错误");
        
        private final String description;
        
        ErrorSeverity(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
