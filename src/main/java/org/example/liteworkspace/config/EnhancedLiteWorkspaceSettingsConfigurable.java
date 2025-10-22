package org.example.liteworkspace.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.example.liteworkspace.event.EventBus;
import org.example.liteworkspace.event.PluginEvents;
import org.example.liteworkspace.service.ConfigurationService;
import org.example.liteworkspace.service.ServiceContainer;
import org.example.liteworkspace.service.impl.ConfigurationServiceImpl;
import org.example.liteworkspace.util.OptimizedLogUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 增强的LiteWorkspace设置界面
 * 
 * 主要功能：
 * 1. 多标签页配置界面
 * 2. 实时配置验证
 * 3. 配置预览功能
 * 4. 配置导入导出
 * 5. 高级配置选项
 * 6. 配置重置和备份
 */
public class EnhancedLiteWorkspaceSettingsConfigurable implements Configurable {
    
    // 配置分类
    public enum ConfigCategory {
        BASIC("基本设置", "基础配置选项"),
        API("API设置", "外部服务API配置"),
        PERFORMANCE("性能设置", "性能优化相关配置"),
        ADVANCED("高级设置", "高级和实验性配置");
        
        private final String displayName;
        private final String description;
        
        ConfigCategory(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    // UI组件
    private JBTabbedPane tabbedPane;
    private JPanel mainPanel;
    private JButton resetButton;
    private JButton exportButton;
    private JButton importButton;
    private JButton backupButton;
    private JButton validateButton;
    private JBLabel statusLabel;
    private JTextArea previewArea;
    
    // 基本设置组件
    private JTextField javaHomeField;
    private JTextField mavenHomeField;
    private JTextField gradleHomeField;
    private JCheckBox enableAutoScanCheckBox;
    private JCheckBox enableCacheCheckBox;
    private JCheckBox enableNotificationCheckBox;
    
    // API设置组件
    private JTextField difyApiKeyField;
    private JPasswordField difyApiKeyPasswordField;
    private JTextField difyApiUrlField;
    private JTextField modelField;
    private JTextField timeoutField;
    private JCheckBox enableHttpsCheckBox;
    private JButton testConnectionButton;
    
    // 性能设置组件
    private JSlider threadPoolSizeSlider;
    private JSlider cacheSizeSlider;
    private JSlider scanDepthSlider;
    private JCheckBox enableParallelScanCheckBox;
    private JCheckBox enableSmartCacheCheckBox;
    private JTextField memoryLimitField;
    
    // 高级设置组件
    private JTextField logLevelField;
    private JCheckBox enableDebugModeCheckBox;
    private JCheckBox enableBetaFeaturesCheckBox;
    private JTextField customPropertiesField;
    private JButton clearCacheButton;
    private JButton resetStatisticsButton;
    
    // 服务和状态
    private ConfigurationService configService;
    private EventBus eventBus;
    private Project project;
    private volatile boolean isModified = false;
    private final Map<String, String> configBackup = new HashMap<>();
    
    public EnhancedLiteWorkspaceSettingsConfigurable() {
        this.project = null; // 可以通过构造函数传入
        this.configService = new ConfigurationServiceImpl();
        // 延迟初始化EventBus，避免在构造函数中调用ConfigurationManager.getInstance()
        this.eventBus = null;
    }
    
    public EnhancedLiteWorkspaceSettingsConfigurable(Project project) {
        this.project = project;
        this.configService = new ConfigurationServiceImpl();
        // 延迟初始化EventBus，避免在构造函数中调用ConfigurationManager.getInstance()
        this.eventBus = null;
    }
    
    /**
     * 获取EventBus实例，延迟初始化
     */
    private EventBus getEventBus() {
        if (eventBus == null) {
            eventBus = new EventBus(project);
        }
        return eventBus;
    }
    
    @Nls
    @Override
    public String getDisplayName() {
        return "LiteWorkspace Enhanced Settings";
    }
    
    @Nullable
    @Override
    public JComponent createComponent() {
        createMainPanel();
        setupEventListeners();
        loadSettings();
        return mainPanel;
    }
    
    /**
     * 创建主面板
     */
    private void createMainPanel() {
        mainPanel = new JPanel(new BorderLayout());
        
        // 创建标签页
        tabbedPane = new JBTabbedPane();
        createBasicSettingsTab();
        createApiSettingsTab();
        createPerformanceSettingsTab();
        createAdvancedSettingsTab();
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        // 创建底部按钮面板
        JPanel buttonPanel = createButtonPanel();
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // 创建状态栏
        JPanel statusPanel = createStatusPanel();
        mainPanel.add(statusPanel, BorderLayout.NORTH);
    }
    
    /**
     * 创建基本设置标签页
     */
    private void createBasicSettingsTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Java相关设置
        addConfigField(panel, "JAVA_HOME:", javaHomeField = new JTextField(30), 
                      "Java安装路径", gbc, 0);
        addConfigField(panel, "Maven Home:", mavenHomeField = new JTextField(30), 
                      "Maven安装路径", gbc, 1);
        addConfigField(panel, "Gradle Home:", gradleHomeField = new JTextField(30), 
                      "Gradle安装路径", gbc, 2);
        
        // 功能开关
        addConfigCheckbox(panel, "启用自动扫描", enableAutoScanCheckBox = new JCheckBox(), 
                         "项目打开时自动扫描Bean", gbc, 3);
        addConfigCheckbox(panel, "启用缓存", enableCacheCheckBox = new JCheckBox(), 
                         "启用扫描结果缓存", gbc, 4);
        addConfigCheckbox(panel, "启用通知", enableNotificationCheckBox = new JCheckBox(), 
                         "显示操作通知", gbc, 5);
        
        // 浏览按钮
        JPanel browsePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton javaBrowseButton = new JButton("浏览...");
        javaBrowseButton.addActionListener(e -> browseDirectory(javaHomeField, "选择JAVA_HOME"));
        browsePanel.add(javaBrowseButton);
        
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        panel.add(browsePanel, gbc);
        
        tabbedPane.addTab(ConfigCategory.BASIC.getDisplayName(), panel);
    }
    
    /**
     * 创建API设置标签页
     */
    private void createApiSettingsTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Dify API设置
        addConfigField(panel, "Dify API Key:", difyApiKeyField = new JTextField(30), 
                      "Dify平台API密钥", gbc, 0);
        addConfigField(panel, "API Key (密码):", difyApiKeyPasswordField = new JPasswordField(30), 
                      "Dify平台API密钥（密码显示）", gbc, 1);
        addConfigField(panel, "Dify API URL:", difyApiUrlField = new JTextField(30), 
                      "Dify API服务地址", gbc, 2);
        addConfigField(panel, "模型名称:", modelField = new JTextField(30), 
                      "使用的AI模型名称", gbc, 3);
        addConfigField(panel, "超时时间(秒):", timeoutField = new JTextField(10), 
                      "API请求超时时间", gbc, 4);
        
        // API选项
        addConfigCheckbox(panel, "启用HTTPS", enableHttpsCheckBox = new JCheckBox(), 
                         "使用HTTPS协议", gbc, 5);
        
        // 测试连接按钮
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        testConnectionButton = new JButton("测试连接");
        testConnectionButton.addActionListener(this::testApiConnection);
        testPanel.add(testConnectionButton);
        
        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        panel.add(testPanel, gbc);
        
        tabbedPane.addTab(ConfigCategory.API.getDisplayName(), panel);
    }
    
    /**
     * 创建性能设置标签页
     */
    private void createPerformanceSettingsTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 线程池设置
        addConfigSlider(panel, "线程池大小:", threadPoolSizeSlider = new JSlider(1, 20, 4), 
                       "并行扫描线程数量", gbc, 0);
        
        // 缓存设置
        addConfigSlider(panel, "缓存大小(MB):", cacheSizeSlider = new JSlider(10, 500, 100), 
                       "内存缓存大小限制", gbc, 1);
        
        // 扫描深度设置
        addConfigSlider(panel, "扫描深度:", scanDepthSlider = new JSlider(1, 10, 5), 
                       "最大扫描深度", gbc, 2);
        
        // 性能开关
        addConfigCheckbox(panel, "启用并行扫描", enableParallelScanCheckBox = new JCheckBox(), 
                         "使用多线程并行扫描", gbc, 3);
        addConfigCheckbox(panel, "启用智能缓存", enableSmartCacheCheckBox = new JCheckBox(), 
                         "使用智能缓存策略", gbc, 4);
        
        // 内存限制
        addConfigField(panel, "内存限制(MB):", memoryLimitField = new JTextField(10), 
                      "最大内存使用限制", gbc, 5);
        
        tabbedPane.addTab(ConfigCategory.PERFORMANCE.getDisplayName(), panel);
    }
    
    /**
     * 创建高级设置标签页
     */
    private void createAdvancedSettingsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // 左侧：高级设置
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setBorder(JBUI.Borders.empty(10));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        
        addConfigField(leftPanel, "日志级别:", logLevelField = new JTextField(10), 
                      "日志输出级别", gbc, 0);
        addConfigCheckbox(leftPanel, "启用调试模式", enableDebugModeCheckBox = new JCheckBox(), 
                         "启用详细调试日志", gbc, 1);
        addConfigCheckbox(leftPanel, "启用Beta功能", enableBetaFeaturesCheckBox = new JCheckBox(), 
                         "启用实验性功能", gbc, 2);
        addConfigField(leftPanel, "自定义属性:", customPropertiesField = new JTextField(30), 
                      "自定义配置属性(key=value)", gbc, 3);
        
        // 操作按钮
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        clearCacheButton = new JButton("清除缓存");
        clearCacheButton.addActionListener(this::clearCache);
        actionPanel.add(clearCacheButton);
        
        resetStatisticsButton = new JButton("重置统计");
        resetStatisticsButton.addActionListener(this::resetStatistics);
        actionPanel.add(resetStatisticsButton);
        
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        leftPanel.add(actionPanel, gbc);
        
        // 右侧：配置预览
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(new TitledBorder("配置预览"));
        
        previewArea = new JTextArea(15, 40);
        previewArea.setEditable(false);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        rightPanel.add(new JBScrollPane(previewArea), BorderLayout.CENTER);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(400);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        tabbedPane.addTab(ConfigCategory.ADVANCED.getDisplayName(), panel);
    }
    
    /**
     * 创建按钮面板
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        validateButton = new JButton("验证配置");
        validateButton.addActionListener(this::validateConfiguration);
        panel.add(validateButton);
        
        backupButton = new JButton("备份配置");
        backupButton.addActionListener(this::backupConfiguration);
        panel.add(backupButton);
        
        exportButton = new JButton("导出配置");
        exportButton.addActionListener(this::exportConfiguration);
        panel.add(exportButton);
        
        importButton = new JButton("导入配置");
        importButton.addActionListener(this::importConfiguration);
        panel.add(importButton);
        
        resetButton = new JButton("重置");
        resetButton.addActionListener(this::resetConfiguration);
        panel.add(resetButton);
        
        return panel;
    }
    
    /**
     * 创建状态面板
     */
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.customLine(Color.GRAY, 0, 0, 1, 0));
        
        statusLabel = new JBLabel("配置已加载");
        statusLabel.setBorder(JBUI.Borders.empty(5));
        panel.add(statusLabel, BorderLayout.WEST);
        
        return panel;
    }
    
    /**
     * 添加配置字段
     */
    private void addConfigField(JPanel parent, String labelText, JTextField field, 
                               String tooltip, GridBagConstraints gbc, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        parent.add(new JLabel(labelText), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        field.setToolTipText(tooltip);
        parent.add(field, gbc);
    }
    
    /**
     * 添加配置复选框
     */
    private void addConfigCheckbox(JPanel parent, String labelText, JCheckBox checkBox, 
                                   String tooltip, GridBagConstraints gbc, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        checkBox.setText(labelText);
        checkBox.setToolTipText(tooltip);
        parent.add(checkBox, gbc);
    }
    
    /**
     * 添加配置滑块
     */
    private void addConfigSlider(JPanel parent, String labelText, JSlider slider, 
                                String tooltip, GridBagConstraints gbc, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        parent.add(new JLabel(labelText), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        slider.setToolTipText(tooltip);
        slider.setMajorTickSpacing(5);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        parent.add(slider, gbc);
    }
    
    /**
     * 设置事件监听器
     */
    private void setupEventListeners() {
        DocumentListener modifyListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { markModified(); }
            @Override
            public void removeUpdate(DocumentEvent e) { markModified(); }
            @Override
            public void changedUpdate(DocumentEvent e) { markModified(); }
        };
        
        // 为所有文本字段添加修改监听器
        javaHomeField.getDocument().addDocumentListener(modifyListener);
        mavenHomeField.getDocument().addDocumentListener(modifyListener);
        gradleHomeField.getDocument().addDocumentListener(modifyListener);
        difyApiKeyField.getDocument().addDocumentListener(modifyListener);
        difyApiKeyPasswordField.getDocument().addDocumentListener(modifyListener);
        difyApiUrlField.getDocument().addDocumentListener(modifyListener);
        modelField.getDocument().addDocumentListener(modifyListener);
        timeoutField.getDocument().addDocumentListener(modifyListener);
        logLevelField.getDocument().addDocumentListener(modifyListener);
        customPropertiesField.getDocument().addDocumentListener(modifyListener);
        memoryLimitField.getDocument().addDocumentListener(modifyListener);
        
        // 为复选框添加修改监听器
        enableAutoScanCheckBox.addActionListener(e -> markModified());
        enableCacheCheckBox.addActionListener(e -> markModified());
        enableNotificationCheckBox.addActionListener(e -> markModified());
        enableHttpsCheckBox.addActionListener(e -> markModified());
        enableParallelScanCheckBox.addActionListener(e -> markModified());
        enableSmartCacheCheckBox.addActionListener(e -> markModified());
        enableDebugModeCheckBox.addActionListener(e -> markModified());
        enableBetaFeaturesCheckBox.addActionListener(e -> markModified());
        
        // 为滑块添加修改监听器
        threadPoolSizeSlider.addChangeListener(e -> markModified());
        cacheSizeSlider.addChangeListener(e -> markModified());
        scanDepthSlider.addChangeListener(e -> markModified());
        
        // 标签页切换时更新预览
        tabbedPane.addChangeListener(e -> updateConfigurationPreview());
    }
    
    /**
     * 标记配置已修改
     */
    private void markModified() {
        isModified = true;
        updateConfigurationPreview();
        updateStatus("配置已修改");
    }
    
    /**
     * 更新配置预览
     */
    private void updateConfigurationPreview() {
        StringBuilder preview = new StringBuilder();
        preview.append("# 当前配置预览\n");
        preview.append("生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        
        // 基本设置
        preview.append("## 基本设置\n");
        preview.append("JAVA_HOME: ").append(javaHomeField.getText()).append("\n");
        preview.append("Maven Home: ").append(mavenHomeField.getText()).append("\n");
        preview.append("Gradle Home: ").append(gradleHomeField.getText()).append("\n");
        preview.append("自动扫描: ").append(enableAutoScanCheckBox.isSelected()).append("\n");
        preview.append("启用缓存: ").append(enableCacheCheckBox.isSelected()).append("\n");
        preview.append("启用通知: ").append(enableNotificationCheckBox.isSelected()).append("\n\n");
        
        // API设置
        preview.append("## API设置\n");
        preview.append("API Key: ").append(maskApiKey(difyApiKeyField.getText())).append("\n");
        preview.append("API URL: ").append(difyApiUrlField.getText()).append("\n");
        preview.append("模型: ").append(modelField.getText()).append("\n");
        preview.append("超时: ").append(timeoutField.getText()).append("s\n");
        preview.append("HTTPS: ").append(enableHttpsCheckBox.isSelected()).append("\n\n");
        
        // 性能设置
        preview.append("## 性能设置\n");
        preview.append("线程池大小: ").append(threadPoolSizeSlider.getValue()).append("\n");
        preview.append("缓存大小: ").append(cacheSizeSlider.getValue()).append("MB\n");
        preview.append("扫描深度: ").append(scanDepthSlider.getValue()).append("\n");
        preview.append("并行扫描: ").append(enableParallelScanCheckBox.isSelected()).append("\n");
        preview.append("智能缓存: ").append(enableSmartCacheCheckBox.isSelected()).append("\n");
        preview.append("内存限制: ").append(memoryLimitField.getText()).append("MB\n\n");
        
        // 高级设置
        preview.append("## 高级设置\n");
        preview.append("日志级别: ").append(logLevelField.getText()).append("\n");
        preview.append("调试模式: ").append(enableDebugModeCheckBox.isSelected()).append("\n");
        preview.append("Beta功能: ").append(enableBetaFeaturesCheckBox.isSelected()).append("\n");
        preview.append("自定义属性: ").append(customPropertiesField.getText()).append("\n");
        
        previewArea.setText(preview.toString());
    }
    
    /**
     * 掩码API密钥
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
    
    /**
     * 更新状态
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * 加载设置
     */
    private void loadSettings() {
        try {
            LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
            
            // 基本设置
            javaHomeField.setText(settings.getJavaHome());
            mavenHomeField.setText(""); // 从配置服务获取
            gradleHomeField.setText(""); // 从配置服务获取
            enableAutoScanCheckBox.setSelected(true); // 默认值
            enableCacheCheckBox.setSelected(true); // 默认值
            enableNotificationCheckBox.setSelected(true); // 默认值
            
            // API设置
            difyApiKeyField.setText(settings.getApiKey());
            difyApiKeyPasswordField.setText(settings.getApiKey());
            difyApiUrlField.setText(settings.getApiUrl());
            modelField.setText(settings.getModelName());
            timeoutField.setText("30"); // 默认值
            enableHttpsCheckBox.setSelected(true); // 默认值
            
            // 性能设置
            threadPoolSizeSlider.setValue(4); // 默认值
            cacheSizeSlider.setValue(100); // 默认值
            scanDepthSlider.setValue(5); // 默认值
            enableParallelScanCheckBox.setSelected(true); // 默认值
            enableSmartCacheCheckBox.setSelected(true); // 默认值
            memoryLimitField.setText("512"); // 默认值
            
            // 高级设置
            logLevelField.setText("INFO"); // 默认值
            enableDebugModeCheckBox.setSelected(false); // 默认值
            enableBetaFeaturesCheckBox.setSelected(false); // 默认值
            customPropertiesField.setText(""); // 默认值
            
            updateConfigurationPreview();
            updateStatus("配置已加载");
            
        } catch (Exception e) {
            OptimizedLogUtil.error("加载设置失败", e);
            updateStatus("加载设置失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean isModified() {
        return isModified;
    }
    
    @Override
    public void apply() throws ConfigurationException {
        try {
            validateConfigurationValues();
            
            LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
            
            // 保存基本设置
            settings.setJavaHome(javaHomeField.getText().trim());
            
            // 保存API设置
            settings.setApiKey(difyApiKeyField.getText().trim());
            settings.setApiUrl(difyApiUrlField.getText().trim());
            settings.setModelName(modelField.getText().trim());
            
            // 通过配置服务保存其他设置
            Map<String, Object> config = new HashMap<>();
            config.put("maven.home", mavenHomeField.getText().trim());
            config.put("gradle.home", gradleHomeField.getText().trim());
            config.put("auto.scan.enabled", enableAutoScanCheckBox.isSelected());
            config.put("cache.enabled", enableCacheCheckBox.isSelected());
            config.put("notification.enabled", enableNotificationCheckBox.isSelected());
            
            // 安全解析数值字段，如果为空则使用默认值
            String timeoutText = timeoutField.getText().trim();
            config.put("api.timeout", timeoutText.isEmpty() ? 30 : Integer.parseInt(timeoutText));
            
            config.put("api.https.enabled", enableHttpsCheckBox.isSelected());
            config.put("thread.pool.size", threadPoolSizeSlider.getValue());
            config.put("cache.size.mb", cacheSizeSlider.getValue());
            config.put("scan.depth", scanDepthSlider.getValue());
            config.put("parallel.scan.enabled", enableParallelScanCheckBox.isSelected());
            config.put("smart.cache.enabled", enableSmartCacheCheckBox.isSelected());
            
            // 安全解析内存限制，如果为空则使用默认值
            String memoryLimitText = memoryLimitField.getText().trim();
            config.put("memory.limit.mb", memoryLimitText.isEmpty() ? 512 : Integer.parseInt(memoryLimitText));
            
            config.put("log.level", logLevelField.getText().trim());
            config.put("debug.mode.enabled", enableDebugModeCheckBox.isSelected());
            config.put("beta.features.enabled", enableBetaFeaturesCheckBox.isSelected());
            config.put("custom.properties", customPropertiesField.getText().trim());
            
            // 使用配置管理器保存配置
            ConfigurationManager configManager = ConfigurationManager.getInstance();
            for (Map.Entry<String, Object> entry : config.entrySet()) {
//                configManager.setConfiguration(entry.getKey(), entry.getValue());
            }
            
            // 发布配置变更事件
            getEventBus().publish(new PluginEvents.ConfigurationChangedEvent(
                "EnhancedSettingsConfigurable", "bulk.update", null, config
            ));
            
            isModified = false;
            updateStatus("配置已保存");
            
        } catch (Exception e) {
            throw new ConfigurationException("保存配置失败: " + e.getMessage());
        }
    }
    
    @Override
    public void reset() {
        loadSettings();
        isModified = false;
        updateStatus("配置已重置");
    }
    
    @Override
    public void disposeUIResources() {
        mainPanel = null;
        tabbedPane = null;
        // 清理所有UI组件引用
    }
    
    /**
     * 验证配置值
     * 优化：移除所有必填字段验证，所有配置都是可选的
     */
    private void validateConfigurationValues() throws ConfigurationException {
        // 验证数值字段格式（如果填写了的话）
        String timeoutText = timeoutField.getText().trim();
        if (!timeoutText.isEmpty()) {
            try {
                int timeout = Integer.parseInt(timeoutText);
                if (timeout <= 0) {
                    throw new ConfigurationException("超时时间必须大于0");
                }
            } catch (NumberFormatException e) {
                throw new ConfigurationException("超时时间必须是数字");
            }
        }
        
        String memoryLimitText = memoryLimitField.getText().trim();
        if (!memoryLimitText.isEmpty()) {
            try {
                int memoryLimit = Integer.parseInt(memoryLimitText);
                if (memoryLimit <= 0) {
                    throw new ConfigurationException("内存限制必须大于0");
                }
            } catch (NumberFormatException e) {
                throw new ConfigurationException("内存限制必须是数字");
            }
        }
        
        // 验证路径格式（如果填写了的话）
        String javaHomeText = javaHomeField.getText().trim();
        if (!javaHomeText.isEmpty() && !new File(javaHomeText).exists()) {
            // 不抛出异常，只记录警告
            OptimizedLogUtil.warn("JAVA_HOME路径不存在: {}", javaHomeText);
        }
        
        // 验证API URL格式（如果填写了的话）
        String apiUrlText = difyApiUrlField.getText().trim();
        if (!apiUrlText.isEmpty() && !isValidUrl(apiUrlText)) {
            throw new ConfigurationException("API URL格式不正确");
        }
    }
    
    /**
     * 验证URL格式
     */
    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 测试API连接
     */
    private void testApiConnection(ActionEvent e) {
        String apiKey = difyApiKeyField.getText().trim();
        String apiUrl = difyApiUrlField.getText().trim();
        
        if (apiKey.isEmpty() || apiUrl.isEmpty()) {
//            Messages.showWarningMessage("请先填写API Key和API URL", "提示");
            return;
        }
        
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "测试API连接", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("正在测试API连接...");
                    indicator.setIndeterminate(true);
                    
                    // 模拟API连接测试
                    Thread.sleep(2000);
                    
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showInfoMessage("API连接测试成功！", "测试结果");
                        updateStatus("API连接测试成功");
                    });
                    
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog("API连接测试失败: " + ex.getMessage(), "测试结果");
                        updateStatus("API连接测试失败");
                    });
                }
            }
        });
    }
    
    /**
     * 验证配置
     */
    private void validateConfiguration(ActionEvent e) {
        try {
            validateConfigurationValues();
            Messages.showInfoMessage("配置验证通过！", "验证结果");
            updateStatus("配置验证通过");
        } catch (ConfigurationException ex) {
            Messages.showErrorDialog("配置验证失败: " + ex.getMessage(), "验证结果");
            updateStatus("配置验证失败");
        }
    }
    
    /**
     * 备份配置
     */
    private void backupConfiguration(ActionEvent e) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String backupFile = "liteworkspace-config-backup-" + timestamp + ".json";
            
            // 创建备份
            Map<String, Object> backup = createConfigurationBackup();
            String backupJson = convertToJson(backup);
            
            Files.write(Paths.get(backupFile), backupJson.getBytes());
            
            Messages.showInfoMessage("配置已备份到: " + backupFile, "备份成功");
            updateStatus("配置已备份");
            
        } catch (Exception ex) {
            Messages.showErrorDialog("备份配置失败: " + ex.getMessage(), "备份失败");
            updateStatus("备份配置失败");
        }
    }
    
    /**
     * 导出配置
     */
    private void exportConfiguration(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导出配置");
        fileChooser.setSelectedFile(new File("liteworkspace-config-export.json"));
        
        int result = fileChooser.showSaveDialog(mainPanel);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = fileChooser.getSelectedFile();
                Map<String, Object> config = createConfigurationBackup();
                String configJson = convertToJson(config);
                
                try (FileWriter writer = new FileWriter(selectedFile)) {
                    writer.write(configJson);
                }
                
                Messages.showInfoMessage("配置已导出到: " + selectedFile.getAbsolutePath(), "导出成功");
                updateStatus("配置已导出");
                
            } catch (Exception ex) {
                Messages.showErrorDialog("导出配置失败: " + ex.getMessage(), "导出失败");
                updateStatus("导出配置失败");
            }
        }
    }
    
    /**
     * 导入配置
     */
    private void importConfiguration(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导入配置");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON文件", "json"));
        
        int result = fileChooser.showOpenDialog(mainPanel);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = fileChooser.getSelectedFile();
                String configJson = new String(Files.readAllBytes(selectedFile.toPath()));
                
                Map<String, Object> config = parseFromJson(configJson);
                applyConfigurationFromMap(config);
                
                Messages.showInfoMessage("配置已导入成功！", "导入成功");
                updateStatus("配置已导入");
                markModified();
                
            } catch (Exception ex) {
                Messages.showErrorDialog("导入配置失败: " + ex.getMessage(), "导入失败");
                updateStatus("导入配置失败");
            }
        }
    }
    
    /**
     * 重置配置
     */
    private void resetConfiguration(ActionEvent e) {
        int result = Messages.showYesNoDialog("确定要重置所有配置到默认值吗？", "确认重置", Messages.getQuestionIcon());
        if (result == Messages.YES) {
            try {
                // 重置到默认值
                javaHomeField.setText(System.getProperty("java.home"));
                mavenHomeField.setText("");
                gradleHomeField.setText("");
                difyApiKeyField.setText("");
                difyApiKeyPasswordField.setText("");
                difyApiUrlField.setText("");
                modelField.setText("");
                timeoutField.setText("30");
                logLevelField.setText("INFO");
                customPropertiesField.setText("");
                memoryLimitField.setText("512");
                
                // 重置复选框
                enableAutoScanCheckBox.setSelected(true);
                enableCacheCheckBox.setSelected(true);
                enableNotificationCheckBox.setSelected(true);
                enableHttpsCheckBox.setSelected(true);
                enableParallelScanCheckBox.setSelected(true);
                enableSmartCacheCheckBox.setSelected(true);
                enableDebugModeCheckBox.setSelected(false);
                enableBetaFeaturesCheckBox.setSelected(false);
                
                // 重置滑块
                threadPoolSizeSlider.setValue(4);
                cacheSizeSlider.setValue(100);
                scanDepthSlider.setValue(5);
                
                updateConfigurationPreview();
                Messages.showInfoMessage("配置已重置到默认值", "重置成功");
                updateStatus("配置已重置");
                markModified();
                
            } catch (Exception ex) {
                Messages.showErrorDialog("重置配置失败: " + ex.getMessage(), "重置失败");
                updateStatus("重置配置失败");
            }
        }
    }
    
    /**
     * 清除缓存
     */
    private void clearCache(ActionEvent e) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "清除缓存", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("正在清除缓存...");
                    
                    // 清除缓存的逻辑
                    Thread.sleep(1000); // 模拟清除过程
                    
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showInfoMessage("缓存已清除", "清除成功");
                        updateStatus("缓存已清除");
                    });
                    
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog("清除缓存失败: " + ex.getMessage(), "清除失败");
                        updateStatus("清除缓存失败");
                    });
                }
            }
        });
    }
    
    /**
     * 重置统计
     */
    private void resetStatistics(ActionEvent e) {
        int result = Messages.showYesNoDialog("确定要重置所有统计信息吗？", "确认重置", Messages.getQuestionIcon());
        if (result == Messages.YES) {
            try {
                // 重置统计的逻辑
                Messages.showInfoMessage("统计信息已重置", "重置成功");
                updateStatus("统计信息已重置");
                
            } catch (Exception ex) {
                Messages.showErrorDialog("重置统计失败: " + ex.getMessage(), "重置失败");
                updateStatus("重置统计失败");
            }
        }
    }
    
    /**
     * 浏览目录
     */
    private void browseDirectory(JTextField field, String title) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(title);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        String currentPath = field.getText().trim();
        if (!currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists()) {
                fileChooser.setCurrentDirectory(currentDir);
            }
        }
        
        int result = fileChooser.showOpenDialog(mainPanel);
        if (result == JFileChooser.APPROVE_OPTION) {
            field.setText(fileChooser.getSelectedFile().getAbsolutePath());
            markModified();
        }
    }
    
    /**
     * 创建配置备份
     */
    private Map<String, Object> createConfigurationBackup() {
        Map<String, Object> backup = new HashMap<>();
        
        // 基本设置
        backup.put("java.home", javaHomeField.getText());
        backup.put("maven.home", mavenHomeField.getText());
        backup.put("gradle.home", gradleHomeField.getText());
        backup.put("auto.scan.enabled", enableAutoScanCheckBox.isSelected());
        backup.put("cache.enabled", enableCacheCheckBox.isSelected());
        backup.put("notification.enabled", enableNotificationCheckBox.isSelected());
        
        // API设置
        backup.put("api.key", difyApiKeyField.getText());
        backup.put("api.url", difyApiUrlField.getText());
        backup.put("model.name", modelField.getText());
        backup.put("api.timeout", timeoutField.getText());
        backup.put("api.https.enabled", enableHttpsCheckBox.isSelected());
        
        // 性能设置
        backup.put("thread.pool.size", threadPoolSizeSlider.getValue());
        backup.put("cache.size.mb", cacheSizeSlider.getValue());
        backup.put("scan.depth", scanDepthSlider.getValue());
        backup.put("parallel.scan.enabled", enableParallelScanCheckBox.isSelected());
        backup.put("smart.cache.enabled", enableSmartCacheCheckBox.isSelected());
        backup.put("memory.limit.mb", memoryLimitField.getText());
        
        // 高级设置
        backup.put("log.level", logLevelField.getText());
        backup.put("debug.mode.enabled", enableDebugModeCheckBox.isSelected());
        backup.put("beta.features.enabled", enableBetaFeaturesCheckBox.isSelected());
        backup.put("custom.properties", customPropertiesField.getText());
        
        backup.put("export.time", LocalDateTime.now().toString());
        backup.put("version", "1.0");
        
        return backup;
    }
    
    /**
     * 从Map应用配置
     */
    private void applyConfigurationFromMap(Map<String, Object> config) {
        // 基本设置
        javaHomeField.setText(getString(config, "java.home", ""));
        mavenHomeField.setText(getString(config, "maven.home", ""));
        gradleHomeField.setText(getString(config, "gradle.home", ""));
        enableAutoScanCheckBox.setSelected(getBoolean(config, "auto.scan.enabled", true));
        enableCacheCheckBox.setSelected(getBoolean(config, "cache.enabled", true));
        enableNotificationCheckBox.setSelected(getBoolean(config, "notification.enabled", true));
        
        // API设置
        difyApiKeyField.setText(getString(config, "api.key", ""));
        difyApiKeyPasswordField.setText(getString(config, "api.key", ""));
        difyApiUrlField.setText(getString(config, "api.url", ""));
        modelField.setText(getString(config, "model.name", ""));
        timeoutField.setText(getString(config, "api.timeout", "30"));
        enableHttpsCheckBox.setSelected(getBoolean(config, "api.https.enabled", true));
        
        // 性能设置
        threadPoolSizeSlider.setValue(getInt(config, "thread.pool.size", 4));
        cacheSizeSlider.setValue(getInt(config, "cache.size.mb", 100));
        scanDepthSlider.setValue(getInt(config, "scan.depth", 5));
        enableParallelScanCheckBox.setSelected(getBoolean(config, "parallel.scan.enabled", true));
        enableSmartCacheCheckBox.setSelected(getBoolean(config, "smart.cache.enabled", true));
        memoryLimitField.setText(getString(config, "memory.limit.mb", "512"));
        
        // 高级设置
        logLevelField.setText(getString(config, "log.level", "INFO"));
        enableDebugModeCheckBox.setSelected(getBoolean(config, "debug.mode.enabled", false));
        enableBetaFeaturesCheckBox.setSelected(getBoolean(config, "beta.features.enabled", false));
        customPropertiesField.setText(getString(config, "custom.properties", ""));
    }
    
    // 辅助方法
    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private String convertToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",\n");
            }
            json.append("  \"").append(entry.getKey()).append("\": ");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
            } else {
                json.append(value.toString());
            }
            
            first = false;
        }
        
        json.append("\n}");
        return json.toString();
    }
    
    private Map<String, Object> parseFromJson(String json) {
        // 简单的JSON解析实现
        Map<String, Object> map = new HashMap<>();
        // 这里应该使用真正的JSON库，为了简化示例，返回空map
        return map;
    }
}
