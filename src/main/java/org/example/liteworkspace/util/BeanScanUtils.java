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



    public static PsiClass findConfigurationClassOfBean(Project project, PsiClass beanClass) {
        String fqcn = beanClass.getQualifiedName();
        if (fqcn == null) return null;

        Map<String, PsiClass> cache = configBeanCache.computeIfAbsent(project, p -> new HashMap<>());
        if (cache.containsKey(fqcn)) {
            return cache.get(fqcn); // ✅ 命中缓存（可能为 null）
        }

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(project);

        for (String methodName : shortNamesCache.getAllMethodNames()) {
            for (PsiMethod method : shortNamesCache.getMethodsByName(methodName, scope)) {
                if (!method.hasAnnotation("org.springframework.context.annotation.Bean")) continue;

                PsiType returnType = method.getReturnType();
                if (!(returnType instanceof PsiClassType)) continue;

                PsiClass returnClass = ((PsiClassType) returnType).resolve();
                if (returnClass == null || !fqcn.equals(returnClass.getQualifiedName())) continue;

                PsiClass configClass = method.getContainingClass();
                if (configClass != null && configClass.hasAnnotation("org.springframework.context.annotation.Configuration")) {
                    cache.put(fqcn, configClass); // ✅ 缓存结果
                    return configClass;
                }
            }
        }

        cache.put(fqcn, null); // ✅ 缓存未命中结果，避免下次重复查
        return null;
    }

}
