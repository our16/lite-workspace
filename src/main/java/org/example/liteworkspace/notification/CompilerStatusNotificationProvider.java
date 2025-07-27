package org.example.liteworkspace.notification;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Function;

public class CompilerStatusNotificationProvider implements EditorNotificationProvider {

    private static final Key<String> KEY = new Key<>("RunOnDemandCompilerStatus", 1);

    @Override
    public @NotNull Function<? super FileEditor, ? extends JComponent> collectNotificationData(
            @NotNull Project project, @NotNull VirtualFile file) {
        return fileEditor -> {
            if (CompilerStatusState.getInstance(project).shouldShow(file)) {
                EditorNotificationPanel panel = new EditorNotificationPanel();
                panel.setText("⚠ 构建配置异常，请检查");
                panel.setBackground(EditorNotificationPanel.getToolbarBackground());
                return panel;
            }
            return null;
        };
    }
}
