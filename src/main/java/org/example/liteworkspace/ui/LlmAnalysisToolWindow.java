package org.example.liteworkspace.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class LlmAnalysisToolWindow implements ToolWindowFactory {
    // 1. 定义工具窗口ID和数据键
    private static final String TOOL_WINDOW_ID = "LlmAnalysisToolWindow";
    private static final Key<JTextArea> TEXT_AREA_KEY = new Key<>("LlmAnalysisToolWindow.TextArea");

    private static JTextArea textArea;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 2. 创建工具窗口内容
        JPanel panel = new JPanel(new BorderLayout());
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        // 3. 添加关闭按钮
        JButton closeButton = new JButton("关闭");
        closeButton.addActionListener(e -> {
            ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID).hide();
        });
        panel.add(closeButton, BorderLayout.SOUTH);

        // 4. 注册工具窗口内容
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);

        // 5. 使用 putUserData 存储文本区域引用（需检查 UserDataHolder 接口）
        if (toolWindow instanceof UserDataHolder) {
            ((UserDataHolder) toolWindow).putUserData(TEXT_AREA_KEY, textArea);
        }
        LlmAnalysisToolWindow.textArea = textArea;
    }

    public static void updateTextArea(Project project, String message) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(LlmAnalysisToolWindow.TOOL_WINDOW_ID);

        if (toolWindow instanceof UserDataHolder) {
            JTextArea textArea = ((UserDataHolder) toolWindow)
                    .getUserData(LlmAnalysisToolWindow.TEXT_AREA_KEY);

            if (textArea != null) {
                textArea.setText(message);
            }
        } else if (LlmAnalysisToolWindow.textArea != null) {
            LlmAnalysisToolWindow.textArea.setText(message);
        }
    }
}