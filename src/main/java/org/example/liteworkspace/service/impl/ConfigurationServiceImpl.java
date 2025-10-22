package org.example.liteworkspace.service.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.example.liteworkspace.config.ConfigurationManager;
import org.example.liteworkspace.config.LiteWorkspaceSettings;
import org.example.liteworkspace.service.ConfigurationService;
import org.example.liteworkspace.util.LogUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 配置服务实现
 */
@Service
public final class ConfigurationServiceImpl implements ConfigurationService {
    
    private final List<ConfigurationChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<LiteWorkspaceSettings> cachedSettings = new AtomicReference<>();
    
    @Override
    public LiteWorkspaceSettings getSettings(Project project) {
        LiteWorkspaceSettings cached = cachedSettings.get();
        if (cached != null) {
            return cached;
        }
        
        LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
        cachedSettings.set(settings);
        return settings;
    }
    
    @Override
    public boolean updateSettings(Project project, LiteWorkspaceSettings settings) {
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(settings, "Settings cannot be null");
        
        // 验证设置
        ConfigurationValidationResult validation = validateSettings(settings);
        if (!validation.isValid()) {
            LogUtil.error("配置验证失败: {}", null, validation.getErrors());
            return false;
        }
        
        // 显示警告
        if (!validation.getWarnings().isEmpty()) {
            LogUtil.warn("配置警告: {}", validation.getWarnings());
        }
        
        LiteWorkspaceSettings oldSettings = getSettings(project);
        
        // 更新设置
        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                // 复制设置属性
                copySettings(settings, oldSettings);
                cachedSettings.set(oldSettings);
            } catch (Exception e) {
                LogUtil.error("更新配置失败", e);
                throw new RuntimeException("更新配置失败", e);
            }
        });
        
        // 通知监听器
        notifyConfigurationChanged(project, oldSettings, settings);
        
        LogUtil.info("配置更新成功");
        return true;
    }
    
    @Override
    public ConfigurationValidationResult validateSettings(LiteWorkspaceSettings settings) {
        Objects.requireNonNull(settings, "Settings cannot be null");
        
        Map<String, String> errors = new HashMap<>();
        Map<String, String> warnings = new HashMap<>();
        
        // 验证API Key
        if (settings.getApiKey() == null || settings.getApiKey().trim().isEmpty()) {
            warnings.put("apiKey", "API Key为空，可能影响功能使用");
        }
        
        // 验证API URL
        if (settings.getApiUrl() == null || settings.getApiUrl().trim().isEmpty()) {
            errors.put("apiUrl", "API URL不能为空");
        } else if (!settings.getApiUrl().startsWith("http://") && !settings.getApiUrl().startsWith("https://")) {
            errors.put("apiUrl", "API URL格式不正确，必须以http://或https://开头");
        }
        
        // 验证模型名称
        if (settings.getModelName() == null || settings.getModelName().trim().isEmpty()) {
            warnings.put("modelName", "模型名称为空，将使用默认模型");
        }
        
        // 验证Java Home
        if (settings.getJavaHome() != null && !settings.getJavaHome().trim().isEmpty()) {
            Path javaHomePath = Paths.get(settings.getJavaHome());
            if (!Files.exists(javaHomePath)) {
                warnings.put("javaHome", "Java Home路径不存在");
            }
        }
        
        if (errors.isEmpty()) {
            return warnings.isEmpty() ? 
                ConfigurationValidationResult.valid() : 
                ConfigurationValidationResult.withWarnings(warnings);
        } else {
            return ConfigurationValidationResult.invalid(errors);
        }
    }
    
    @Override
    public LiteWorkspaceSettings getDefaultSettings() {
        LiteWorkspaceSettings defaultSettings = new LiteWorkspaceSettings();
        defaultSettings.setApiKey("");
        defaultSettings.setApiUrl("http://localhost/v1/chat-messages");
        defaultSettings.setModelName("local");
        defaultSettings.setJavaHome("");
        return defaultSettings;
    }
    
    @Override
    public boolean resetToDefaults(Project project) {
        LiteWorkspaceSettings defaultSettings = getDefaultSettings();
        return updateSettings(project, defaultSettings);
    }
    
    @Override
    public CompletableFuture<Boolean> exportSettings(Project project, String filePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LiteWorkspaceSettings settings = getSettings(project);
                Path path = Paths.get(filePath);
                
                // 创建父目录
                Files.createDirectories(path.getParent());
                
                // 导出设置为JSON
                String json = exportToJson(settings);
                Files.write(path, json.getBytes());
                
                LogUtil.info("配置导出成功: {}", filePath);
                return true;
                
            } catch (IOException e) {
                LogUtil.error("配置导出失败: " + filePath, e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> importSettings(Project project, String filePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path path = Paths.get(filePath);
                if (!Files.exists(path)) {
                    LogUtil.error("配置文件不存在: {}", null, filePath);
                    return false;
                }
                
                String json = new String(Files.readAllBytes(path));
                LiteWorkspaceSettings settings = importFromJson(json);
                
                // 验证导入的设置
                ConfigurationValidationResult validation = validateSettings(settings);
                if (!validation.isValid()) {
                    LogUtil.error("导入的配置无效: {}", null, validation.getErrors());
                    return false;
                }
                
                boolean success = updateSettings(project, settings);
                if (success) {
                    LogUtil.info("配置导入成功: {}", filePath);
                }
                
                return success;
                
            } catch (IOException e) {
                LogUtil.error("配置导入失败: " + filePath, e);
                return false;
            }
        });
    }
    
    @Override
    public ConfigurationChangeListener getChangeListener() {
        return (project, oldSettings, newSettings) -> {
            // 默认空实现
        };
    }
    
    @Override
    public void addConfigurationChangeListener(ConfigurationChangeListener listener) {
        listeners.add(Objects.requireNonNull(listener, "Listener cannot be null"));
    }
    
    @Override
    public void removeConfigurationChangeListener(ConfigurationChangeListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 复制设置
     */
    private void copySettings(LiteWorkspaceSettings source, LiteWorkspaceSettings target) {
        target.setApiKey(source.getApiKey());
        target.setApiUrl(source.getApiUrl());
        target.setModelName(source.getModelName());
        target.setJavaHome(source.getJavaHome());
    }
    
    /**
     * 导出设置为JSON
     */
    private String exportToJson(LiteWorkspaceSettings settings) {
        // 简单的JSON导出实现
        return String.format("""
            {
              "apiKey": "%s",
              "apiUrl": "%s",
              "modelName": "%s",
              "javaHome": "%s"
            }
            """,
            settings.getApiKey(),
            settings.getApiUrl(),
            settings.getModelName(),
            settings.getJavaHome()
        );
    }
    
    /**
     * 从JSON导入设置
     */
    private LiteWorkspaceSettings importFromJson(String json) {
        // 简单的JSON导入实现，实际项目中应该使用专业的JSON库
        LiteWorkspaceSettings settings = new LiteWorkspaceSettings();
        
        // 这里应该使用JSON解析库，为了简化暂时手动解析
        // 在实际实现中应该使用Jackson或Gson等库
        
        return settings;
    }
    
    /**
     * 通知配置变更
     */
    private void notifyConfigurationChanged(Project project, 
                                           LiteWorkspaceSettings oldSettings, 
                                           LiteWorkspaceSettings newSettings) {
        for (ConfigurationChangeListener listener : listeners) {
            try {
                listener.onConfigurationChanged(project, oldSettings, newSettings);
            } catch (Exception e) {
                LogUtil.error("配置变更监听器执行失败", e);
            }
        }
    }
}
