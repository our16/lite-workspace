//package org.example.liteworkspace.datasource;
//
//import com.intellij.openapi.project.Project;
//import org.yaml.snakeyaml.Yaml;
//
//import java.io.InputStream;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
//public class YamlDataSourceParser {
//    public static List<DataSourceConfig> parse(Project project) {
//        List<DataSourceConfig> result = new ArrayList<>();
//
//        // 这里用 SnakeYAML 解析 application.yml
//        Yaml yaml = new Yaml();
//        InputStream inputStream = ... // 读 application.yml
//        Map<String, Object> obj = yaml.load(inputStream);
//
//        Map<String, Object> mybatis = (Map<String, Object>) obj.get("mybatis");
//        if (mybatis != null) {
//            for (String key : mybatis.keySet()) {
//                Map<String, Object> dsMap = (Map<String, Object>) mybatis.get(key);
//                DataSourceConfig config = new DataSourceConfig();
//                config.setName(key);
//                config.setDataSourceBeanId(key + "DataSource");
//                config.setSqlSessionFactoryBeanId(key + "SqlSessionFactory");
//                config.getMapperLocations().add((String) dsMap.get("mapper-locations"));
//                result.add(config);
//            }
//        }
//        return result;
//    }
//}
