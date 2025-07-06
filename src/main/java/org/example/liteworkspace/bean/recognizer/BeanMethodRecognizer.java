package org.example.liteworkspace.bean.recognizer;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import org.example.liteworkspace.bean.BeanRecognizer;
import org.example.liteworkspace.bean.core.BeanOrigin;

import java.util.Arrays;

public class BeanMethodRecognizer implements BeanRecognizer {
    private final Project project;

    public BeanMethodRecognizer(Project project) {
        this.project = project;
    }

    @Override
    public boolean isBean(PsiClass clazz) {
        return getOrigin(clazz) != null;
    }

    @Override
    public BeanOrigin getOrigin(PsiClass clazz) {
        return getProviderClass(clazz) != null ? BeanOrigin.BEAN_METHOD : null;
    }

    @Override
    public PsiClass getProviderClass(PsiClass clazz) {
        String fqcn = clazz.getQualifiedName();
        if (fqcn == null) return null;

        for (PsiClass conf : AllClassesSearch.search(GlobalSearchScope.projectScope(project), project)) {
            if (!hasConfigurationAnnotation(conf)) continue;

            for (PsiMethod method : conf.getMethods()) {
                if (!method.hasAnnotation("org.springframework.context.annotation.Bean")) continue;
                PsiType type = method.getReturnType();
                if (type instanceof PsiClassType clsType &&
                        fqcn.equals(clsType.resolve().getQualifiedName())) {
                    return conf;
                }
            }
        }
        return null;
    }

    private boolean hasConfigurationAnnotation(PsiClass cls) {
        PsiModifierList list = cls.getModifierList();
        if (list == null) return false;
        return Arrays.stream(list.getAnnotations())
                .anyMatch(ann -> "org.springframework.context.annotation.Configuration".equals(ann.getQualifiedName()));
    }
}

