package org.example.liteworkspace.service;

import com.intellij.openapi.project.Project;
import org.example.liteworkspace.config.LiteWorkspaceSettings;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 配置服务接口
 * 负责插件配置的管理、验证和热更新
 */
public interface ConfigurationService {
    
    /**
     * 获取插件设置
     * 
     * @param project 项目
     * @return 插件设置
     */
    LiteWorkspaceSettings getSettings(Project project);
    
    /**
     * 更新插件设置
     * 
     * @param project 项目
     * @param settings 新的设置
     * @return 更新是否成功
     */
    boolean updateSettings(Project project, LiteWorkspaceSettings settings);
    
    /**
     * 验证配置的有效性
     * 
     * @param settings 要验证的设置
     * @return 验证结果
     */
    ConfigurationValidationResult validateSettings(LiteWorkspaceSettings settings);
    
    /**
     * 获取默认配置
     * 
     * @return 默认配置
     */
    LiteWorkspaceSettings getDefaultSettings();
    
    /**
     * 重置为默认配置
     * 
     * @param project 项目
     * @return 重置是否成功
     */
    boolean resetToDefaults(Project project);
    
    /**
     * 导出配置
     * 
     * @param project 项目
     * @param filePath 导出文件路径
     * @return 导出是否成功
     */
    CompletableFuture<Boolean> exportSettings(Project project, String filePath);
    
    /**
     * 导入配置
     * 
     * @param project 项目
     * @param filePath 导入文件路径
     * @return 导入是否成功
     */
    CompletableFuture<Boolean> importSettings(Project project, String filePath);
    
    /**
     * 获取配置变更监听器
     * 
     * @return 配置变更监听器
     */
    ConfigurationChangeListener getChangeListener();
    
    /**
     * 添加配置变更监听器
     * 
     * @param listener 监听器
     */
    void addConfigurationChangeListener(ConfigurationChangeListener listener);
    
    /**
     * 移除配置变更监听器
     * 
     * @param listener 监听器
     */
    void removeConfigurationChangeListener(ConfigurationChangeListener listener);
    
    /**
     * 配置验证结果
     */
    class ConfigurationValidationResult {
        private final boolean valid;
        private final Map<String, String> errors;
        private final Map<String, String> warnings;
        
        public ConfigurationValidationResult(boolean valid, Map<String, String> errors, Map<String, String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public Map<String, String> getErrors() {
            return errors;
        }
        
        public Map<String, String> getWarnings() {
            return warnings;
        }
        
        public static ConfigurationValidationResult valid() {
            return new ConfigurationValidationResult(true, Map.of(), Map.of());
        }
        
        public static ConfigurationValidationResult invalid(Map<String, String> errors) {
            return new ConfigurationValidationResult(false, errors, Map.of());
        }
        
        public static ConfigurationValidationResult withWarnings(Map<String, String> warnings) {
            return new ConfigurationValidationResult(true, Map.of(), warnings);
        }
    }
    
    /**
     * 配置变更监听器
     */
    interface ConfigurationChangeListener {
        /**
         * 配置变更时调用
         * 
         * @param project 项目
         * @param oldSettings 旧设置
         * @param newSettings 新设置
         */
        void onConfigurationChanged(Project project, LiteWorkspaceSettings oldSettings, LiteWorkspaceSettings newSettings);
    }
}
