package org.example.liteworkspace.model;


import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import org.example.liteworkspace.config.LiteWorkspaceSettings;
import org.example.liteworkspace.model.entity.DifyResponse;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LlmModelInvoker {
    private static final Logger LOG = Logger.getInstance(LlmModelInvoker.class);

    public static String invoke(String prompt) throws IOException, InterruptedException {
        boolean userLocal = Boolean.parseBoolean(System.getenv("USE_LOCAL")); // 通过环境变量切换
        if (!userLocal) {
            return invokeViaDify(prompt);
        } else {
            return invokeViaLocalModel(prompt);
        }
    }

    private static String invokeViaLocalModel(String prompt) throws IOException, InterruptedException {
        File promptFile = File.createTempFile("llm_prompt_", ".txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(promptFile, StandardCharsets.UTF_8))) {
            writer.write(prompt);
        }

        File modelFile = new File("D:\\ollama_models\\deepseek-coder\\deepseek-coder-1.3b-instruct.Q4_K_M.gguf");
        if (!modelFile.exists()) {
            throw new FileNotFoundException("本地模型文件不存在");
        }

        ProcessBuilder builder = new ProcessBuilder(
                "cmd.exe", "/c",
                "D:\\ollama_models\\llama\\llama-run.exe",
                "file://" + modelFile.getAbsolutePath(),
                "@" + promptFile.getAbsolutePath()
        );

        builder.redirectErrorStream(true);
        Process process = builder.start();

        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
                LOG.info("【本地LLM输出】" + line);
            }
        }

        process.waitFor();
        return result.toString();
    }

    public static String invokeViaDify(String prompt) throws IOException {
        LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
        String apiUrl = settings.getApiUrl();
        String apiKey = settings.getApiKey();
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        // 构造 JSON 请求体
        Map<String, Object> body = new HashMap<>();
        body.put("inputs", new HashMap<>()); // 空对象
        body.put("query", prompt);
        body.put("response_mode", "streaming");
        body.put("conversation_id", "");
        body.put("user", "abc-123");
        // 序列化为 JSON 字符串
        Gson requestJson = new Gson();
        String jsonBody = requestJson.toJson(body);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        InputStream responseStream = (status >= 200 && status < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        StringBuilder fullAnswer = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            Gson gson = new Gson();

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String jsonPart = line.substring("data: ".length()).trim();
                    if (jsonPart.contains("event")) {
                        DifyResponse jsonObj = gson.fromJson(jsonPart, DifyResponse.class);
                        String event = jsonObj.event;
                        if ("agent_message".equals(event) && jsonObj.answer != null && !jsonObj.answer.isEmpty()) {
                            String answer = jsonObj.answer;
                            fullAnswer.append(answer);  // 拼接每一段返回内容
                        } else if ("message_end".equals(event)) {
                            // 流结束
                            break;
                        }
                    }
                }
            }
        }
        System.out.println("LLM 回复内容：\n" + fullAnswer.toString());
        return fullAnswer.toString();
    }
}

