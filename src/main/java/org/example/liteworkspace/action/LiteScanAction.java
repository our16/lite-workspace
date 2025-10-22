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
import org.example.liteworkspace.dto.ClassSignatureDTO;
import org.example.liteworkspace.dto.MethodSignatureDTO;
import org.example.liteworkspace.dto.PsiToDtoConverter;
import org.example.liteworkspace.util.CostUtil;
import org.example.liteworkspace.util.LogUtil;
import org.example.liteworkspace.util.ReadActionUtil;

public class LiteScanAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        CostUtil.start("LiteScanAction_total");
        LogUtil.info("LiteScanAction.actionPerformed 开始执行");
        
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || !(psiFile instanceof PsiJavaFile)) {
            LogUtil.warn("项目为空或文件不是Java文件，项目={}, 文件类型={}",
                project, psiFile != null ? psiFile.getClass().getSimpleName() : "null");
            return;
        }

        // 使用 ReadActionUtil.runSync 包装 PSI 操作
        final ClassSignatureDTO[] targetClassDto = new ClassSignatureDTO[1];
        final MethodSignatureDTO[] targetMethodDto = new MethodSignatureDTO[1];
        
        try {
            ReadActionUtil.runSync(project, () -> {
                CostUtil.start("getPsiClasses");
                PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
                CostUtil.end("getPsiClasses", "获取Java类");
                
                if (classes.length == 0) {
                    LogUtil.warn("Java文件中没有找到类");
                    return;
                }

                PsiClass targetPsiClass = classes[0];
                if (targetPsiClass == null) {
                    LogUtil.warn("目标类为空");
                    return;
                }
                
                LogUtil.info("找到目标类: {}", targetPsiClass.getQualifiedName());
                
                // 将目标类转换为DTO，不长期保存PSI对象
                targetClassDto[0] = PsiToDtoConverter.convertToClassSignature(targetPsiClass);
                LogUtil.debug("目标类转换为DTO: {}", targetClassDto[0]);
                
                // 获取目标方法
                CostUtil.start("getTargetMethod");
                PsiMethod targetPsiMethod = getTargetMethod(e);
                CostUtil.end("getTargetMethod", "获取目标方法");
                
                if (targetPsiMethod == null) {
                    LogUtil.info("没有找到具体方法");
                } else {
                    LogUtil.info("找到目标方法: {}", targetPsiMethod.getName());
                    // 将目标方法转换为DTO，不长期保存PSI对象
                    targetMethodDto[0] = PsiToDtoConverter.convertToMethodSignature(targetPsiMethod);
                    LogUtil.debug("目标方法转换为DTO: {}", targetMethodDto[0]);
                }
            });
        } catch (Exception ex) {
            LogUtil.error("PSI操作执行失败", ex);
            return;
        }

        if (targetClassDto[0] == null) {
            Messages.showDialog(project, "请右键在类上运行LiteWorkspace", "LiteWorkspace 工具",
                    new String[]{"确定"}, 0, Messages.getInformationIcon());
            return;
        }

        new Task.Backgroundable(project, "LiteWorkspace 生成中...", true) {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    CostUtil.start("LiteWorkspaceService_scanAndGenerate");
                    LogUtil.info("开始后台任务: LiteWorkspace 生成");
                    
                    indicator.setIndeterminate(false);
                    indicator.setText("正在扫描项目依赖...");
                    indicator.setFraction(0.1);
                    
                    // 在后台线程中创建服务，使用DTO而不是PSI对象
                    LiteWorkspaceService service = new LiteWorkspaceService(project);
                    service.scanAndGenerateWithDto(targetClassDto[0], targetMethodDto[0], indicator);
                    
                    CostUtil.end("LiteWorkspaceService_scanAndGenerate", "LiteWorkspace服务扫描和生成");
                } catch (Exception ex) {
                    LogUtil.error("生成过程发生异常", ex);
                    showError(project, "❌ 生成失败：" + ex.getMessage());
                } finally {
                    CostUtil.end("LiteScanAction_total", "LiteScanAction总执行");
                }
            }
        }.queue();
    }

    private static void showError(Project project, String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            LogUtil.error("显示错误对话框: {}", null, message);
            Messages.showErrorDialog(project, message, "LiteWorkspace");
        });
    }

    private PsiMethod getTargetMethod(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        var editor = e.getData(CommonDataKeys.EDITOR);

        if (project == null || psiFile == null || editor == null) {
            LogUtil.debug("获取目标方法参数不完整: project={}, psiFile={}, editor={}",
                project, psiFile != null, editor != null);
            return null;
        }

        int offset = editor.getCaretModel().getOffset();
        
        // 使用 ReadActionUtil.runSync 包装 PSI 操作
        final PsiElement[] elementAt = new PsiElement[1];
        final PsiMethod[] method = new PsiMethod[1];
        
        try {
            ReadActionUtil.runSync(project, () -> {
                CostUtil.start("findElementAt");
                elementAt[0] = psiFile.findElementAt(offset);
                CostUtil.end("findElementAt", "查找元素");
                
                if (elementAt[0] == null) {
                    LogUtil.debug("在偏移量 {} 处未找到PSI元素", offset);
                    return;
                }
                
                // 向上找方法
                CostUtil.start("getParentOfType");
                method[0] = PsiTreeUtil.getParentOfType(elementAt[0], PsiMethod.class);
                CostUtil.end("getParentOfType", "获取父类型方法");
            });
        } catch (Exception ex) {
            LogUtil.error("获取目标方法时发生异常", ex);
            return null;
        }
        
        return method[0];
    }

}
