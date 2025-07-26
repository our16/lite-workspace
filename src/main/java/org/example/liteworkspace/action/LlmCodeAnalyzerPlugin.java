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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.example.liteworkspace.model.LlmModelInvoker;
import org.example.liteworkspace.ui.LlmAnalysisToolWindow;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class LlmCodeAnalyzerPlugin extends AnAction {
    private static final String TOOL_WINDOW_ID = "LlmAnalysisToolWindow";

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (project == null || file == null || file.isDirectory()) {
            Messages.showWarningDialog("请选择一个 Java 文件进行分析。", "LLM 分析器");
            return;
        }

        // 显示工具窗口
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow != null) {
            toolWindow.show();
        }

        new Task.Backgroundable(project, "LLM 代码分析中...", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    // 读取文件内容
                    String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                    content = "```java\n" + content + "\n```";
                    // 更新工具窗口内容
                    LlmAnalysisToolWindow.updateTextArea(project, content);

                    // 调用 LLM 模型
                    LlmModelInvoker invoker = new LlmModelInvoker();
                    String outputText = invoker.invoke("""
                            You are a senior Java developer. Analyze the following code and point out any potential issues and possible improvements.
                            Return a short and clear summary. End your response with Analysis complete.
                            """ + content);

                    // 更新工具窗口内容
                    ApplicationManager.getApplication().invokeLater(() -> {
                        LlmAnalysisToolWindow.updateTextArea(project, outputText);
                    });

                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog(project, "分析失败: " + ex.getMessage(), "LLM 分析器错误");
                    });
                }
            }
        }.queue();
    }
}