package org.example.liteworkspace.bean.builder;

import com.intellij.psi.PsiClass;
import org.example.liteworkspace.bean.core.BeanRegistry;
import org.example.liteworkspace.bean.core.BeanOrigin;
import org.example.liteworkspace.bean.BeanDefinitionBuilder;

public class AnnotationBeanBuilder implements BeanDefinitionBuilder {
    @Override
    public void buildBean(PsiClass clazz, BeanRegistry registry) {
        String fqcn = clazz.getQualifiedName();
        if (fqcn == null) return;

        String id = decapitalize(clazz.getName());
        String xml = "    <bean id=\"" + id + "\" class=\"" + fqcn + "\"/> ";

        registry.register(id, xml, BeanOrigin.ANNOTATION);
    }

    private String decapitalize(String name) {
        return name == null || name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}