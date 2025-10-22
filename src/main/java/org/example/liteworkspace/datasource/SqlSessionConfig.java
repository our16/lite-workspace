package org.example.liteworkspace.datasource;

import java.util.*;

/**
 * 数据源配置封装类
 */
public class SqlSessionConfig {
    private String name; // master / slave / tidb / default
    private String dataSourceBeanId;
    private String sqlSessionFactoryBeanId;
    private List<String> mapperBasePackages = new ArrayList<>();
    private List<String> mapperLocations = new ArrayList<>();

    private static final String DEFAULT_DATASOURCE_XML = """
    <bean id="dataSource" class="org.springframework.jdbc.datasource.DriverManagerDataSource">
        <property name="driverClassName" value="${driver}"/>
        <property name="url" value="${url}"/>
        <property name="username" value="${username}"/>
        <property name="password" value="${password}"/>
    </bean>
""";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDataSourceBeanId() {
        return dataSourceBeanId;
    }

    public void setDataSourceBeanId(String dataSourceBeanId) {
        this.dataSourceBeanId = dataSourceBeanId;
    }

    public String getSqlSessionFactoryBeanId() {
        return sqlSessionFactoryBeanId;
    }

    public void setSqlSessionFactoryBeanId(String sqlSessionFactoryBeanId) {
        this.sqlSessionFactoryBeanId = sqlSessionFactoryBeanId;
    }

    public List<String> getMapperBasePackages() {
        return mapperBasePackages;
    }

    public void setMapperBasePackages(List<String> mapperBasePackages) {
        this.mapperBasePackages = mapperBasePackages;
    }

    public List<String> getMapperLocations() {
        return mapperLocations;
    }

    public void setMapperLocations(List<String> mapperLocations) {
        this.mapperLocations = mapperLocations;
    }
}
