package org.example.liteworkspace.task;

/**
 * 任务类型枚举
 * 
 * 定义系统中各种任务的类型分类
 */
public enum TaskType {
    
    // ===================== 扫描相关任务 =====================
    
    /**
     * Bean 扫描任务
     */
    BEAN_SCAN("Bean扫描", "扫描和分析Spring Bean"),
    
    /**
     * 类依赖分析任务
     */
    DEPENDENCY_ANALYSIS("依赖分析", "分析类之间的依赖关系"),
    
    /**
     * 配置扫描任务
     */
    CONFIGURATION_SCAN("配置扫描", "扫描配置文件和配置类"),
    
    /**
     * MyBatis 扫描任务
     */
    MYBATIS_SCAN("MyBatis扫描", "扫描MyBatis相关组件"),
    
    // ===================== 缓存相关任务 =====================
    
    /**
     * 缓存清理任务
     */
    CACHE_CLEANUP("缓存清理", "清理过期或无效的缓存"),
    
    /**
     * 缓存预热任务
     */
    CACHE_WARMUP("缓存预热", "预加载常用数据到缓存"),
    
    /**
     * 缓存同步任务
     */
    CACHE_SYNC("缓存同步", "同步缓存数据"),
    
    // ===================== 分析相关任务 =====================
    
    /**
     * 代码分析任务
     */
    CODE_ANALYSIS("代码分析", "分析代码结构和质量"),
    
    /**
     * 性能分析任务
     */
    PERFORMANCE_ANALYSIS("性能分析", "分析和优化性能"),
    
    /**
     * 依赖图构建任务
     */
    DEPENDENCY_GRAPH_BUILD("依赖图构建", "构建项目依赖关系图"),
    
    // ===================== 系统相关任务 =====================
    
    /**
     * 系统健康检查任务
     */
    HEALTH_CHECK("健康检查", "检查系统健康状态"),
    
    /**
     * 资源清理任务
     */
    RESOURCE_CLEANUP("资源清理", "清理系统资源"),
    
    /**
     * 数据备份任务
     */
    DATA_BACKUP("数据备份", "备份重要数据"),
    
    /**
     * 日志清理任务
     */
    LOG_CLEANUP("日志清理", "清理过期日志文件"),
    
    // ===================== 用户界面相关任务 =====================
    
    /**
     * UI 更新任务
     */
    UI_UPDATE("界面更新", "更新用户界面"),
    
    /**
     * 通知发送任务
     */
    NOTIFICATION_SEND("通知发送", "发送用户通知"),
    
    /**
     * 报告生成任务
     */
    REPORT_GENERATION("报告生成", "生成分析报告"),
    
    // ===================== 网络相关任务 =====================
    
    /**
     * 网络请求任务
     */
    NETWORK_REQUEST("网络请求", "执行网络请求"),
    
    /**
     * 文件下载任务
     */
    FILE_DOWNLOAD("文件下载", "下载文件"),
    
    /**
     * 数据同步任务
     */
    DATA_SYNC("数据同步", "同步远程数据"),
    
    // ===================== 通用任务 =====================
    
    /**
     * 批处理任务
     */
    BATCH_PROCESS("批处理", "批量处理数据"),
    
    /**
     * 定时任务
     */
    SCHEDULED_TASK("定时任务", "按计划执行的任务"),
    
    /**
     * 后台任务
     */
    BACKGROUND_TASK("后台任务", "在后台运行的任务"),
    
    /**
     * 未知任务类型
     */
    UNKNOWN("未知任务", "未识别的任务类型");
    
    private final String displayName;
    private final String description;
    
    TaskType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 是否为扫描相关任务
     */
    public boolean isScanTask() {
        return this == BEAN_SCAN || this == DEPENDENCY_ANALYSIS || 
               this == CONFIGURATION_SCAN || this == MYBATIS_SCAN;
    }
    
    /**
     * 是否为缓存相关任务
     */
    public boolean isCacheTask() {
        return this == CACHE_CLEANUP || this == CACHE_WARMUP || this == CACHE_SYNC;
    }
    
    /**
     * 是否为分析相关任务
     */
    public boolean isAnalysisTask() {
        return this == CODE_ANALYSIS || this == PERFORMANCE_ANALYSIS || 
               this == DEPENDENCY_GRAPH_BUILD;
    }
    
    /**
     * 是否为系统相关任务
     */
    public boolean isSystemTask() {
        return this == HEALTH_CHECK || this == RESOURCE_CLEANUP || 
               this == DATA_BACKUP || this == LOG_CLEANUP;
    }
    
    /**
     * 是否为用户界面相关任务
     */
    public boolean isUITask() {
        return this == UI_UPDATE || this == NOTIFICATION_SEND || this == REPORT_GENERATION;
    }
    
    /**
     * 是否为网络相关任务
     */
    public boolean isNetworkTask() {
        return this == NETWORK_REQUEST || this == FILE_DOWNLOAD || this == DATA_SYNC;
    }
    
    /**
     * 是否为耗时任务
     */
    public boolean isTimeConsuming() {
        return isScanTask() || isAnalysisTask() || this == DEPENDENCY_GRAPH_BUILD || 
               this == REPORT_GENERATION || this == DATA_SYNC;
    }
    
    /**
     * 是否为关键任务
     */
    public boolean isCritical() {
        return this == HEALTH_CHECK || this == BEAN_SCAN || this == CONFIGURATION_SCAN;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
