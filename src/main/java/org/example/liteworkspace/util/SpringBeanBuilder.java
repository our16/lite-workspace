package org.example.liteworkspace.util;

import com.intellij.psi.*;

import java.util.Set;

public class SpringBeanBuilder implements BeanDefinitionBuilder {

    @Override
    public boolean supports(PsiClass clazz) {
        return !clazz.isInterface();
    }

    @Override
    public String buildBeanXml(PsiClass clazz, Set<String> visited, XmlBeanAssembler assembler) {
        String id = decapitalize(clazz.getName());
        String className = clazz.getQualifiedName();
        StringBuilder beanXml = new StringBuilder();
        beanXml.append("    <bean id=\"").append(id).append("\" class=\"").append(className).append("\">\n");

        for (PsiMethod constructor : clazz.getConstructors()) {
            for (PsiParameter param : constructor.getParameterList().getParameters()) {
                PsiClass dep = resolveClass(param.getType());
                if (dep != null) {
                    beanXml.append("        <constructor-arg ref=\"")
                           .append(decapitalize(dep.getName())).append("\"/>\n");
                    assembler.buildBeanIfNecessary(dep, visited);
                }
            }
        }

        for (PsiField field : clazz.getFields()) {
            PsiClass dep = resolveClass(field.getType());
            if (dep != null) {
                beanXml.append("        <property name=\"").append(field.getName())
                       .append("\" ref=\"").append(decapitalize(dep.getName())).append("\"/>\n");
                assembler.buildBeanIfNecessary(dep, visited);
            }
        }

        beanXml.append("    </bean>");
        return beanXml.toString();
    }

    private PsiClass resolveClass(PsiType type) {
        return type instanceof PsiClassType ? ((PsiClassType) type).resolve() : null;
    }

    private String decapitalize(String name) {
        return name == null || name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}