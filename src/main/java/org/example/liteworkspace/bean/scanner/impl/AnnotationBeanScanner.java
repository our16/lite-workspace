package org.example.liteworkspace.bean.scanner.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Query;
import org.example.liteworkspace.bean.core.BeanType;
import org.example.liteworkspace.bean.core.LiteProjectContext;
import org.example.liteworkspace.bean.scanner.BeanScanner;

import java.util.*;

public class AnnotationBeanScanner implements BeanScanner {

    private final LiteProjectContext context;

    public AnnotationBeanScanner(LiteProjectContext context) {
        this.context = context;
    }

    @Override
    public Set<PsiClass> collectDependencies(PsiClass clazz) {
        Set<PsiClass> result = new HashSet<>();
        Project project = context.getProject();

        for (PsiField field : clazz.getAllFields()) {
            if (!isAutowired(field)) {
                continue;
            }

            PsiType type = field.getType();
            if (!(type instanceof PsiClassType ct)) {
                continue;
            }

            PsiClass depClass = ct.resolve();
            if (depClass == null) {
                continue;
            }

            // 排除 MyBatis Mapper 和 @Configuration 提供的 bean
            if (isMyBatisMapper(depClass) || context.isProvidedByConfiguration(depClass)) {
                continue;
            }

            if (depClass.isInterface()) {
                result.addAll(findSpringImplementations(depClass));
            } else {
                // 只处理由 @Service/@Component 注解声明的类
                if (!isSpringManaged(depClass)) {
                    continue;
                }
                result.add(depClass);
            }
        }

        return result;
    }

    @Override
    public List<BeanType> supportedType() {
        return Arrays.asList(BeanType.ANNOTATION);
    }

    private boolean isAutowired(PsiModifierListOwner element) {
        PsiModifierList list = element.getModifierList();
        return list != null && (
                list.hasAnnotation("org.springframework.beans.factory.annotation.Autowired") ||
                        list.hasAnnotation("javax.annotation.Resource") ||
                        list.hasAnnotation("org.springframework.beans.factory.annotation.Value")
        );
    }

    private boolean isSpringManaged(PsiClass clazz) {
        PsiModifierList list = clazz.getModifierList();
        if (list == null) {
            return false;
        }

        return list.hasAnnotation("org.springframework.stereotype.Component") ||
                list.hasAnnotation("org.springframework.stereotype.Service") ||
                list.hasAnnotation("org.springframework.stereotype.Repository") ||
                list.hasAnnotation("org.springframework.web.bind.annotation.RestController");
    }

    private boolean isMyBatisMapper(PsiClass clazz) {
        return clazz.hasAnnotation("org.apache.ibatis.annotations.Mapper") ||
                (clazz.getName() != null && clazz.getName().endsWith("Mapper"));
    }

    private Collection<PsiClass> findSpringImplementations(PsiClass iface) {
        Set<PsiClass> result = new HashSet<>();
        Project project = context.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Query<PsiClass> query = ClassInheritorsSearch.search(iface, scope, true);

        for (PsiClass impl : query) {
            if (isSpringManaged(impl) && !isMyBatisMapper(impl) && !context.isProvidedByConfiguration(impl)) {
                result.add(impl);
            }
        }

        return result;
    }
}
