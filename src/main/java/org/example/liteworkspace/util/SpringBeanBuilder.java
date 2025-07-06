package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Query;

import java.util.*;

import static org.example.liteworkspace.util.BeanScanUtils.getBeanProvidingConfiguration;
import static org.example.liteworkspace.util.BeanScanUtils.isBeanProvidedBySpring;

public class SpringBeanBuilder implements BeanDefinitionBuilder {

    private final Project project;

    public SpringBeanBuilder(Project project) {
        this.project = project;
    }

    @Override
    public boolean supports(PsiClass clazz) {
        return clazz != null && clazz.getQualifiedName() != null;
    }

    @Override
    public void buildBeanXml(PsiClass clazz, Set<String> visited, Map<String, String> beanMap, XmlBeanAssembler assembler) {
        String qName = clazz.getQualifiedName();
        if (!supports(clazz) || visited.contains(qName)) return;
        visited.add(qName);

        boolean providedByBean = isBeanProvidedBySpring(project, clazz);

        // ⚠ 如果由 @Bean 提供，则需要找出其配置类，构建该配置类的 bean
        if (providedByBean) {
            PsiClass configClass = getBeanProvidingConfiguration(project, clazz);
            if (configClass != null) {
                assembler.buildBeanIfNecessary(configClass);
            }
        }

        // 继续处理字段/构造器依赖
        for (PsiMethod constructor : clazz.getConstructors()) {
            for (PsiParameter param : constructor.getParameterList().getParameters()) {
                resolveAndBuildDependency(param.getType(), assembler);
            }
        }

        for (PsiField field : clazz.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.FINAL)) continue;
            resolveAndBuildDependency(field.getType(), assembler);
        }

        // 处理 @Bean 方法（只做依赖，不生成 bean）
        for (PsiMethod method : clazz.getMethods()) {
            if (method.hasAnnotation("org.springframework.context.annotation.Bean")) {
                resolveAndBuildDependency(method.getReturnType(), assembler);
                method.accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                        PsiMethod called = expression.resolveMethod();
                        if (called != null && called.getContainingClass() != null
                                && called.getContainingClass().equals(clazz)) {
                            resolveAndBuildDependency(called.getReturnType(), assembler);
                        }
                    }
                });
            }
        }

        // ✅ 只有不由 @Bean 提供时，才输出 bean
        if (!providedByBean) {
            String id = decapitalize(clazz.getName());
            StringBuilder beanXml = new StringBuilder();
            beanXml.append("    <bean id=\"").append(id).append("\" class=\"").append(qName).append("\"/>\n");
            assembler.putBeanXml(id, beanXml.toString());
        }
    }

    private void resolveAndBuildDependency(PsiType type, XmlBeanAssembler assembler) {
        if (!(type instanceof PsiClassType)) return;

        PsiClass clazz = ((PsiClassType) type).resolve();
        if (clazz == null || clazz.getQualifiedName() == null) return;
        if (clazz.isEnum() || clazz.isAnnotationType() || clazz instanceof PsiTypeParameter) return;
        if (clazz.getQualifiedName().startsWith("java.")) return;

        if (clazz.isInterface()) {
            if (isMyBatisMapper(clazz)) return;
            for (PsiClass impl : findImplementations(clazz)) {
                assembler.buildBeanIfNecessary(impl);
            }
        } else {
            assembler.buildBeanIfNecessary(clazz);
        }
    }

    private boolean isMyBatisMapper(PsiClass clazz) {
        PsiModifierList modifiers = clazz.getModifierList();
        if (modifiers != null) {
            for (PsiAnnotation ann : modifiers.getAnnotations()) {
                if ("org.apache.ibatis.annotations.Mapper".equals(ann.getQualifiedName())) {
                    return true;
                }
            }
        }
        String qName = clazz.getQualifiedName();
        return qName != null && qName.contains(".mapper");
    }

    private List<PsiClass> findImplementations(PsiClass iface) {
        List<PsiClass> result = new ArrayList<>();
        Query<PsiClass> query = ClassInheritorsSearch.search(iface, GlobalSearchScope.projectScope(project), true);
        for (PsiClass cls : query) {
            if (!cls.isInterface()) {
                result.add(cls);
            }
        }
        return result;
    }

    private String decapitalize(String name) {
        return (name == null || name.isEmpty())
                ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
