package org.example.liteworkspace.util;

import com.intellij.psi.PsiClass;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class MyBatisMapperBuilder implements BeanDefinitionBuilder {

    private final Set<String> knownDataSourceClasses;
    private final Set<String> knownSqlSessionFactoryClasses;

    public MyBatisMapperBuilder() {
        Properties props = loadProperties();
        this.knownDataSourceClasses = parseClasses(props.getProperty("datasource.classes"));
        this.knownSqlSessionFactoryClasses = parseClasses(props.getProperty("sqlsessionfactory.classes"));
    }

    @Override
    public boolean supports(PsiClass clazz) {
        return clazz.isInterface() && clazz.getName() != null && clazz.getName().endsWith("Mapper");
    }

    @Override
    public void buildBeanXml(PsiClass clazz, Set<String> visited, Map<String, String> beanMap, XmlBeanAssembler assembler) {
        if (!supports(clazz) || visited.contains(clazz.getQualifiedName())) return;
        visited.add(clazz.getQualifiedName());

        // 检查是否已存在数据源或SqlSessionFactory类（根据类型而非id）
        boolean hasDataSource = beanMap.values().stream().anyMatch(xml ->
                knownDataSourceClasses.stream().anyMatch(xml::contains));
        boolean hasSessionFactory = beanMap.values().stream().anyMatch(xml ->
                knownSqlSessionFactoryClasses.stream().anyMatch(xml::contains));

        if (!hasDataSource) {
            assembler.putBeanXml("dataSource", getDefaultDataSourceBean());
        }

        if (!hasSessionFactory) {
            assembler.putBeanXml("sqlSessionFactory", getDefaultSqlSessionFactoryBean());
        }

        String id = decapitalize(clazz.getName());
        String className = clazz.getQualifiedName();

        String mapperBean =
                "    <bean id=\"" + id + "\" class=\"org.mybatis.spring.mapper.MapperFactoryBean\">\n" +
                        "        <property name=\"mapperInterface\" value=\"" + className + "\"/>\n" +
                        "        <property name=\"sqlSessionFactory\" ref=\"sqlSessionFactory\"/>\n" +
                        "    </bean>";

        assembler.putBeanXml(id, mapperBean);
    }

    private Properties loadProperties() {
        Properties props = new Properties();

        // Step 1: 先加载默认配置
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("datasource.properties")) {
            if (in != null) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}

        // Step 2: 再加载用户配置（覆盖默认）
        try {
            File custom = new File(System.getProperty("user.home"), ".lite-workspace/datasource.properties");
            if (custom.exists()) {
                try (InputStream in = new FileInputStream(custom)) {
                    props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {}

        return props;
    }


    private Set<String> parseClasses(String raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptySet();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
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

    private String decapitalize(String name) {
        return (name == null || name.isEmpty())
                ? name
                : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
