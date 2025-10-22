package org.example.liteworkspace.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.example.liteworkspace.config.ConfigurationManager;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * 优化的日志工具类
 * 
 * 主要功能：
 * 1. 条件日志记录
 * 2. 日志格式化标准
 * 3. 日志轮转和清理
 * 4. 性能优化
 * 5. 异步日志处理
 */
public class OptimizedLogUtil {
    
    /**
     * 日志级别枚举
     */
    public enum LogLevel {
        TRACE(0, "TRACE"),
        DEBUG(1, "DEBUG"),
        INFO(2, "INFO"),
        WARN(3, "WARN"),
        ERROR(4, "ERROR"),
        OFF(5, "OFF");
        
        private final int level;
        private final String name;
        
        LogLevel(int level, String name) {
            this.level = level;
            this.name = name;
        }
        
        public int getLevel() { return level; }
        public String getName() { return name; }
        
        public boolean isEnabled(LogLevel currentLevel) {
            return this.level >= currentLevel.level;
        }
    }
    
    /**
     * 日志格式化器接口
     */
    public interface LogFormatter {
        String format(LogLevel level, String message, Throwable throwable, long timestamp, String threadName);
    }
    
    /**
     * 默认日志格式化器
     */
    public static class DefaultLogFormatter implements LogFormatter {
        private static final DateTimeFormatter DATETIME_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        
        @Override
        public String format(LogLevel level, String message, Throwable throwable, long timestamp, String threadName) {
            StringBuilder sb = new StringBuilder();
            
            // 时间戳
            sb.append(DATETIME_FORMATTER.format(Instant.ofEpochMilli(timestamp)));
            sb.append(" ");
            
            // 日志级别
            sb.append("[").append(level.getName()).append("]");
            sb.append(" ");
            
            // 线程名
            sb.append("[").append(threadName).append("]");
            sb.append(" ");
            
            // 插件标识
            sb.append("[LiteWorkspace]");
            sb.append(" ");
            
            // 消息
            sb.append(message);
            
            // 异常信息
            if (throwable != null) {
                sb.append(" - Exception: ").append(throwable.getClass().getSimpleName());
                sb.append(": ").append(throwable.getMessage());
            }
            
            return sb.toString();
        }
    }
    
    /**
     * 简洁日志格式化器
     */
    public static class SimpleLogFormatter implements LogFormatter {
        @Override
        public String format(LogLevel level, String message, Throwable throwable, long timestamp, String threadName) {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(level.getName()).append("]");
            sb.append(" [LiteWorkspace] ");
            sb.append(message);
            
            if (throwable != null) {
                sb.append(" - ").append(throwable.getMessage());
            }
            
            return sb.toString();
        }
    }
    
    /**
     * 日志统计信息
     */
    public static class LogStatistics {
        private final AtomicLong totalLogs = new AtomicLong(0);
        private final Map<LogLevel, AtomicLong> logsByLevel = new ConcurrentHashMap<>();
        private final AtomicLong totalFormattingTime = new AtomicLong(0);
        private final AtomicLong totalWritingTime = new AtomicLong(0);
        private final AtomicLong droppedLogs = new AtomicLong(0);
        
        public LogStatistics() {
            for (LogLevel level : LogLevel.values()) {
                logsByLevel.put(level, new AtomicLong(0));
            }
        }
        
        public void recordLog(LogLevel level, long formattingTime, long writingTime) {
            totalLogs.incrementAndGet();
            logsByLevel.get(level).incrementAndGet();
            totalFormattingTime.addAndGet(formattingTime);
            totalWritingTime.addAndGet(writingTime);
        }
        
        public void recordDroppedLog() {
            droppedLogs.incrementAndGet();
        }
        
        public double getAverageFormattingTime() {
            long total = totalLogs.get();
            return total == 0 ? 0.0 : (double) totalFormattingTime.get() / total;
        }
        
        public double getAverageWritingTime() {
            long total = totalLogs.get();
            return total == 0 ? 0.0 : (double) totalWritingTime.get() / total;
        }
        
        public double getDropRate() {
            long total = totalLogs.get() + droppedLogs.get();
            return total == 0 ? 0.0 : (double) droppedLogs.get() / total;
        }
        
        @Override
        public String toString() {
            return String.format(
                "LogStats{total=%d, dropped=%d(dropRate=%.2f%%), avgFormat=%.2fms, avgWrite=%.2fms}",
                totalLogs.get(), droppedLogs.get(), getDropRate() * 100,
                getAverageFormattingTime(), getAverageWritingTime()
            );
        }
        
        // Getters
        public long getTotalLogs() { return totalLogs.get(); }
        public long getDroppedLogs() { return droppedLogs.get(); }
        public Map<LogLevel, AtomicLong> getLogsByLevel() { 
            return new HashMap<>(logsByLevel); 
        }
    }
    
    /**
     * 日志条目
     */
    private static class LogEntry {
        final LogLevel level;
        final String message;
        final Throwable throwable;
        final long timestamp;
        final String threadName;
        
        LogEntry(LogLevel level, String message, Throwable throwable) {
            this.level = level;
            this.message = message;
            this.throwable = throwable;
            this.timestamp = System.currentTimeMillis();
            this.threadName = Thread.currentThread().getName();
        }
    }
    
    /**
     * 日志文件管理器
     */
    private static class LogFileManager {
        private final String logDir;
        private final String logFilePrefix;
        private final long maxFileSize;
        private final int maxFiles;
        private final long maxFileAge;
        
        private volatile PrintWriter currentWriter;
        private volatile String currentFileName;
        private volatile long currentFileSize;
        private final ReentrantLock fileLock = new ReentrantLock();
        
        public LogFileManager(String logDir, String logFilePrefix, long maxFileSize, int maxFiles, long maxFileAge) {
            this.logDir = logDir;
            this.logFilePrefix = logFilePrefix;
            this.maxFileSize = maxFileSize;
            this.maxFiles = maxFiles;
            this.maxFileAge = maxFileAge;
            
            // 创建日志目录
            try {
                Files.createDirectories(Paths.get(logDir));
            } catch (IOException e) {
                Logger.getInstance(OptimizedLogUtil.class).error("Failed to create log directory: " + logDir, e);
            }
            
            // 初始化当前文件
            rotateIfNeeded();
        }
        
        public void writeLog(String logMessage) {
            fileLock.lock();
            try {
                rotateIfNeeded();
                
                if (currentWriter != null) {
                    currentWriter.println(logMessage);
                    currentWriter.flush();
                    currentFileSize += logMessage.length() + 1; // +1 for newline
                }
            } catch (Exception e) {
                Logger.getInstance(OptimizedLogUtil.class).error("Failed to write log", e);
            } finally {
                fileLock.unlock();
            }
        }
        
        private void rotateIfNeeded() {
            if (currentWriter == null || currentFileSize >= maxFileSize) {
                closeCurrentWriter();
                createNewFile();
                cleanOldFiles();
            }
        }
        
        private void closeCurrentWriter() {
            if (currentWriter != null) {
                currentWriter.close();
                currentWriter = null;
            }
        }
        
        private void createNewFile() {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("%s_%s_%d.log", logFilePrefix, timestamp, System.currentTimeMillis());
            Path filePath = Paths.get(logDir, fileName);
            
            try {
                currentWriter = new PrintWriter(Files.newBufferedWriter(filePath, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
                currentFileName = fileName;
                currentFileSize = 0;
            } catch (IOException e) {
                Logger.getInstance(OptimizedLogUtil.class).error("Failed to create log file: " + filePath, e);
            }
        }
        
        private void cleanOldFiles() {
            try {
                long cutoffTime = System.currentTimeMillis() - maxFileAge;
                
                List<Path> logFiles = Files.list(Paths.get(logDir))
                    .filter(path -> path.getFileName().toString().startsWith(logFilePrefix))
                    .filter(path -> path.getFileName().toString().endsWith(".log"))
                    .sorted(Comparator.comparing(path -> -path.toFile().lastModified()))
                    .collect(Collectors.toList());
                
                // 删除超过数量的文件
                if (logFiles.size() > maxFiles) {
                    for (int i = maxFiles; i < logFiles.size(); i++) {
                        Files.deleteIfExists(logFiles.get(i));
                    }
                }
                
                // 删除过期的文件
                for (Path file : logFiles) {
                    if (file.toFile().lastModified() < cutoffTime) {
                        Files.deleteIfExists(file);
                    }
                }
            } catch (IOException e) {
                Logger.getInstance(OptimizedLogUtil.class).error("Failed to clean old log files", e);
            }
        }
        
        public void shutdown() {
            fileLock.lock();
            try {
                closeCurrentWriter();
            } finally {
                fileLock.unlock();
            }
        }
    }
    
    // 核心组件
    private static final Logger intellijLogger = Logger.getInstance(OptimizedLogUtil.class);
    private static volatile LogLevel currentLogLevel = LogLevel.INFO;
    private static volatile LogFormatter formatter = new DefaultLogFormatter();
    private static volatile LogFileManager fileManager;
    private static final BlockingQueue<LogEntry> logQueue = new LinkedBlockingQueue<>(10000);
    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OptimizedLogUtil-Worker");
        t.setDaemon(true);
        return t;
    });
    private static final LogStatistics statistics = new LogStatistics();
    private static volatile boolean isShutdown = false;
    
    // 条件日志记录的谓词
    private static final Map<String, Predicate<Object[]>> logConditions = new ConcurrentHashMap<>();
    
    static {
        // 启动日志处理线程
        startLogProcessor();
        
        // 添加默认条件
        addLogCondition("performance", params -> params.length > 0 && params[0] instanceof Number && ((Number) params[0]).doubleValue() > 1000.0);
        addLogCondition("cache", params -> params.length > 0 && "cache".equals(params[0]));
        addLogCondition("scan", params -> params.length > 0 && "scan".equals(params[0]));
    }
    
    /**
     * 启动日志处理器
     */
    private static void startLogProcessor() {
        logExecutor.submit(() -> {
            while (!isShutdown && !Thread.currentThread().isInterrupted()) {
                try {
                    LogEntry entry = logQueue.poll(1, TimeUnit.SECONDS);
                    if (entry != null) {
                        processLogEntry(entry);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    intellijLogger.error("Log processor exception", e);
                }
            }
        });
    }
    
    /**
     * 处理日志条目
     */
    private static void processLogEntry(LogEntry entry) {
        long startTime = System.nanoTime();
        
        try {
            // 格式化日志
            String formattedMessage = formatter.format(
                entry.level, entry.message, entry.throwable, 
                entry.timestamp, entry.threadName
            );
            
            long formattingTime = (System.nanoTime() - startTime) / 1_000_000;
            long writeStartTime = System.nanoTime();
            
            // 输出到 IntelliJ 日志
            switch (entry.level) {
                case TRACE:
                case DEBUG:
                    intellijLogger.debug(formattedMessage, entry.throwable);
                    break;
                case INFO:
                    intellijLogger.info(formattedMessage, entry.throwable);
                    break;
                case WARN:
                    intellijLogger.warn(formattedMessage, entry.throwable);
                    break;
                case ERROR:
                    intellijLogger.error(formattedMessage, entry.throwable);
                    break;
            }
            
            // 输出到文件
            if (fileManager != null) {
                fileManager.writeLog(formattedMessage);
            }
            
            long writingTime = (System.nanoTime() - writeStartTime) / 1_000_000;
            statistics.recordLog(entry.level, formattingTime, writingTime);
            
        } catch (Exception e) {
            statistics.recordDroppedLog();
            intellijLogger.error("Failed to process log entry", e);
        }
    }
    
    /**
     * 设置日志级别
     */
    public static void setLogLevel(LogLevel level) {
        currentLogLevel = level;
    }
    
    /**
     * 设置日志格式化器
     */
    public static void setFormatter(LogFormatter logFormatter) {
        formatter = logFormatter != null ? logFormatter : new DefaultLogFormatter();
    }
    
    /**
     * 启用文件日志
     */
    public static void enableFileLogging(String logDir, String logFilePrefix, long maxFileSize, int maxFiles, long maxFileAge) {
        if (fileManager != null) {
            fileManager.shutdown();
        }
        fileManager = new LogFileManager(logDir, logFilePrefix, maxFileSize, maxFiles, maxFileAge);
    }
    
    /**
     * 禁用文件日志
     */
    public static void disableFileLogging() {
        if (fileManager != null) {
            fileManager.shutdown();
            fileManager = null;
        }
    }
    
    /**
     * 添加日志条件
     */
    public static void addLogCondition(String name, Predicate<Object[]> condition) {
        logConditions.put(name, condition);
    }
    
    /**
     * 移除日志条件
     */
    public static void removeLogCondition(String name) {
        logConditions.remove(name);
    }
    
    /**
     * 检查条件是否满足
     */
    private static boolean checkCondition(String conditionName, Object[] params) {
        Predicate<Object[]> condition = logConditions.get(conditionName);
        return condition != null && condition.test(params);
    }
    
    /**
     * 格式化消息
     */
    private static String formatMessage(String message, Object... params) {
        if (params == null || params.length == 0) {
            return message;
        }
        
        String result = message;
        for (Object param : params) {
            String value;
            if (param == null) {
                value = "null";
            } else if (isPrimitiveOrString(param)) {
                value = String.valueOf(param);
            } else if (param instanceof Collection<?> col) {
                value = col.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", ", "[", "]"));
            } else if (param instanceof Map<?, ?> map) {
                value = map.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", ", "{", "}"));
            } else {
                value = JSONUtil.toJsonStr(param);
            }
            
            result = result.replaceFirst("\\{\\}", value);
        }
        
        return result;
    }
    
    /**
     * 检查是否为基本类型或字符串
     */
    private static boolean isPrimitiveOrString(Object obj) {
        Class<?> clazz = obj.getClass();
        return clazz.isPrimitive()
            || clazz == String.class
            || Number.class.isAssignableFrom(clazz)
            || clazz == Boolean.class
            || clazz == Character.class;
    }
    
    /**
     * 提交日志条目
     */
    private static void submitLog(LogLevel level, String message, Throwable throwable, Object... params) {
        if (!level.isEnabled(currentLogLevel)) {
            return;
        }
        
        String formattedMessage = formatMessage(message, params);
        LogEntry entry = new LogEntry(level, formattedMessage, throwable);
        
        if (!logQueue.offer(entry)) {
            statistics.recordDroppedLog();
        }
    }
    
    // 公共日志方法
    
    /**
     * TRACE 级别日志
     */
    public static void trace(String message, Object... params) {
        submitLog(LogLevel.TRACE, message, null, params);
    }
    
    /**
     * DEBUG 级别日志
     */
    public static void debug(String message, Object... params) {
        submitLog(LogLevel.DEBUG, message, null, params);
    }
    
    /**
     * INFO 级别日志
     */
    public static void info(String message, Object... params) {
        submitLog(LogLevel.INFO, message, null, params);
    }
    
    /**
     * WARN 级别日志
     */
    public static void warn(String message, Object... params) {
        submitLog(LogLevel.WARN, message, null, params);
    }
    
    /**
     * ERROR 级别日志
     */
    public static void error(String message, Throwable throwable, Object... params) {
        submitLog(LogLevel.ERROR, message, throwable, params);
    }
    
    // 条件日志方法
    
    /**
     * 条件日志 - 性能相关
     */
    public static void logPerformance(String message, Object... params) {
        if (checkCondition("performance", params)) {
            info("[PERF] " + message, params);
        }
    }
    
    /**
     * 条件日志 - 缓存相关
     */
    public static void logCache(String message, Object... params) {
        if (checkCondition("cache", params)) {
            debug("[CACHE] " + message, params);
        }
    }
    
    /**
     * 条件日志 - 扫描相关
     */
    public static void logScan(String message, Object... params) {
        if (checkCondition("scan", params)) {
            info("[SCAN] " + message, params);
        }
    }
    
    /**
     * 自定义条件日志
     */
    public static void logIf(String conditionName, String message, Object... params) {
        if (checkCondition(conditionName, params)) {
            info("[" + conditionName.toUpperCase() + "] " + message, params);
        }
    }
    
    // 配置相关方法
    
    /**
     * 从配置管理器更新设置
     */
    public static void updateFromConfiguration(Project project) {
        ConfigurationManager config = ConfigurationManager.getInstance(project);
        
        // 更新日志级别
        String logLevelStr = config.getLogLevel();
        try {
            LogLevel level = LogLevel.valueOf(logLevelStr.toUpperCase());
            setLogLevel(level);
        } catch (IllegalArgumentException e) {
            setLogLevel(LogLevel.INFO);
        }
        
        // 更新文件日志设置
        if (config.isEnableDebugLog()) {
            String logDir = System.getProperty("user.home") + "/.lite-workspace/logs";
            enableFileLogging(logDir, "liteworkspace", 10 * 1024 * 1024, 10, 7 * 24 * 60 * 60 * 1000L);
        } else {
            disableFileLogging();
        }
    }
    
    /**
     * 获取统计信息
     */
    public static LogStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * 清理日志队列
     */
    public static void clearQueue() {
        logQueue.clear();
    }
    
    /**
     * 关闭日志系统
     */
    public static void shutdown() {
        isShutdown = true;
        
        // 处理剩余日志
        LogEntry entry;
        while ((entry = logQueue.poll()) != null) {
            processLogEntry(entry);
        }
        
        // 关闭组件
        if (fileManager != null) {
            fileManager.shutdown();
        }
        
        logExecutor.shutdown();
        try {
            if (!logExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 检查是否已关闭
     */
    public static boolean isShutdown() {
        return isShutdown;
    }
}
