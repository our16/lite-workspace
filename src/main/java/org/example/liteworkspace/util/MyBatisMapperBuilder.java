package org.example.liteworkspace.util;

import com.intellij.psi.PsiClass;

import java.util.Map;
import java.util.Set;

public class MyBatisMapperBuilder implements BeanDefinitionBuilder {

    @Override
    public boolean supports(PsiClass clazz) {
        return clazz.isInterface();
    }

    @Override
    public void buildBeanXml(PsiClass clazz, Set<String> visited, Map<String, String> beanMap, XmlBeanAssembler assembler) {
        if (!supports(clazz) || visited.contains(clazz.getQualifiedName())) return;
        visited.add(clazz.getQualifiedName());

        String id = decapitalize(clazz.getName());
        String className = clazz.getQualifiedName();

        if (!beanMap.containsKey("dataSource")) {
            assembler.putBeanXml("dataSource", getDefaultDataSourceBean());
        }

        if (!beanMap.containsKey("sqlSessionFactory")) {
            assembler.putBeanXml("sqlSessionFactory", getDefaultSqlSessionFactoryBean());
        }

        String mapperBean =
                "    <bean id=\"" + id + "\" class=\"org.mybatis.spring.mapper.MapperFactoryBean\">\n" +
                        "        <property name=\"mapperInterface\" value=\"" + className + "\"/>\n" +
                        "        <property name=\"sqlSessionFactory\" ref=\"sqlSessionFactory\"/>\n" +
                        "    </bean>";

        assembler.putBeanXml(id, mapperBean);
    }

    private String decapitalize(String name) {
        return name == null || name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private String getDefaultDataSourceBean() {
        return "    <bean id=\"dataSource\" class=\"org.apache.commons.dbcp2.BasicDataSource\">\n" +
                "        <property name=\"driverClassName\" value=\"com.mysql.cj.jdbc.Driver\"/>\n" +
                "        <property name=\"url\" value=\"jdbc:mysql://localhost:3306/test\"/>\n" +
                "        <property name=\"username\" value=\"root\"/>\n" +
                "        <property name=\"password\" value=\"123456\"/>\n" +
                "    </bean>";
    }

    private String getDefaultSqlSessionFactoryBean() {
        return "    <bean id=\"sqlSessionFactory\" class=\"org.mybatis.spring.SqlSessionFactoryBean\">\n" +
                "        <property name=\"dataSource\" ref=\"dataSource\"/>\n" +
                "    </bean>";
    }
}
