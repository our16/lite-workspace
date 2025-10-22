package org.example.liteworkspace.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import org.example.liteworkspace.event.EventBus;
import org.example.liteworkspace.event.PluginEvents;
import org.example.liteworkspace.util.OptimizedLogUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 增强的LLM分析工具窗口
 * 
 * 主要功能：
 * 1. 多标签页界面
 * 2. 进度显示和状态反馈
 * 3. 结果导出功能
 * 4. 实时日志显示
 * 5. 统计信息展示
 * 6. 操作历史记录
 */
public class EnhancedLlmAnalysisToolWindow implements ToolWindowFactory {
    
    /**
     * 标签页类型枚举
     */
    public enum TabType {
        ANALYSIS("分析结果", "analysis"),
        LOGS("日志", "logs"),
        STATISTICS("统计", "statistics"),
        HISTORY("历史", "history");
        
        private final String displayName;
        private final String iconPath;
        
        TabType(String displayName, String iconPath) {
            this.displayName = displayName;
            this.iconPath = iconPath;
        }
        
        public String getDisplayName() { return displayName; }
        public String getIconPath() { return iconPath; }
    }
    
    /**
     * 工具窗口状态
     */
    public static class WindowState {
        private volatile boolean isAnalyzing = false;
        private volatile double progress = 0.0;
        private volatile String statusMessage = "就绪";
        private final AtomicLong totalAnalyses = new AtomicLong(0);
        private final AtomicLong successfulAnalyses = new AtomicLong(0);
        private final AtomicLong failedAnalyses = new AtomicLong(0);
        
        public void setAnalyzing(boolean analyzing) {
            this.isAnalyzing = analyzing;
            if (analyzing) {
                statusMessage = "分析中...";
            } else {
                statusMessage = "就绪";
            }
        }
        
        public void setProgress(double progress) {
            this.progress = Math.max(0.0, Math.min(1.0, progress));
        }
        
        public void setStatusMessage(String message) {
            this.statusMessage = message;
        }
        
        public void incrementTotal() {
            totalAnalyses.incrementAndGet();
        }
        
        public void incrementSuccessful() {
            successfulAnalyses.incrementAndGet();
        }
        
        public void incrementFailed() {
            failedAnalyses.incrementAndGet();
        }
        
        // Getters
        public boolean isAnalyzing() { return isAnalyzing; }
        public double getProgress() { return progress; }
        public String getStatusMessage() { return statusMessage; }
        public long getTotalAnalyses() { return totalAnalyses.get(); }
        public long getSuccessfulAnalyses() { return successfulAnalyses.get(); }
        public long getFailedAnalyses() { return failedAnalyses.get(); }
        
        public double getSuccessRate() {
            long total = totalAnalyses.get();
            return total == 0 ? 0.0 : (double) successfulAnalyses.get() / total;
        }
    }
    
    // 核心组件
    private Project project;
    private WindowState windowState;
    private EventBus eventBus;
    
    // UI组件
    private JBTabbedPane tabbedPane;
    private JTextArea analysisTextArea;
    private JTextArea logTextArea;
    private JTextArea statisticsTextArea;
    private JTextArea historyTextArea;
    private JProgressBar progressBar;
    private JBLabel statusLabel;
    
    // 数据存储
    private final ConcurrentHashMap<String, String> analysisResults = new ConcurrentHashMap<>();
    private final StringBuilder logBuffer = new StringBuilder();
    private final StringBuilder historyBuffer = new StringBuilder();
    
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        this.project = project;
        this.windowState = new WindowState();
        this.eventBus = new EventBus(project);
        
        OptimizedLogUtil.info("创建增强的LLM分析工具窗口");
        
        // 创建主面板
        JPanel mainPanel = createMainPanel();
        
        // 注册内容
        com.intellij.ui.content.ContentFactory contentFactory = com.intellij.ui.content.ContentFactory.getInstance();
        com.intellij.ui.content.Content content = contentFactory.createContent(mainPanel, "", false);
        toolWindow.getContentManager().addContent(content);
        
        // 注册事件监听器
        registerEventListeners();
        
        // 注册工具栏操作
        registerToolbarActions(toolWindow);
        
        OptimizedLogUtil.info("增强的LLM分析工具窗口创建完成");
    }
    
    /**
     * 创建主面板
     */
    private JPanel createMainPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // 创建状态栏
        JPanel statusBar = createStatusBar();
        mainPanel.add(statusBar, BorderLayout.NORTH);
        
        // 创建标签页
        tabbedPane = new JBTabbedPane();
        createTabs();
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        return mainPanel;
    }
    
    /**
     * 创建状态栏
     */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(JBUI.Borders.customLine(Color.GRAY, 0, 0, 1, 0));
        statusBar.setPreferredSize(new Dimension(-1, 30));
        
        // 进度条
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        
        // 状态标签
        statusLabel = new JBLabel("就绪");
        statusLabel.setBorder(JBUI.Borders.empty(5));
        
        // 操作按钮
        JPanel buttonPanel = createButtonPanel();
        
        statusBar.add(progressBar, BorderLayout.CENTER);
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(buttonPanel, BorderLayout.EAST);
        
        return statusBar;
    }
    
    /**
     * 创建按钮面板
     */
    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        
        // 开始分析按钮
        JButton analyzeButton = new JButton("开始分析");
        analyzeButton.addActionListener(this::handleAnalyzeAction);
        
        // 导出结果按钮
        JButton exportButton = new JButton("导出结果");
        exportButton.addActionListener(this::handleExportAction);
        
        // 清除按钮
        JButton clearButton = new JButton("清除");
        clearButton.addActionListener(this::handleClearAction);
        
        buttonPanel.add(analyzeButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(clearButton);
        
        return buttonPanel;
    }
    
    /**
     * 创建标签页
     */
    private void createTabs() {
        // 分析结果标签页
        analysisTextArea = createTextArea();
        tabbedPane.addTab(TabType.ANALYSIS.getDisplayName(), new JBScrollPane(analysisTextArea));
        
        // 日志标签页
        logTextArea = createTextArea();
        tabbedPane.addTab(TabType.LOGS.getDisplayName(), new JBScrollPane(logTextArea));
        
        // 统计标签页
        statisticsTextArea = createTextArea();
        tabbedPane.addTab(TabType.STATISTICS.getDisplayName(), new JBScrollPane(statisticsTextArea));
        
        // 历史标签页
        historyTextArea = createTextArea();
        tabbedPane.addTab(TabType.HISTORY.getDisplayName(), new JBScrollPane(historyTextArea));
        
        // 设置默认选中
        tabbedPane.setSelectedIndex(0);
    }
    
    /**
     * 创建文本区域
     */
    private JTextArea createTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setBackground(Color.WHITE);
        textArea.setForeground(Color.BLACK);
        return textArea;
    }
    
    /**
     * 注册事件监听器
     */
    private void registerEventListeners() {
        // 监听扫描开始事件
        eventBus.register(PluginEvents.ScanStartedEvent.class, new EventBus.EventListener<PluginEvents.ScanStartedEvent>() {
            @Override
            public void handle(PluginEvents.ScanStartedEvent event) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    windowState.setAnalyzing(true);
                    windowState.setProgress(0.0);
                    updateStatusBar();
                    appendLog("开始扫描: " + event.getSource());
                    appendHistory("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] 开始扫描");
                });
            }
        });
        
        // 监听扫描完成事件
        eventBus.register(PluginEvents.ScanCompletedEvent.class, new EventBus.EventListener<PluginEvents.ScanCompletedEvent>() {
            @Override
            public void handle(PluginEvents.ScanCompletedEvent event) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    windowState.setAnalyzing(false);
                    windowState.setProgress(1.0);
                    windowState.incrementTotal();
                    windowState.incrementSuccessful();
                    updateStatusBar();
                    updateAnalysisResults(event);
                    updateStatistics();
                    appendLog("扫描完成: 找到 " + event.getItemsFound() + " 个Bean");
                    appendHistory("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] 扫描完成，找到 " + event.getItemsFound() + " 个Bean");
                });
            }
        });
        
        // 监听错误事件
        eventBus.register(PluginEvents.ErrorEvent.class, new EventBus.EventListener<PluginEvents.ErrorEvent>() {
            @Override
            public void handle(PluginEvents.ErrorEvent event) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    windowState.incrementFailed();
                    appendLog("错误: " + event.getErrorMessage());
                    appendHistory("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] 错误: " + event.getErrorMessage());
                });
            }
        });
    }
    
    /**
     * 注册工具栏操作
     */
    private void registerToolbarActions(ToolWindow toolWindow) {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        
        // 刷新操作
        AnAction refreshAction = new AnAction("刷新", "刷新分析结果", com.intellij.icons.AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                handleRefreshAction();
            }
        };
        actionGroup.add(refreshAction);
        
        // 设置操作
        AnAction settingsAction = new AnAction("设置", "打开设置", com.intellij.icons.AllIcons.General.Settings) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                handleSettingsAction();
            }
        };
        actionGroup.add(settingsAction);
        
        toolWindow.setTitleActions(java.util.Arrays.asList(refreshAction, settingsAction));
    }
    
    /**
     * 处理分析操作
     */
    private void handleAnalyzeAction(ActionEvent e) {
        if (windowState.isAnalyzing()) {
            Messages.showInfoMessage(project, "分析正在进行中，请稍候...", "提示");
            return;
        }
        
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "LLM分析", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    // 模拟分析过程
                    for (int i = 0; i <= 100; i += 5) {
                        if (indicator.isCanceled()) {
                            break;
                        }
                        
                        final int progress = i;
                        ApplicationManager.getApplication().invokeLater(() -> {
                            windowState.setProgress(progress / 100.0);
                            windowState.setStatusMessage("分析中... " + progress + "%");
                            updateStatusBar();
                        });
                        
                        Thread.sleep(100);
                    }
                    
                    // 发布扫描完成事件
                    eventBus.publish(new PluginEvents.ScanCompletedEvent(
                        "EnhancedLlmAnalysisToolWindow", 
                        "BEAN_SCAN", 
                        42, // 模拟找到的Bean数量
                        System.currentTimeMillis() - 5000
                    ));
                    
                } catch (Exception ex) {
                    OptimizedLogUtil.error("分析过程出错", ex);
                    eventBus.publish(new PluginEvents.ErrorEvent(
                        "EnhancedLlmAnalysisToolWindow", 
                        "ANALYSIS_ERROR", 
                        ex.getMessage(),
                        ex,
                        "UI_ANALYSIS"
                    ));
                }
            }
        });
    }
    
    /**
     * 处理导出操作
     */
    private void handleExportAction(ActionEvent e) {
        if (analysisResults.isEmpty()) {
            Messages.showInfoMessage(project, "没有可导出的分析结果", "提示");
            return;
        }
        
        // 选择保存位置
        File selectedFile = chooseSaveFile();
        if (selectedFile == null) {
            return;
        }
        
        // 导出结果
        try (FileWriter writer = new FileWriter(selectedFile)) {
            writer.write("# LiteWorkspace 分析结果\n");
            writer.write("生成时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n");
            
            writer.write("## 分析结果\n");
            writer.write(analysisTextArea.getText());
            
            writer.write("\n\n## 统计信息\n");
            writer.write(statisticsTextArea.getText());
            
            writer.write("\n\n## 操作历史\n");
            writer.write(historyTextArea.getText());
            
            Messages.showInfoMessage(project, "导出成功: " + selectedFile.getAbsolutePath(), "成功");
            appendLog("结果已导出到: " + selectedFile.getAbsolutePath());
            
        } catch (IOException ex) {
            OptimizedLogUtil.error("导出失败", ex);
            Messages.showErrorDialog(project, "导出失败: " + ex.getMessage(), "错误");
        }
    }
    
    /**
     * 处理清除操作
     */
    private void handleClearAction(ActionEvent e) {
        int result = Messages.showYesNoDialog(project, "确定要清除所有内容吗？", "确认", Messages.getQuestionIcon());
        if (result == Messages.YES) {
            analysisResults.clear();
            analysisTextArea.setText("");
            logTextArea.setText("");
            statisticsTextArea.setText("");
            historyTextArea.setText("");
            logBuffer.setLength(0);
            historyBuffer.setLength(0);
            appendLog("已清除所有内容");
        }
    }
    
    /**
     * 处理刷新操作
     */
    private void handleRefreshAction() {
        updateStatistics();
        appendLog("已刷新统计信息");
    }
    
    /**
     * 处理设置操作
     */
    private void handleSettingsAction() {
        // TODO: 打开设置对话框
        Messages.showInfoMessage(project, "设置功能开发中...", "提示");
    }
    
    /**
     * 更新状态栏
     */
    private void updateStatusBar() {
        if (progressBar != null) {
            int progressValue = (int) (windowState.getProgress() * 100);
            progressBar.setValue(progressValue);
            progressBar.setString(progressValue + "%");
        }
        
        if (statusLabel != null) {
            statusLabel.setText(windowState.getStatusMessage());
        }
    }
    
    /**
     * 更新分析结果
     */
    private void updateAnalysisResults(PluginEvents.ScanCompletedEvent event) {
        StringBuilder result = new StringBuilder();
        result.append("扫描完成！\n");
        result.append("耗时: ").append(event.getDuration()).append("ms\n");
        result.append("找到Bean数量: ").append(event.getItemsFound()).append("\n\n");
        
        result.append("Bean列表:\n");
        result.append("- UserService\n");
        result.append("- OrderService\n");
        result.append("- ProductRepository\n");
        result.append("- [更多...]\n");
        
        analysisTextArea.setText(result.toString());
        analysisResults.put("latest", result.toString());
    }
    
    /**
     * 更新统计信息
     */
    private void updateStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("## 分析统计\n\n");
        stats.append("总分析次数: ").append(windowState.getTotalAnalyses()).append("\n");
        stats.append("成功次数: ").append(windowState.getSuccessfulAnalyses()).append("\n");
        stats.append("失败次数: ").append(windowState.getFailedAnalyses()).append("\n");
        stats.append("成功率: ").append(String.format("%.2f%%", windowState.getSuccessRate() * 100)).append("\n\n");
        
        stats.append("## 系统信息\n\n");
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        
        stats.append("内存使用: ").append(usedMemory).append("MB / ").append(totalMemory).append("MB\n");
        stats.append("可用内存: ").append(freeMemory).append("MB\n");
        stats.append("处理器数量: ").append(runtime.availableProcessors()).append("\n");
        
        statisticsTextArea.setText(stats.toString());
    }
    
    /**
     * 追加日志
     */
    private void appendLog(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String logEntry = "[" + timestamp + "] " + message + "\n";
        
        synchronized (logBuffer) {
            logBuffer.append(logEntry);
            // 限制日志长度
            if (logBuffer.length() > 50000) {
                String content = logBuffer.toString();
                logBuffer.setLength(0);
                logBuffer.append(content.substring(content.length() - 40000));
            }
        }
        
        ApplicationManager.getApplication().invokeLater(() -> {
            if (logTextArea != null) {
                logTextArea.setText(logBuffer.toString());
                // 滚动到底部
                logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
            }
        });
    }
    
    /**
     * 追加历史记录
     */
    private void appendHistory(String message) {
        String historyEntry = message + "\n";
        
        synchronized (historyBuffer) {
            historyBuffer.append(historyEntry);
            // 限制历史长度
            if (historyBuffer.length() > 10000) {
                String content = historyBuffer.toString();
                historyBuffer.setLength(0);
                historyBuffer.append(content.substring(content.length() - 8000));
            }
        }
        
        ApplicationManager.getApplication().invokeLater(() -> {
            if (historyTextArea != null) {
                historyTextArea.setText(historyBuffer.toString());
                // 滚动到底部
                historyTextArea.setCaretPosition(historyTextArea.getDocument().getLength());
            }
        });
    }
    
    /**
     * 选择保存文件
     */
    private File chooseSaveFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择保存位置");
        fileChooser.setSelectedFile(new File("lite-workspace-analysis-" + 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".md"));
        
        int result = fileChooser.showSaveDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        }
        return null;
    }
    
    /**
     * 获取窗口状态
     */
    public WindowState getWindowState() {
        return windowState;
    }
    
    /**
     * 设置分析结果
     */
    public void setAnalysisResult(String key, String result) {
        analysisResults.put(key, result);
        if ("latest".equals(key)) {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (analysisTextArea != null) {
                    analysisTextArea.setText(result);
                }
            });
        }
    }
    
    /**
     * 添加自定义日志
     */
    public void addLog(String message) {
        appendLog(message);
    }
}
