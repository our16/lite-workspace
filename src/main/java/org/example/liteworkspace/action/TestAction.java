package org.example.liteworkspace.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;

public class TestAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiElement element = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.PSI_ELEMENT);
        PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (clazz == null) {
            Messages.showInfoMessage("请右键在类名上运行", "LiteWorkspace");
            return;
        }

        Messages.showInfoMessage("找到类：" + clazz.getQualifiedName(), "LiteWorkspace");
    }
}
