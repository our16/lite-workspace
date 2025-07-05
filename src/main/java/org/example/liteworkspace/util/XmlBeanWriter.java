package org.example.liteworkspace.util;

import java.util.Map;

public class XmlBeanWriter {

    public static String generateXml(Map<String, String> beanMap) {
        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<beans xmlns=\"http://www.springframework.org/schema/beans\"\n");
        xml.append("       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        xml.append("       xsi:schemaLocation=\"http://www.springframework.org/schema/beans\n");
        xml.append("        https://www.springframework.org/schema/beans/spring-beans.xsd\">\n\n");

        // 补充公共 Bean，例如 dataSource 和 sqlSessionFactory（仅在需要时）
        if (beanMap.containsKey("sqlSessionFactory") && !beanMap.containsKey("dataSource")) {
            xml.append(getDefaultDataSourceBean()).append("\n");
        }
        if (beanMap.containsKey("sqlSessionFactory") && !beanMap.containsKey("sqlSessionFactory")) {
            xml.append(getDefaultSqlSessionFactoryBean()).append("\n");
        }

        // 添加业务 bean
        for (String beanXml : beanMap.values()) {
            xml.append(beanXml).append("\n");
        }

        xml.append("</beans>\n");
        return xml.toString();
    }

    private static String getDefaultDataSourceBean() {
        return "    <bean id=\"dataSource\" class=\"org.apache.commons.dbcp2.BasicDataSource\">\n" +
                "        <property name=\"driverClassName\" value=\"com.mysql.cj.jdbc.Driver\"/>\n" +
                "        <property name=\"url\" value=\"jdbc:mysql://localhost:3306/test\"/>\n" +
                "        <property name=\"username\" value=\"root\"/>\n" +
                "        <property name=\"password\" value=\"123456\"/>\n" +
                "    </bean>";
    }

    private static String getDefaultSqlSessionFactoryBean() {
        return "    <bean id=\"sqlSessionFactory\" class=\"org.mybatis.spring.SqlSessionFactoryBean\">\n" +
                "        <property name=\"dataSource\" ref=\"dataSource\"/>\n" +
                "    </bean>";
    }
}
