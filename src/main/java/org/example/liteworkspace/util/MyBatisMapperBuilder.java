package org.example.liteworkspace.util;

import com.intellij.psi.PsiClass;

import java.util.Set;

public class MyBatisMapperBuilder implements BeanDefinitionBuilder {

    @Override
    public boolean supports(PsiClass clazz) {
        return clazz.isInterface();
    }

    @Override
    public String buildBeanXml(PsiClass clazz, Set<String> visited, XmlBeanAssembler assembler) {
        String id = decapitalize(clazz.getName());
        String className = clazz.getQualifiedName();

        return "    <bean id=\"" + id + "\" class=\"org.mybatis.spring.mapper.MapperFactoryBean\">\n" +
               "        <property name=\"mapperInterface\" value=\"" + className + "\"/>\n" +
               "        <property name=\"sqlSessionFactory\" ref=\"sqlSessionFactory\"/>\n" +
               "    </bean>";
    }

    private String decapitalize(String name) {
        return name == null || name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}