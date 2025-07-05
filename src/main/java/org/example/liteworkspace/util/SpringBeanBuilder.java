package org.example.liteworkspace.util;

import com.intellij.psi.*;

import java.util.Map;
import java.util.Set;

public class SpringBeanBuilder implements BeanDefinitionBuilder {

    private static final Set<String> INJECT_ANNOTATIONS = Set.of(
            "Autowired", "Resource", "Inject", "Qualifier"
    );

    @Override
    public boolean supports(PsiClass clazz) {
        return clazz != null && !clazz.isInterface() && clazz.getQualifiedName() != null;
    }

    @Override
    public void buildBeanXml(PsiClass clazz, Set<String> visited, Map<String, String> beanMap, XmlBeanAssembler assembler) {
        if (!supports(clazz) || visited.contains(clazz.getQualifiedName())) return;
        visited.add(clazz.getQualifiedName());

        String id = decapitalize(clazz.getName());
        String className = clazz.getQualifiedName();
        StringBuilder beanXml = new StringBuilder();
        beanXml.append("    <bean id=\"").append(id).append("\" class=\"").append(className).append("\"/>\n");

        // 扫描构造器依赖（但不生成 constructor-arg，只标记依赖）
        for (PsiMethod constructor : clazz.getConstructors()) {
            for (PsiParameter param : constructor.getParameterList().getParameters()) {
                PsiClass dep = resolveInjectableClass(param.getType());
                if (dep != null) {
                    assembler.buildBeanIfNecessary(dep); // 加入依赖
                }
            }
        }

        // 扫描字段依赖（不管有没有注解，都不生成 property，但记录依赖）
        for (PsiField field : clazz.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.FINAL)) {
                continue;
            }
            PsiClass dep = resolveInjectableClass(field.getType());
            if (dep != null) {
                assembler.buildBeanIfNecessary(dep);
            }
        }

        assembler.putBeanXml(id, beanXml.toString());
    }

    private PsiClass resolveInjectableClass(PsiType type) {
        if (!(type instanceof PsiClassType)) return null;
        PsiClass clazz = ((PsiClassType) type).resolve();
        if (clazz == null) return null;

        String qName = clazz.getQualifiedName();
        if (qName == null) return null;

        if (clazz.isEnum() || clazz.isAnnotationType()) return null;
        if (qName.startsWith("java.lang.") || qName.startsWith("java.util.")) return null;
        if (clazz instanceof PsiTypeParameter) return null;

        return clazz;
    }

    private String decapitalize(String name) {
        return (name == null || name.isEmpty())
                ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
