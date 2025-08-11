package org.example.liteworkspace.extensions;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public class CustomCompileJUnitProducer extends RunConfigurationProducer<JUnitConfiguration> {

    protected CustomCompileJUnitProducer() {
        super(JUnitConfigurationType.getInstance());
    }

    @Override
    protected boolean setupConfigurationFromContext(@NotNull JUnitConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
        Location<?> location = context.getLocation();
        if (location == null) {
            return false;
        }

        PsiElement element = location.getPsiElement();

        // 判断是不是JUnit测试类/方法
        if (element instanceof PsiClass) {
            configuration.setName("Run with Custom Compile: " + ((PsiClass) element).getName());
            configuration.beClassConfiguration((PsiClass) element);

        } else if (element instanceof PsiMethod method) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                configuration.setName("Run with Custom Compile: " + method.getName());
                configuration.beMethodConfiguration(MethodLocation.elementInClass(method, containingClass));
            } else {
                return false;
            }
        } else {
            return false;
        }

        // 这里加你的“增量编译逻辑”
        runCustomCompile();

        return true;
    }


    @Override
    public boolean isConfigurationFromContext(@NotNull JUnitConfiguration configuration,
                                              @NotNull ConfigurationContext context) {
        // 用来判断配置是否可复用，简单返回 false 保证每次都新建
        return false;
    }

    private void runCustomCompile() {
        System.out.println("=== Running custom incremental compile ===");
        // 这里调用你自定义的编译逻辑，比如直接调用 javac 或 IDEA 编译 API
    }
}

