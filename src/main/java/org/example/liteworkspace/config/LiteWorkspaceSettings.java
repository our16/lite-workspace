package org.example.liteworkspace.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * LiteWorkspace 设置类
 * 重构后使用 ConfigurationManager 作为统一的配置管理器
 * 此类保持向后兼容性，委托给 ConfigurationManager
 */
@State(name = "DifySettings", storages = @Storage("LiteWorkspaceSettings.xml"))
public class LiteWorkspaceSettings implements PersistentStateComponent<LiteWorkspaceSettings.State> {

    public static class State {
        public String apiKey = "";
        public String apiUrl = "http://localhost/v1/chat-messages";
        public String modelName = "local";
        public String javaHome = "";
    }

    private State state = new State();
    private ConfigurationManager configManager;

    public LiteWorkspaceSettings() {
        // 获取配置管理器实例
        this.configManager = ConfigurationManager.getInstance();
    }

    @Override
    public @Nullable State getState() {
        // 为了向后兼容，保持原有的状态结构
        syncFromConfigurationManager();
        return state;
    }

    @Override
    public void loadState(State state) {
        this.state = state;
        // 同步到 ConfigurationManager
        syncToConfigurationManager();
    }

    /**
     * 从 ConfigurationManager 同步状态
     */
    private void syncFromConfigurationManager() {
        if (configManager != null) {
            state.apiKey = configManager.getApiKey();
            state.apiUrl = configManager.getApiUrl();
            state.modelName = configManager.getModelName();
            state.javaHome = configManager.getJavaHome();
        }
    }

    /**
     * 同步状态到 ConfigurationManager
     */
    private void syncToConfigurationManager() {
        if (configManager != null) {
            configManager.setApiKey(state.apiKey);
            configManager.setApiUrl(state.apiUrl);
            configManager.setModelName(state.modelName);
            configManager.setJavaHome(state.javaHome);
        }
    }

    /**
     * 获取实例（向后兼容）
     */
    public static LiteWorkspaceSettings getInstance() {
        return com.intellij.openapi.application.ApplicationManager.getApplication().getService(LiteWorkspaceSettings.class);
    }

    /**
     * 获取项目特定的实例
     */
    public static LiteWorkspaceSettings getInstance(Project project) {
        return project.getService(LiteWorkspaceSettings.class);
    }

    /**
     * 获取配置管理器（推荐使用）
     */
    public ConfigurationManager getConfigurationManager() {
        return configManager;
    }

    // Getter 方法 - 委托给 ConfigurationManager
    public String getApiKey() {
        return configManager != null ? configManager.getApiKey() : state.apiKey;
    }

    public String getApiUrl() {
        return configManager != null ? configManager.getApiUrl() : state.apiUrl;
    }

    public String getModelName() {
        return configManager != null ? configManager.getModelName() : state.modelName;
    }

    public String getJavaHome() {
        return configManager != null ? configManager.getJavaHome() : state.javaHome;
    }

    // Setter 方法 - 委托给 ConfigurationManager
    public void setApiKey(String apiKey) {
        state.apiKey = apiKey;
        if (configManager != null) {
            configManager.setApiKey(apiKey);
        }
    }

    public void setApiUrl(String apiUrl) {
        state.apiUrl = apiUrl;
        if (configManager != null) {
            configManager.setApiUrl(apiUrl);
        }
    }

    public void setModelName(String modelName) {
        state.modelName = modelName;
        if (configManager != null) {
            configManager.setModelName(modelName);
        }
    }

    public void setJavaHome(String javaHome) {
        state.javaHome = javaHome;
        if (configManager != null) {
            configManager.setJavaHome(javaHome);
        }
    }

    /**
     * 添加配置监听器
     */
    public void addConfigurationListener(ConfigurationManager.ConfigurationListener listener) {
        if (configManager != null) {
            configManager.addConfigurationListener(listener);
        }
    }

    /**
     * 移除配置监听器
     */
    public void removeConfigurationListener(ConfigurationManager.ConfigurationListener listener) {
        if (configManager != null) {
            configManager.removeConfigurationListener(listener);
        }
    }

    /**
     * 获取配置统计信息
     */
    public ConfigurationManager.ConfigurationStatistics getConfigurationStatistics() {
        return configManager != null ? configManager.getStatistics() : 
            new ConfigurationManager.ConfigurationStatistics(0, 0);
    }
}
