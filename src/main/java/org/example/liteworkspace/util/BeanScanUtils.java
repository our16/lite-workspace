package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.example.liteworkspace.index.BeanReturnTypeIndex;

import java.util.*;

public class BeanScanUtils {

    private static final Map<Project, Map<String, PsiClass>> configBeanCache = new WeakHashMap<>();

    public static boolean isBeanProvidedBySpring(Project project, PsiClass targetClass) {
        String fqcn = targetClass.getQualifiedName();
        if (fqcn == null) {
            return false;
        }

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<PsiMethod> candidates = findAllMethodsWithReturnType(project, fqcn, scope);

        for (PsiMethod method : candidates) {
            if (method.hasAnnotation("org.springframework.context.annotation.Bean")) {
                return true;
            }
        }
        return false;
    }

    private static Collection<PsiMethod> findAllMethodsWithReturnType(Project project, String className, GlobalSearchScope scope) {
        List<PsiMethod> result = new ArrayList<>();
        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);

        for (String methodName : cache.getAllMethodNames()) {
            for (PsiMethod method : cache.getMethodsByName(methodName, scope)) {
                PsiType returnType = method.getReturnType();
                if (returnType instanceof PsiClassType) {
                    PsiClass returnClass = ((PsiClassType) returnType).resolve();
                    if (returnClass != null && className.equals(returnClass.getQualifiedName())) {
                        result.add(method);
                    }
                }
            }
        }
        return result;
    }

    public static PsiClass getBeanProvidingConfiguration(Project project, PsiClass beanClass) {
        String beanFqcn = beanClass.getQualifiedName();
        if (beanFqcn == null) return null;

        Collection<PsiClass> configClasses = BeanReturnTypeIndex.getBeanProviders(beanFqcn, project);
        if (configClasses.isEmpty()) return null;

        return configClasses.iterator().next(); // 通常只有一个配置类
    }

}
