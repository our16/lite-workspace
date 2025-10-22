package org.example.liteworkspace.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.example.liteworkspace.cache.LiteCacheStorage;
import org.example.liteworkspace.util.RunOnDemandCompiler;

import java.util.*;

public class RunOnDemandAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("未找到当前项目", "错误");
            return;
        }

        // 1. 获取当前编辑器、文件、光标位置
        Editor editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE);

        if (editor == null || psiFile == null) {
            Messages.showErrorDialog("请在 Java 编辑器中打开一个类文件后重试", "未找到编辑器或文件");
            return;
        }

        // 2. 获取光标位置的 PsiElement，以及其父类：PsiMethod / PsiClass
        int offset = editor.getCaretModel().getOffset();
        PsiElement elementAtCursor = psiFile.findElementAt(offset);

        PsiMethod selectedMethod = PsiTreeUtil.getParentOfType(elementAtCursor, PsiMethod.class);
        PsiClass containingClass = PsiTreeUtil.getParentOfType(elementAtCursor, PsiClass.class);

        if (containingClass == null) {
            Messages.showErrorDialog("光标不在 Java 类中", "无法识别当前类");
            return;
        }

        // 3. 获取主类信息
        String mainClass = containingClass.getQualifiedName();
        if (mainClass == null || mainClass.trim().isEmpty()) {
            Messages.showErrorDialog("无法解析当前类的全限定名", "类名解析失败");
            return;
        }

        // 4. 获取当前类的源码文件路径（只传当前类，也可扩展为多个）
        VirtualFile virtualFile = containingClass.getContainingFile().getVirtualFile();
        if (virtualFile == null) {
            Messages.showErrorDialog("无法获取当前类的文件路径", "路径解析失败");
            return;
        }

        new Task.Backgroundable(project, "LiteWorkspace 编译运行中...") {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    String filePath = virtualFile.getPath();
                    // 5. （可选）加载缓存（如果你想用缓存里的信息做智能推荐，可以在这里使用）
                    LiteCacheStorage cacheStorage = new LiteCacheStorage(project);
                    Set<String> springScanPackages = cacheStorage.loadJavaPaths();
                    List<String> objects = new ArrayList<>(springScanPackages);
                    objects.add(filePath);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        // 6. 调用编译和运行（只传当前类）
                        RunOnDemandCompiler.run(project, mainClass.trim(), objects);
                    });
                } catch (Exception ex) {
                    showError(project, "❌ 编译失败：" + ex.getMessage());
                }
            }
        }.queue();
    }

    private static void showError(Project project, String message) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showErrorDialog(project, message, "LiteWorkspace"));
    }
}