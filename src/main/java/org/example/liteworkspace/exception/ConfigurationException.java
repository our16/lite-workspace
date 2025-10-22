package org.example.liteworkspace.exception;

/**
 * 配置异常
 * 在配置管理过程中发生的异常
 */
public class ConfigurationException extends PluginException {
    
    // 错误代码常量
    public static final String CONFIG_LOAD_FAILED = "CONFIG_001";
    public static final String CONFIG_SAVE_FAILED = "CONFIG_002";
    public static final String CONFIG_VALIDATION_FAILED = "CONFIG_003";
    public static final String CONFIG_NOT_FOUND = "CONFIG_004";
    public static final String INVALID_CONFIG_FORMAT = "CONFIG_005";
    public static final String CONFIG_ACCESS_DENIED = "CONFIG_006";
    public static final String CONFIG_CORRUPTED = "CONFIG_007";
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误代码
     * @param message 错误消息
     */
    public ConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误代码
     * @param message 错误消息
     * @param cause 原因异常
     */
    public ConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
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
    public ConfigurationException(String errorCode, String message, ErrorSeverity severity, String userMessage, Throwable cause) {
        super(errorCode, message, severity, userMessage, cause);
    }
    
    /**
     * 创建配置加载失败异常
     */
    public static ConfigurationException loadFailed(String configPath, Throwable cause) {
        return new ConfigurationException(
            CONFIG_LOAD_FAILED,
            "配置加载失败: " + configPath,
            ErrorSeverity.HIGH,
            "无法加载配置文件 " + configPath + "，请检查文件是否存在且有读取权限",
            cause
        );
    }
    
    /**
     * 创建配置保存失败异常
     */
    public static ConfigurationException saveFailed(String configPath, Throwable cause) {
        return new ConfigurationException(
            CONFIG_SAVE_FAILED,
            "配置保存失败: " + configPath,
            ErrorSeverity.HIGH,
            "无法保存配置到 " + configPath + "，请检查磁盘空间和写入权限",
            cause
        );
    }
    
    /**
     * 创建配置验证失败异常
     */
    public static ConfigurationException validationFailed(String configKey, String value, String reason) {
        return new ConfigurationException(
            CONFIG_VALIDATION_FAILED,
            "配置验证失败: " + configKey + " = " + value,
            ErrorSeverity.MEDIUM,
            "配置项 " + configKey + " 的值 " + value + " 无效: " + reason,
            null
        );
    }
    
    /**
     * 创建配置未找到异常
     */
    public static ConfigurationException notFound(String configKey) {
        return new ConfigurationException(
            CONFIG_NOT_FOUND,
            "配置未找到: " + configKey,
            ErrorSeverity.LOW,
            "找不到配置项 " + configKey + "，将使用默认值",
            null
        );
    }
    
    /**
     * 创建无效配置格式异常
     */
    public static ConfigurationException invalidFormat(String configPath, String expectedFormat) {
        return new ConfigurationException(
            INVALID_CONFIG_FORMAT,
            "无效的配置格式: " + configPath,
            ErrorSeverity.MEDIUM,
            "配置文件 " + configPath + " 格式不正确，期望格式: " + expectedFormat,
            null
        );
    }
    
    /**
     * 创建配置访问拒绝异常
     */
    public static ConfigurationException accessDenied(String configPath) {
        return new ConfigurationException(
            CONFIG_ACCESS_DENIED,
            "配置访问被拒绝: " + configPath,
            ErrorSeverity.HIGH,
            "没有权限访问配置文件 " + configPath + "，请检查文件权限设置",
            null
        );
    }
    
    /**
     * 创建配置损坏异常
     */
    public static ConfigurationException corrupted(String configPath, Throwable cause) {
        return new ConfigurationException(
            CONFIG_CORRUPTED,
            "配置文件损坏: " + configPath,
            ErrorSeverity.HIGH,
            "配置文件 " + configPath + " 已损坏，请恢复备份或重新配置",
            cause
        );
    }
}
