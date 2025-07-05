package org.example.liteworkspace;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import org.example.liteworkspace.util.SpringXmlGenerator;


public class ParseClassDependenceAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || !(psiFile instanceof PsiJavaFile)) {
            return;
        }

        PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
        if (classes.length == 0) return;

        PsiClass targetClass = classes[0];
        SpringXmlGenerator.generateXmlForClass(project, targetClass);
        // ✅ 添加弹窗提示
        Messages.showInfoMessage(
                project,
                "已为类 " + targetClass.getName() + " 生成 Spring XML 配置文件。",
                "生成完成"
        );
    }

    @Override
    public void update(AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(psiFile instanceof PsiJavaFile);
    }
}
