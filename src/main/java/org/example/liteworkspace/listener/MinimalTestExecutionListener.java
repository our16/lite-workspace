package org.example.liteworkspace.listener;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.example.liteworkspace.util.MinimalCompilerBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class MinimalTestExecutionListener implements ExecutionListener {

    @Override
    public void processStarted(@NotNull String executorId,
                               @NotNull ExecutionEnvironment env,
                               @NotNull ProcessHandler handler) {

        Project project = env.getProject();
        RunProfile runProfile = env.getRunProfile();

        String className = runProfile.getName();
        PsiClass testClass = JavaPsiFacade.getInstance(project)
                .findClass(className, GlobalSearchScope.projectScope(project));

        if (testClass != null) {
            MinimalCompilerBuilder.compile(project, Collections.singleton(testClass));
        }
    }
}
