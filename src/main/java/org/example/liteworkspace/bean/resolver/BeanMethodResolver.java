package org.example.liteworkspace.bean.resolver;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.util.Query;
import org.example.liteworkspace.bean.BeanDefinitionResolver;

public class BeanMethodResolver implements BeanDefinitionResolver {

    private final Project project;

    public BeanMethodResolver(Project project) {
        this.project = project;
    }

    @Override
    public boolean isBean(PsiClass clazz) {
        return false;
    }

    @Override
    public boolean isXmlDefined(PsiClass clazz) {
        return false;
    }

    @Override
    public boolean isProvidedByBeanMethod(PsiClass clazz) {
        return getProvidingConfiguration(clazz) != null;
    }

    public PsiClass getProvidingConfiguration(PsiClass target) {
        String fqcn = target.getQualifiedName();
        if (fqcn == null) return null;

        Query<PsiClass> query = AllClassesSearch.search(GlobalSearchScope.projectScope(project), project);
        for (PsiClass cls : query) {
            if (!isConfigurationClass(cls)) continue;

            for (PsiMethod method : cls.getMethods()) {
                if (!method.hasAnnotation("org.springframework.context.annotation.Bean")) continue;
                PsiType returnType = method.getReturnType();
                if (returnType instanceof PsiClassType) {
                    PsiClass returnClass = ((PsiClassType) returnType).resolve();
                    if (returnClass != null && fqcn.equals(returnClass.getQualifiedName())) {
                        return cls;
                    }
                }
            }
        }
        return null;
    }

    private boolean isConfigurationClass(PsiClass cls) {
        PsiModifierList list = cls.getModifierList();
        if (list == null) return false;
        for (PsiAnnotation ann : list.getAnnotations()) {
            String qName = ann.getQualifiedName();
            if ("org.springframework.context.annotation.Configuration".equals(qName)) {
                return true;
            }
        }
        return false;
    }
}