package org.example.liteworkspace.bean.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// 数据源配置封装类
public class DatasourceConfig {
    private final boolean isImported;
    private final String importPath;
    private final Map<String, String> configMap;
    private String mapperLocation; // ✅ 新增 mapper 路径

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

    /** ✅ 默认数据源 */
    public static DatasourceConfig createDefault() {
        return createCustomConfig(
                "default",
                "jdbc:mysql://localhost:3306/default_db",
                "root",
                "123456",
                "com.mysql.cj.jdbc.Driver",
                null
        );
    }

    // ---------------------- 工厂方法 ----------------------

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

    /** ✅ 自定义配置（多数据源时使用） */
    public static DatasourceConfig createCustomConfig(String name,
                                                      String url,
                                                      String username,
                                                      String password,
                                                      String driver,
                                                      String mapperLocation) {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("datasource.name", name);
        config.put("datasource.url", url);
        config.put("datasource.username", username);
        config.put("datasource.password", password);
        config.put("datasource.driver-class-name", driver);

        DatasourceConfig ds = new DatasourceConfig(false, null, config);
        ds.setMapperLocation(mapperLocation);
        return ds;
    }

    // ---------------------- 属性方法 ----------------------

    public boolean isImported() {
        return isImported;
    }

    public String getImportPath() {
        return importPath;
    }

    public Map<String, String> getConfigMap() {
        return configMap;
    }

    public String getMapperLocation() {
        return mapperLocation;
    }

    /** ✅ 允许后续动态修改 mapper 位置 */
    public void setMapperLocation(String mapperLocation) {
        this.mapperLocation = mapperLocation;
    }

    public Map<String, String> getDefaultDatasource() {
        if (configMap == null) return null;

        String populatedXml = DEFAULT_DATASOURCE_XML
                .replace("${driver}", configMap.get("datasource.driver-class-name"))
                .replace("${url}", configMap.get("datasource.url"))
                .replace("${username}", configMap.get("datasource.username"))
                .replace("${password}", configMap.get("datasource.password"));
        Map<String, String> xmlMap = new HashMap<>();
        xmlMap.put("dataSource", populatedXml);
        return xmlMap;
    }

    @Override
    public String toString() {
        return "DatasourceConfig{" +
                "isImported=" + isImported +
                ", importPath='" + importPath + '\'' +
                ", configMap=" + configMap +
                ", mapperLocation='" + mapperLocation + '\'' +
                '}';
    }
}
