package org.example.liteworkspace.model.provider.impl;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.example.liteworkspace.config.LiteWorkspaceSettings;
import org.example.liteworkspace.model.entity.DifyResponse;
import org.example.liteworkspace.model.provider.LlmProvider;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class DifyLlmProvider implements LlmProvider {

    private static final Gson GSON = new Gson();

    @Override
    public String invoke(String prompt) {
        LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
        String apiUrl = settings.getApiUrl();
        String apiKey = settings.getApiKey();

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // 设置请求方法、Header、Body
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // 构造请求体
            Map<String, Object> body = new HashMap<>();
            body.put("inputs", new HashMap<>()); // 可以替换为具体参数
            body.put("query", prompt);
            body.put("response_mode", "streaming");
            body.put("conversation_id", "");
            body.put("user", "abc-123");

            String jsonBody = GSON.toJson(body);

            // 发送请求体
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            // 处理响应
            int status = conn.getResponseCode();
            InputStream responseStream = (status >= 200 && status < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            StringBuilder fullAnswer = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String jsonPart = line.substring("data: ".length()).trim();

                        try {
                            DifyResponse response = GSON.fromJson(jsonPart, DifyResponse.class);

                            if (response != null) {
                                String event = response.getEvent(); // 确保你有 getEvent() 方法
                                String answer = response.getAnswer(); // 确保有 getAnswer()

                                if ("agent_message".equals(event) && answer != null && !answer.isEmpty()) {
                                    fullAnswer.append(answer);
                                } else if ("message_end".equals(event)) {
                                    // 流式结束
                                    break;
                                }
                            }
                        } catch (JsonSyntaxException e) {
                            System.err.println("解析 Dify 流数据失败: " + jsonPart);
                            e.printStackTrace();
                        }
                    }
                }
            }

            if (fullAnswer.length() == 0) {
                // 尝试从普通响应中读取（非流式情况下的备用逻辑，可选）
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        fullAnswer.append(line);
                    }
                }
            }

            return fullAnswer.toString();

        } catch (IOException e) {
            throw new RuntimeException("调用 Dify API 失败: " + e.getMessage(), e);
        }
    }
}