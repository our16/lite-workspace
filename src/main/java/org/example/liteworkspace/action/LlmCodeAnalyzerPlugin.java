package org.example.liteworkspace.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.*;
import java.nio.charset.StandardCharsets;

import com.intellij.openapi.diagnostic.Logger;
import org.example.liteworkspace.model.LlmModelInvoker;

public class LlmCodeAnalyzerPlugin extends AnAction {

    private static final Logger LOG = Logger.getInstance(LlmCodeAnalyzerPlugin.class);


    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (project == null || file == null || file.isDirectory()) {
            Messages.showWarningDialog("请选择一个 Java 文件进行分析。", "LLM 分析器");
            return;
        }

        new Task.Backgroundable(project, "LLM 代码分析中...", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                File promptFile = null;
                try {
                    // 读取文件内容
                    String content;
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                        content = reader.lines().reduce("", (a, b) -> a + "\n" + b);
                    }

                    content = "```java\n" + content + "\n```";
                    String prompt = """
                            You are a senior Java developer. Analyze the following code and point out any potential issues and possible improvements.
                            Return a short and clear summary. End your response with Analysis complete.
                            """ + content;

                    // 替代 println
                    LOG.info("【LLM 请求 Prompt】\n" + prompt);
                    LlmModelInvoker invoker = new LlmModelInvoker();
                    String outputText = invoker.invoke(prompt);
                    ApplicationManager.getApplication().invokeLater(() ->
                            Messages.showInfoMessage(project, outputText, "LLM 分析结果"));

                } catch (Exception ex) {
                    showError("分析失败: " + ex.getMessage(), project);
                } finally {
                    if (promptFile != null && promptFile.exists()) {
                        promptFile.deleteOnExit();
                    }
                }
            }
        }.queue();
    }

    private void showError(String message, Project project) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showErrorDialog(project, message, "LLM 分析器错误"));
    }
}
