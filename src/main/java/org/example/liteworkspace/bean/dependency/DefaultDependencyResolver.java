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

        for (PsiField field : clazz.getFields()) {
            if (!field.hasModifierProperty(PsiModifier.STATIC) && !field.hasModifierProperty(PsiModifier.FINAL)) {
                if (field.hasAnnotation("org.springframework.beans.factory.annotation.Autowired")) {
                    addClass(field.getType(), result);
                }
            }
        }

        for (PsiMethod constructor : clazz.getConstructors()) {
            for (PsiParameter param : constructor.getParameterList().getParameters()) {
                addClass(param.getType(), result);
            }
        }

        for (PsiMethod method : clazz.getMethods()) {
            if (method.hasAnnotation("org.springframework.context.annotation.Bean")) {
                addClass(method.getReturnType(), result);
            }
        }

        return result;
    }

    private void addClass(PsiType type, Set<PsiClass> result) {
        if (type instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            if (resolved != null && resolved.getQualifiedName() != null && !resolved.getQualifiedName().startsWith("java.")) {
                result.add(resolved);
            }
        }
    }
}