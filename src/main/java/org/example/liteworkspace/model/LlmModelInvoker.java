package org.example.liteworkspace.model;


import org.example.liteworkspace.config.LiteWorkspaceSettings;
import org.example.liteworkspace.model.provider.LlmProvider;
import org.example.liteworkspace.model.provider.impl.DifyLlmProvider;
import org.example.liteworkspace.model.provider.impl.OpenAiLlmProvider;

import java.util.Objects;

public class LlmModelInvoker {

    private final LlmProvider provider;

    // 通过配置决定使用哪个 Provider
    public LlmModelInvoker() {
        LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
        if (Objects.equals(settings.getModelName(), "local")) {
            this.provider = new DifyLlmProvider(); // 或 LocalModelProvider
        } else {
            this.provider = new OpenAiLlmProvider(settings.getApiKey(), settings.getApiUrl());
        }
    }

    public  String invoke(String prompt) throws Exception {
        return provider.invoke(prompt);
    }
}

