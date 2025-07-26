package org.example.liteworkspace.bean.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// 数据源配置封装类
public  class DatasourceConfig {
    private final boolean isImported;
    private final String importPath;
    private final Map<String, String> configMap;

    private static final String DEFAULT_DATASOURCE_XML = """
    <bean id="dataSource" class="org.springframework.jdbc.datasource.DriverManagerDataSource">
        <property name="driverClassName" value="${driver}"/>
        <property name="url" value="${url}"/>
        <property name="username" value="${username}"/>
        <property name="password" value="${password}"/>
    </bean>
""";

    public DatasourceConfig(boolean isImported, String importPath, Map<String, String> configMap) {
        this.isImported = isImported;
        this.importPath = importPath;
        this.configMap = configMap;
    }

    public DatasourceConfig() {
        this(false, null, null);
    }

    public static DatasourceConfig createImportedConfig(String importPath) {
        return new DatasourceConfig(true, importPath, null);
    }

    public static DatasourceConfig createDefaultConfig(String url, String username,
                                                       String password, String driver) {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("datasource.url", url);
        config.put("datasource.username", username);
        config.put("datasource.password", password);
        config.put("datasource.driver-class-name", driver);
        return new DatasourceConfig(false, null, config);
    }

    public boolean isImported() {
        return isImported;
    }

    public String getImportPath() {
        return importPath;
    }

    public Map<String, String> getDefaultDatasource() {
        // 使用模板方式填充默认配置
        Map<String, String> config = this.configMap;
        String populatedXml = DEFAULT_DATASOURCE_XML
                .replace("${driver}", config.get("datasource.driver-class-name"))
                .replace("${url}", config.get("datasource.url"))
                .replace("${username}", config.get("datasource.username"))
                .replace("${password}", config.get("datasource.password"));
        Map<String, String> xmlMap = new HashMap<>();
        xmlMap.put("dataSource", populatedXml);
        return xmlMap;
    }
}
