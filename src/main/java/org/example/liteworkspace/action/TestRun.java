package org.example.liteworkspace.action;

import com.intellij.openapi.util.io.FileUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class TestRun {
    public static void main(String[] args) throws Exception {
        String prompt = "You are a Java expert. Analyze: public class Test {}";

        // 写入临时文件
        Path promptFile = Files.createTempFile("llm_prompt", ".txt");
        Files.write(promptFile, prompt.getBytes(StandardCharsets.UTF_8));
        System.out.println("Prompt file: " + promptFile);

        // 构建命令
        List<String> command = new ArrayList<>();
        command.add("cmd.exe");
        command.add("/c");
        command.add("D:\\ollama_models\\llama\\llama-run.exe");
        command.add("file://D:/ollama_models/deepseek-coder/deepseek-coder-1.3b-instruct.Q4_K_M.gguf");
        command.add("@" + promptFile.toAbsolutePath().toString());

        ProcessBuilder builder = new ProcessBuilder(command);

        builder.redirectErrorStream(true);
        System.out.println("执行目录: " + new File(".").getAbsolutePath());
        builder.directory(new File("D:\\ollama_models\\llama"));
        System.out.println("执行命令: " + builder.command());

        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(">> " + line);
            }
        } finally {
            if (promptFile != null) {
                FileUtil.delete(promptFile);
            }
        }
    }
}


