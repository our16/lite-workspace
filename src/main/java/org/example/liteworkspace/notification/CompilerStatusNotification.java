package org.example.liteworkspace.notification;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public class CompilerStatusNotification {

    private static final Key<String> NOTIFICATION_KEY = Key.create("RunOnDemandCompilerStatus");

    public static void showNotification(@NotNull Project project, @NotNull String message) {
        // 创建并注册通知提供者
        EditorNotifications.getInstance(project).updateNotifications(
                new EditorNotificationProvider() {
                    @NotNull
                    @Override
                    public Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(
                            @NotNull Project project,
                            @NotNull VirtualFile file
                    ) {
                        return fileEditor -> {
                            // 检查是否需要显示通知（可根据file或project状态过滤）
                            if (shouldShowNotification(project, file)) {
                                EditorNotificationPanel panel = new EditorNotificationPanel();
                                panel.setText(message);
                                panel.setBackground(EditorNotificationPanel.getToolbarBackground());
                                return panel;
                            }
                            return null;
                        };
                    }
                }
        );

        // 刷新通知显示
        EditorNotifications.getInstance(project).updateAllNotifications();
    }

    private static boolean shouldShowNotification(@NotNull Project project, @NotNull VirtualFile file) {
        // 这里添加你的显示逻辑判断
        return true; // 示例：默认总是显示
    }
}