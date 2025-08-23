package org.example.liteworkspace.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;


import com.intellij.psi.util.PsiTreeUtil;
import org.example.liteworkspace.bean.core.LiteWorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiteScanAction extends AnAction {

    private static final Logger log = LoggerFactory.getLogger(LiteScanAction.class);

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || !(psiFile instanceof PsiJavaFile)) return;

        PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
        if (classes.length == 0) return;

        PsiClass targetClass = classes[0];
        if (targetClass == null) {
            Messages.showDialog(project, "请右键在类上运行LiteWorkspace", "LiteWorkspace 工具",
                    new String[]{"确定"}, 0, Messages.getInformationIcon());
            return;
        }

        PsiMethod targetMethod = getTargetMethod(e);
        if (targetMethod == null) {
            log.info("没有找到具体方法");
        }

        new Task.Backgroundable(project, "LiteWorkspace 生成中...") {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    ApplicationManager.getApplication().runReadAction(() -> {
                        LiteWorkspaceService service = new LiteWorkspaceService(project);
                        service.scanAndGenerate(targetClass, targetMethod);
                    });
                } catch (Exception ex) {
                    showError(project, "❌ 生成失败：" + ex.getMessage());
                }
            }
        }.queue();
    }

    private static void showError(Project project, String message) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showErrorDialog(project, message, "LiteWorkspace"));
    }

    private PsiMethod getTargetMethod(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        var editor = e.getData(CommonDataKeys.EDITOR);

        if (project == null || psiFile == null || editor == null) {
            return null;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement elementAt = psiFile.findElementAt(offset);
        if (elementAt == null) return null;

        // 向上找方法
        return PsiTreeUtil.getParentOfType(elementAt, PsiMethod.class);
    }

}
