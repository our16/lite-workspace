package org.example.liteworkspace.bean.scanner.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.util.Query;
import org.example.liteworkspace.bean.core.BeanType;
import org.example.liteworkspace.bean.core.LiteProjectContext;
import org.example.liteworkspace.bean.scanner.BeanScanner;

import java.util.*;

public class JavaConfigBeanScanner implements BeanScanner {

    private final LiteProjectContext context;

    public JavaConfigBeanScanner(LiteProjectContext context) {
        this.context = context;
    }

    @Override
    public Set<PsiClass> collectDependencies(PsiClass clazz) {
        Set<PsiClass> result = new HashSet<>();
        Project project = context.getProject();

        for (PsiField field : clazz.getAllFields()) {
            if (!isAutowired(field)) continue;

            PsiType type = field.getType();
            if (!(type instanceof PsiClassType classType)) continue;

            PsiClass targetType = classType.resolve();
            if (targetType == null) continue;

            // 跳过已经注解声明的 Spring Bean
            if (hasSpringBeanAnnotation(targetType)) continue;

            // 跳过 MyBatis Mapper（可由 MyBatisScanner 处理）
            if (isMyBatisMapper(targetType)) continue;

            // 使用上下文中缓存/模块信息查找 @Configuration + @Bean
            result.addAll(findJavaConfigProviders(targetType));
        }

        return result;
    }

    @Override
    public List<BeanType> supportedType() {
        return Arrays.asList(BeanType.JAVA_CONFIG);
    }

    private boolean isAutowired(PsiModifierListOwner element) {
        PsiModifierList list = element.getModifierList();
        return list != null && (
                list.hasAnnotation("org.springframework.beans.factory.annotation.Autowired") ||
                        list.hasAnnotation("javax.annotation.Resource") ||
                        list.hasAnnotation("org.springframework.beans.factory.annotation.Value")
        );
    }

    private boolean hasSpringBeanAnnotation(PsiClass clazz) {
        return clazz.hasAnnotation("org.springframework.stereotype.Component") ||
                clazz.hasAnnotation("org.springframework.stereotype.Service") ||
                clazz.hasAnnotation("org.springframework.stereotype.Repository") ||
                clazz.hasAnnotation("org.springframework.web.bind.annotation.RestController");
    }

    private boolean isMyBatisMapper(PsiClass clazz) {
        if (clazz.hasAnnotation("org.apache.ibatis.annotations.Mapper")) return true;
        String name = clazz.getName();
        return name != null && name.endsWith("Mapper");
    }

    private Set<PsiClass> findJavaConfigProviders(PsiClass targetType) {
        Set<PsiClass> configClasses = new HashSet<>();
        Project project = context.getProject();

        // 优先使用上下文缓存（可提前构造）
        Collection<PsiClass> candidates = context.getAllConfigurationClasses();
        if (!candidates.isEmpty()) {
            for (PsiClass config : candidates) {
                collectIfBeanReturnTypeMatches(config, targetType, configClasses);
            }
            return configClasses;
        }

        // fallback: 多模块或单模块逐模块扫描
        List<Module> modules = context.getModules();
        if (modules.isEmpty()) {
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            configClasses.addAll(findInScope(scope, project, targetType));
        } else {
            for (Module module : modules) {
                GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesScope(module);
                configClasses.addAll(findInScope(scope, project, targetType));
            }
        }

        return configClasses;
    }

    private Collection<PsiClass> findInScope(GlobalSearchScope scope, Project project, PsiClass targetType) {
        Set<PsiClass> result = new HashSet<>();
        Query<PsiClass> all = AllClassesSearch.search(scope, project);
        for (PsiClass clazz : all) {
            if (clazz.hasAnnotation("org.springframework.context.annotation.Configuration")) {
                collectIfBeanReturnTypeMatches(clazz, targetType, result);
            }
        }
        return result;
    }

    private void collectIfBeanReturnTypeMatches(PsiClass configClass, PsiClass targetType, Set<PsiClass> result) {
        for (PsiMethod method : configClass.getMethods()) {
            if (!method.hasAnnotation("org.springframework.context.annotation.Bean")) continue;
            PsiType returnType = method.getReturnType();
            if (!(returnType instanceof PsiClassType classType)) continue;

            PsiClass returnClass = classType.resolve();
            if (returnClass != null && isCompatible(returnClass, targetType)) {
                result.add(configClass);
            }
        }
    }

    private boolean isCompatible(PsiClass returnClass, PsiClass targetType) {
        return returnClass.equals(targetType) || returnClass.isInheritor(targetType, true);
    }
}
