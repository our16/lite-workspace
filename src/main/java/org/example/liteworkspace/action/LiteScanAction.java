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


import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.LiteProjectContext;
import org.example.liteworkspace.bean.engine.LiteBeanScanner;
import org.example.liteworkspace.bean.engine.LiteFileWriter;
import org.example.liteworkspace.bean.engine.SpringXmlBuilder;

import java.util.*;

public class LiteScanAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || !(psiFile instanceof PsiJavaFile)) return;

        PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
        if (classes.length == 0) return;

        PsiClass targetClass = classes[0];
        if (targetClass == null) {
            Messages.showDialog(project, "请右键在类名上运行LiteWorkspace", "LiteWorkspace 工具",
                    new String[]{"确定"}, 0, Messages.getInformationIcon());
            return;
        }

        new Task.Backgroundable(project, "LiteWorkspace 生成中...") {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    LiteProjectContext liteProjectContext = new LiteProjectContext(project);
                    LiteBeanScanner scanner = new LiteBeanScanner(liteProjectContext);
                    Collection<BeanDefinition> beans = scanner.scanAndCollectBeanList(targetClass, project);
                    Map<String, String> beanMap = new SpringXmlBuilder(liteProjectContext)
                            .buildXmlMap(beans);
                    // 改为使用新写入类
                    LiteFileWriter writer = new LiteFileWriter(liteProjectContext);
                    writer.write(project, targetClass, beanMap);
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
}
