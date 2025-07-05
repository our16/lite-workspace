package org.example.liteworkspace.listener;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfiguration.Data;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.example.liteworkspace.util.MinimalCompilerBuilder;
import org.example.liteworkspace.util.TestXmlLocator;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class MinimalTestExecutionListener implements ExecutionListener {

    @Override
    public void processStarted(@NotNull String executorId,
                               @NotNull ExecutionEnvironment env,
                               @NotNull ProcessHandler handler) {

        Project project = env.getProject();
        RunProfile runProfile = env.getRunProfile();

        // 仅支持 JUnit 测试配置
        if (runProfile instanceof JUnitConfiguration) {
            JUnitConfiguration config = (JUnitConfiguration) runProfile;
            Data data = config.getPersistentData();
            String fqClassName = data.getMainClassName(); // 获取全限定类名

            PsiClass testClass = JavaPsiFacade.getInstance(project)
                    .findClass(fqClassName, GlobalSearchScope.projectScope(project));

            if (testClass != null) {
                Set<PsiClass> classes = new HashSet<>();
                Set<VirtualFile> resources = new HashSet<>();
                classes.add(testClass);

                VirtualFile testXml = TestXmlLocator.findTestXmlForClass(project, testClass);
                if (testXml != null) {
                    MinimalCompilerBuilder.collectDependenciesFromXml(project, testXml, classes, resources);
                }

                MinimalCompilerBuilder.compile(project, classes);
            }
        }
    }
}
