package org.example.liteworkspace.bean.builder;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiAnnotation;
import org.example.liteworkspace.bean.BeanDefinitionBuilder;
import org.example.liteworkspace.bean.core.BeanOrigin;
import org.example.liteworkspace.bean.core.BeanRegistry;

public class MyBatisMapperBuilder implements BeanDefinitionBuilder {

    @Override
    public void buildBean(PsiClass clazz, BeanRegistry registry) {
        if (!isMyBatisMapper(clazz)) return;

        String fqcn = clazz.getQualifiedName();
        if (fqcn == null) return;

        String id = decapitalize(clazz.getName());
        String xml = "    <bean id=\"" + id + "\" class=\"" + fqcn + "\"/> ";
        registry.register(id, xml, BeanOrigin.ANNOTATION);
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

    private String decapitalize(String name) {
        return name == null || name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}