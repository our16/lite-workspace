package org.example.liteworkspace.bean.builder;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import org.example.liteworkspace.bean.BeanDefinitionBuilder;
import org.example.liteworkspace.bean.core.BeanOrigin;
import org.example.liteworkspace.bean.core.BeanRegistry;
import org.example.liteworkspace.bean.core.BeanScanCoordinator;

import java.util.Collection;

public class AnnotationBeanBuilder implements BeanDefinitionBuilder {

    private final BeanScanCoordinator coordinator;

    public AnnotationBeanBuilder(BeanScanCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void buildBean(PsiClass clazz, BeanRegistry registry) {
        if (clazz == null || clazz.getQualifiedName() == null) {
            return;
        }

        if (clazz.isInterface()) {
            // 接口：查找所有实现类并注册
            for (PsiClass impl : findImplementationClasses(clazz)) {
                registerBean(impl, registry);
            }
        } else {
            // 普通类：直接注册
            registerBean(clazz, registry);
        }
    }

    private Collection<PsiClass> findImplementationClasses(PsiClass iface) {
        Project project = iface.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        return ClassInheritorsSearch.search(iface, scope, true).findAll();
    }

    private void registerBean(PsiClass clazz, BeanRegistry registry) {
        if (clazz == null || clazz.isInterface() || clazz.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return; // 忽略接口和抽象类
        }

        String fqcn = clazz.getQualifiedName();
        if (fqcn == null || registry.contains(fqcn)) return;

        String id = decapitalize(clazz.getName());
        String xml = "    <bean id=\"" + id + "\" class=\"" + fqcn + "\"/>";
        registry.register(id, xml, BeanOrigin.ANNOTATION);

        // ✅ 扫描并构建依赖
        coordinator.scanAndBuild(clazz, registry);
    }

    private String decapitalize(String name) {
        return name == null || name.isEmpty()
                ? name
                : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
