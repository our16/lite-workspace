package org.example.liteworkspace;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import org.example.liteworkspace.parse.DependencyInspectorPsi;

public class ParseClassDependenceAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        // 获取当前项目
        Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("无法获取当前项目", "错误");
            return;
        }

        // 获取当前选中的 Java 文件
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile == null || !(psiFile instanceof PsiJavaFile)) {
            Messages.showErrorDialog("请选择一个 Java 源文件", "错误");
            return;
        }

        // 获取第一个类（可拓展支持多个）
        PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
        if (classes.length == 0) {
            Messages.showErrorDialog("未在文件中找到任何类定义", "错误");
            return;
        }

        PsiClass targetClass = classes[0];

        // 分析依赖并生成 Markdown 报告
        String result = DependencyInspectorPsi.analyze(targetClass);

        // 显示结果弹窗（可改为保存文件或在 ToolWindow 中展示）
        Messages.showInfoMessage(project, result, "依赖分析结果");
    }

    @Override
    public void update(AnActionEvent e) {
        // 控制是否启用菜单项（仅当选择了 Java 文件时）
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(psiFile instanceof PsiJavaFile);
    }
}
