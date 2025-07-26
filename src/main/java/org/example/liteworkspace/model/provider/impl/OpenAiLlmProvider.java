package org.example.liteworkspace.model.provider.impl;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import org.example.liteworkspace.model.provider.LlmProvider;

import java.time.Duration;
import java.util.Collections;

public class OpenAiLlmProvider implements LlmProvider {
    private final String apiKey;
    private final String apiUrl;

    public OpenAiLlmProvider(String apiKey, String apiUrl) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
    }

    @Override
    public String invoke(String prompt) throws Exception {
        OpenAiService service = new OpenAiService(apiKey, Duration.ofSeconds(60));
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo") // 或从配置读取
                .messages(Collections.singletonList(
                        new ChatMessage("user", prompt)
                ))
                .build();
        return service.createChatCompletion(request).getChoices().get(0).getMessage().getContent();
    }
}
