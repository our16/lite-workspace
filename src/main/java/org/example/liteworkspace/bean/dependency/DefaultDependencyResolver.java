package org.example.liteworkspace.bean.dependency;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.example.liteworkspace.bean.BeanDependencyResolver;

import java.util.*;

public class DefaultDependencyResolver implements BeanDependencyResolver {
    private final Project project;

    public DefaultDependencyResolver(Project project) {
        this.project = project;
    }

    @Override
    public Set<PsiClass> resolveDependencies(PsiClass clazz) {
        Set<PsiClass> result = new HashSet<>();

        // 1️⃣ 解析字段注入
        for (PsiField field : clazz.getFields()) {
            if (!field.hasModifierProperty(PsiModifier.STATIC) && !field.hasModifierProperty(PsiModifier.FINAL)) {
                if (field.hasAnnotation("org.springframework.beans.factory.annotation.Autowired")) {
                    addClass(field.getType(), result);
                } else  if (field.hasAnnotation("javax.annotation.Resource")) {
                    addClass(field.getType(), result);
                }
            }
        }

        // 2️⃣ 构造器注入（显式 + 隐式）
        PsiMethod[] constructors = clazz.getConstructors();
        for (PsiMethod constructor : constructors) {
            // 如果有 @Autowired 或者该类只有一个构造器（隐式注入）
            boolean hasAutowired = constructor.hasAnnotation("org.springframework.beans.factory.annotation.Autowired");
            boolean isOnlyConstructor = constructors.length == 1;
            if (hasAutowired || isOnlyConstructor) {
                for (PsiParameter param : constructor.getParameterList().getParameters()) {
                    addClass(param.getType(), result);
                }
            }
        }
        // lombok 注解，动态有一个构造器的场景
        if (hasAllArgsConstructorAnnotation(clazz)) {
            for (PsiField field : clazz.getFields()) {
                if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                    addClass(field.getType(), result);
                }
            }
        }


        // 3️⃣ 工厂方法返回类型也作为依赖
        for (PsiMethod method : clazz.getMethods()) {
            if (method.hasAnnotation("org.springframework.context.annotation.Bean")) {
                addClass(method.getReturnType(), result);
            }
        }

        return result;
    }

    private boolean hasAllArgsConstructorAnnotation(PsiClass clazz) {
        return Arrays.stream(clazz.getAnnotations())
                .map(PsiAnnotation::getQualifiedName)
                .anyMatch(q -> q != null && (
                        q.equals("lombok.AllArgsConstructor") ||
                                q.endsWith(".AllArgsConstructor")));
    }


    private void addClass(PsiType type, Set<PsiClass> result) {
        if (type instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            if (resolved != null && resolved.getQualifiedName() != null
                    && !resolved.getQualifiedName().startsWith("java.")) {
                result.add(resolved);
            }
        }
    }
}
