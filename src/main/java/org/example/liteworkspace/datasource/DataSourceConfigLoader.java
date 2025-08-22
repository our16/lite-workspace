package org.example.liteworkspace.datasource;

import com.intellij.openapi.project.Project;

import java.util.*;

/**
 * 统一数据源配置加载器
 * 支持 application.yml / application.yaml + mybatis XML 配置
 */
public class DataSourceConfigLoader {

    public static List<SqlSessionConfig> load(Project project) {

        // 2. XML 解析
        return new ArrayList<>(SqlSessionFactoryXmlParser.parse(project));
    }
}
