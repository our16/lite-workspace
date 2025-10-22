package org.example.liteworkspace.exception;

/**
 * Bean扫描异常
 * 在Bean扫描过程中发生的异常
 */
public class BeanScanningException extends PluginException {
    
    // 错误代码常量
    public static final String SCAN_FAILED = "BEAN_SCAN_001";
    public static final String CLASS_NOT_FOUND = "BEAN_SCAN_002";
    public static final String DEPENDENCY_RESOLUTION_FAILED = "BEAN_SCAN_003";
    public static final String CIRCULAR_DEPENDENCY = "BEAN_SCAN_004";
    public static final String INVALID_CONFIGURATION = "BEAN_SCAN_005";
    public static final String SCAN_TIMEOUT = "BEAN_SCAN_006";
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误代码
     * @param message 错误消息
     */
    public BeanScanningException(String errorCode, String message) {
        super(errorCode, message);
    }
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误代码
     * @param message 错误消息
     * @param cause 原因异常
     */
    public BeanScanningException(String errorCode, String message, Throwable cause) {
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
    public BeanScanningException(String errorCode, String message, ErrorSeverity severity, String userMessage, Throwable cause) {
        super(errorCode, message, severity, userMessage, cause);
    }
    
    /**
     * 创建扫描失败异常
     */
    public static BeanScanningException scanFailed(String className, Throwable cause) {
        return new BeanScanningException(
            SCAN_FAILED,
            "Bean扫描失败: " + className,
            ErrorSeverity.HIGH,
            "扫描类 " + className + " 时发生错误，请检查类定义和依赖关系",
            cause
        );
    }
    
    /**
     * 创建类未找到异常
     */
    public static BeanScanningException classNotFound(String className) {
        return new BeanScanningException(
            CLASS_NOT_FOUND,
            "未找到类: " + className,
            ErrorSeverity.MEDIUM,
            "找不到类 " + className + "，请确认类路径配置正确",
            null
        );
    }
    
    /**
     * 创建依赖解析失败异常
     */
    public static BeanScanningException dependencyResolutionFailed(String className, String dependency, Throwable cause) {
        return new BeanScanningException(
            DEPENDENCY_RESOLUTION_FAILED,
            "依赖解析失败: " + className + " -> " + dependency,
            ErrorSeverity.HIGH,
            "无法解析类 " + className + " 的依赖 " + dependency,
            cause
        );
    }
    
    /**
     * 创建循环依赖异常
     */
    public static BeanScanningException circularDependency(String className, String dependencyChain) {
        return new BeanScanningException(
            CIRCULAR_DEPENDENCY,
            "检测到循环依赖: " + className,
            ErrorSeverity.HIGH,
            "类 " + className + " 存在循环依赖: " + dependencyChain + "，请重构代码以消除循环依赖",
            null
        );
    }
    
    /**
     * 创建无效配置异常
     */
    public static BeanScanningException invalidConfiguration(String configType, String details) {
        return new BeanScanningException(
            INVALID_CONFIGURATION,
            "无效的配置: " + configType,
            ErrorSeverity.MEDIUM,
            "配置 " + configType + " 无效: " + details + "，请检查配置文件",
            null
        );
    }
    
    /**
     * 创建扫描超时异常
     */
    public static BeanScanningException scanTimeout(String className, long timeoutMs) {
        return new BeanScanningException(
            SCAN_TIMEOUT,
            "Bean扫描超时: " + className,
            ErrorSeverity.MEDIUM,
            "扫描类 " + className + " 超时（" + timeoutMs + "ms），请检查是否存在性能问题",
            null
        );
    }
}
