package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Query;

import java.util.*;

import static org.example.liteworkspace.util.BeanScanUtils.*;

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
            boolean hasSpringAnnotation = hasSpringComponentAnnotation(clazz);

            if (hasSpringAnnotation) {
                // 组件注解：生成 <bean>
                generateBeanXml(clazz, assembler);
            } else if (isXmlDefinedBean(project, clazz)) {
                // XML 定义：不生成 bean，但标记其 XML 配置为依赖
                PsiFile xml = findXmlFileDefiningBean(project, clazz);
                if (xml instanceof XmlFile) {
                    copyBeanTagFromXml((XmlFile) xml, clazz, assembler);
                }
            }
        }

    }

    private void generateBeanXml(PsiClass clazz, XmlBeanAssembler assembler) {
        String qName = clazz.getQualifiedName();
        if (qName == null) return;

        String id = decapitalize(clazz.getName());
        StringBuilder beanXml = new StringBuilder();

        beanXml.append("    <bean id=\"").append(id).append("\" class=\"").append(qName).append("\"/>\n");
        assembler.putBeanXml(id, beanXml.toString());
    }



    private void copyBeanTagFromXml(XmlFile xmlFile, PsiClass clazz, XmlBeanAssembler assembler) {
        String fqcn = clazz.getQualifiedName();
        if (fqcn == null) return;

        XmlTag root = xmlFile.getRootTag();
        if (root == null) return;

        for (XmlTag tag : root.findSubTags("bean")) {
            String cls = tag.getAttributeValue("class");
            if (fqcn.equals(cls)) {
                String id = tag.getAttributeValue("id");
                if (id == null || id.isEmpty()) {
                    id = decapitalize(clazz.getName());
                }
                assembler.putBeanXml(id, "    " + tag.getText() + "\n");
                return;
            }
        }
    }


    private boolean hasSpringComponentAnnotation(PsiClass clazz) {
        PsiModifierList modifierList = clazz.getModifierList();
        if (modifierList == null) return false;

        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            String qName = annotation.getQualifiedName();
            if (qName == null) continue;

            if (qName.startsWith("org.springframework.stereotype.") // e.g. @Component, @Service
                    || qName.equals("org.springframework.context.annotation.Configuration")
                    || qName.equals("org.springframework.web.bind.annotation.RestController")) {
                return true;
            }
        }
        return false;
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
