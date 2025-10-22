package org.example.liteworkspace.action;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class CustomCompileRunAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiElement element = e.getData(LangDataKeys.PSI_ELEMENT);

        if (project == null || element == null) return;

        // 1. 获取当前模块
        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module == null) {
            Messages.showErrorDialog("无法确定当前文件所属的模块", "错误");
            return;
        }

        // 2. 增量编译
        CompilerManager compilerManager = CompilerManager.getInstance(project);
        compilerManager.make(module, (aborted, errors, warnings, ctx) -> {
            if (errors == 0) {
                // 2. 调用默认 Run
                Executor executor = DefaultRunExecutor.getRunExecutorInstance();
                RunnerAndConfigurationSettings configuration = createJUnitRunConfig(project, element);
                ProgramRunnerUtil.executeConfiguration(configuration, executor);
            }
        });
    }

    private RunnerAndConfigurationSettings createJUnitRunConfig(Project project, PsiElement element) {
        ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(JUnitConfigurationType.class);
        ConfigurationFactory factory = type.getConfigurationFactories()[0];
        RunManager runManager = RunManager.getInstance(project);
        RunnerAndConfigurationSettings settings = runManager.createConfiguration("CustomRun", factory);
        JUnitConfiguration config = (JUnitConfiguration) settings.getConfiguration();
        if (element instanceof PsiClass) {
            config.beClassConfiguration((PsiClass) element);
        } else if (element instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) element;
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                config.beMethodConfiguration(MethodLocation.elementInClass(method, containingClass));
            }
        }
        return settings;
    }

}
