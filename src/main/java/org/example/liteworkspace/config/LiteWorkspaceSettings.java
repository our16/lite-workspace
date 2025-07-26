package org.example.liteworkspace.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.Nullable;

@State(name = "DifySettings", storages = @Storage("LiteWorkspaceSettings.xml"))
public class LiteWorkspaceSettings implements PersistentStateComponent<LiteWorkspaceSettings.State> {

    public static class State {
        public String apiKey = "";
        public String apiUrl = "http://localhost/v1/chat-messages";
        public String modelName = "local";
    }

    private State state = new State();

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(State state) {
        this.state = state;
    }

    public static LiteWorkspaceSettings getInstance() {
        return com.intellij.openapi.application.ApplicationManager.getApplication().getService(LiteWorkspaceSettings.class);
    }

    public String getApiKey() {
        return state.apiKey;
    }

    public String getApiUrl() {
        return state.apiUrl;
    }

    public String getModelName() {
        return state.modelName;
    }

    public void setApiKey(String apiKey) {
        state.apiKey = apiKey;
    }

    public void setApiUrl(String apiUrl) {
        state.apiUrl = apiUrl;
    }


}
