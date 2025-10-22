package org.example.liteworkspace.datasource;

import com.intellij.openapi.project.Project;

import java.util.*;

/**
 * 统一数据源配置加载器
 * 支持 application.yml / application.yaml + mybatis XML 配置
 */
public class DataSourceConfigLoader {

    public static List<SqlSessionConfig> load(Project project) {
        List<SqlSessionConfig> result = new ArrayList<>();

        // 1. XML 解析
        result.addAll(SqlSessionFactoryXmlParser.parse(project));
        
        // 2. YAML 解析
        result.addAll(YamlDataSourceParser.parse(project));
        
        // 3. Properties 解析
        result.addAll(PropertiesDataSourceParser.parse(project));
        
        // 4. @MapperScan 注解解析
//        result.addAll(MapperScanAnnotationParser.parse(project));
        
        return result;
    }
}
