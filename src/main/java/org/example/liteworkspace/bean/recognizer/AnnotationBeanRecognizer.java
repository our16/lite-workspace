package org.example.liteworkspace.bean.recognizer;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import org.example.liteworkspace.bean.BeanRecognizer;
import org.example.liteworkspace.bean.core.BeanOrigin;

import java.util.Collection;

public class AnnotationBeanRecognizer implements BeanRecognizer {

    @Override
    public boolean isBean(PsiClass clazz) {
        return getOrigin(clazz) != null;
    }

    @Override
    public BeanOrigin getOrigin(PsiClass clazz) {
        if (clazz == null) return null;

        // 1️⃣ 先判断自身是否有注解
        PsiModifierList list = clazz.getModifierList();
        if (list != null) {
            for (PsiAnnotation ann : list.getAnnotations()) {
                String qName = ann.getQualifiedName();
                if (qName != null && (
                        qName.startsWith("org.springframework.stereotype.") ||
                                qName.equals("org.springframework.web.bind.annotation.RestController") ||
                                qName.equals("org.springframework.context.annotation.Configuration"))) {
                    return BeanOrigin.ANNOTATION;
                }
            }
        }

        // 2️⃣ 如果是接口，查找是否有被注解的实现类
        if (clazz.isInterface()) {
            for (PsiClass impl : findImplementationClasses(clazz)) {
                if (isAnnotatedSpringBean(impl)) {
                    return BeanOrigin.ANNOTATION;
                }
            }
        }

        return null;
    }

    private boolean isAnnotatedSpringBean(PsiClass clazz) {
        PsiModifierList list = clazz.getModifierList();
        if (list == null) return false;

        for (PsiAnnotation ann : list.getAnnotations()) {
            String qName = ann.getQualifiedName();
            if (qName != null && (
                    qName.startsWith("org.springframework.stereotype.") ||
                            qName.equals("org.springframework.web.bind.annotation.RestController") ||
                            qName.equals("org.springframework.context.annotation.Configuration"))) {
                return true;
            }
        }

        return false;
    }

    private Collection<PsiClass> findImplementationClasses(PsiClass iface) {
        Project project = iface.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        return ClassInheritorsSearch.search(iface, scope, true).findAll();
    }
}
