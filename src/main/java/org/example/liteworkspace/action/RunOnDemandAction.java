package org.example.liteworkspace.action;


import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import org.example.liteworkspace.util.RunOnDemandCompiler;

public class RunOnDemandAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        // 弹出用户输入对话框
        CompileAndRunDialog dialog = new CompileAndRunDialog();
        if (dialog.showAndGet()) {
            String mainClass = dialog.getMainClass();
            String[] filePaths = dialog.getSourceFilePaths();

            if (mainClass == null || mainClass.trim().isEmpty() || filePaths == null || filePaths.length == 0) {
                Messages.showErrorDialog("请填写主类和至少一个源码文件路径", "输入错误");
                return;
            }

            // 调用编译 + 运行逻辑
            RunOnDemandCompiler.run(mainClass.trim(), filePaths);
        }
    }
}