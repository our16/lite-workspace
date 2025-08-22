package org.example.liteworkspace.bean.engine;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.example.liteworkspace.bean.core.DatasourceConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

public class DatasourceConfigAnalyzer {
    private final Project project;

    public DatasourceConfigAnalyzer(Project project) {
        this.project = project;
    }

    /**
     * 扫描 src/main/resources 下配置，返回 Map<数据源Key, DatasourceConfig>
     */
    public Map<String, DatasourceConfig> scanAllDatasourceConfigs() {
        Map<String, DatasourceConfig> result = new HashMap<>();

        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        for (VirtualFile root : roots) {
            VirtualFile srcMainResources = root.findFileByRelativePath("src/main/resources");
            if (srcMainResources != null && srcMainResources.isDirectory()) {
                scanResourceDirectory(srcMainResources, result);
            }
        }

        if (result.isEmpty()) {
            result.put("default", DatasourceConfig.createDefault());
        }
        return result;
    }

    private void scanResourceDirectory(VirtualFile dir, Map<String, DatasourceConfig> result) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                scanResourceDirectory(child, result);
            } else if (child.isValid()) {
                String ext = child.getExtension();
                if ("xml".equalsIgnoreCase(ext)) {
                    result.putAll(parseSpringXmlDatasource(child));
                } else if ("properties".equalsIgnoreCase(ext)) {
                    result.putAll(parseSpringPropertiesDatasource(child));
                } else if ("yml".equalsIgnoreCase(ext) || "yaml".equalsIgnoreCase(ext)) {
                    result.putAll(parseSpringYmlDatasource(child));
                }
            }
        }
    }

    private Map<String, DatasourceConfig> parseSpringXmlDatasource(VirtualFile xmlFile) {
        Map<String, DatasourceConfig> map = new HashMap<>();
        PsiFile psiFile = PsiManager.getInstance(project).findFile(xmlFile);
        if (!(psiFile instanceof XmlFile xml)) return map;

        XmlTag root = xml.getRootTag();
        if (root == null) return map;

        for (XmlTag beanTag : root.findSubTags("bean")) {
            String clazz = beanTag.getAttributeValue("class");
            if (clazz == null || !clazz.toLowerCase().contains("datasource")) continue;

            String id = beanTag.getAttributeValue("id");
            if (id == null) id = "datasource" + map.size();

            Map<String, String> props = new HashMap<>();
            for (XmlTag prop : beanTag.findSubTags("property")) {
                props.put(prop.getAttributeValue("name"), prop.getAttributeValue("value"));
            }

            String mapperDir = findMapperLocationFromXml(root);

            DatasourceConfig config = DatasourceConfig.createCustomConfig(
                    id,
                    props.get("url"),
                    props.get("username"),
                    props.get("password"),
                    props.get("driverClassName"),
                    mapperDir
            );

            map.put(id, config);
        }
        return map;
    }

    private Map<String, DatasourceConfig> parseSpringPropertiesDatasource(VirtualFile propFile) {
        Map<String, DatasourceConfig> map = new HashMap<>();
        Properties props = new Properties();
        try (InputStream is = propFile.getInputStream()) {
            props.load(is);
        } catch (Exception ignored) {
        }

        if (props.containsKey("spring.datasource.url")) {
            DatasourceConfig config = DatasourceConfig.createCustomConfig(
                    "default",
                    props.getProperty("spring.datasource.url"),
                    props.getProperty("spring.datasource.username"),
                    props.getProperty("spring.datasource.password"),
                    props.getProperty("spring.datasource.driver-class-name"),
                    props.getProperty("mybatis.mapper-locations")
            );
            map.put("default", config);
        }

        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("spring.datasource.") && key.endsWith(".url")) {
                String dsName = key.substring("spring.datasource.".length(), key.length() - ".url".length());
                DatasourceConfig config = DatasourceConfig.createCustomConfig(
                        dsName,
                        props.getProperty(key),
                        props.getProperty("spring.datasource." + dsName + ".username"),
                        props.getProperty("spring.datasource." + dsName + ".password"),
                        props.getProperty("spring.datasource." + dsName + ".driver-class-name"),
                        props.getProperty("mybatis." + dsName + ".mapper-locations")
                );
                map.put(dsName, config);
            }
        }

        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, DatasourceConfig> parseSpringYmlDatasource(VirtualFile ymlFile) {
        Map<String, DatasourceConfig> map = new HashMap<>();
        try (InputStream is = ymlFile.getInputStream()) {
            Yaml yaml = new Yaml();
            Object data = yaml.load(is);
            if (!(data instanceof Map<?, ?> root)) return map;

            Object dsNode = root.get("spring");
            if (!(dsNode instanceof Map<?, ?> springMap)) return map;

            Object dsConfig = springMap.get("datasource");
            if (dsConfig instanceof Map<?, ?> dsMap) {
                if (dsMap.containsKey("url")) {
                    DatasourceConfig config = DatasourceConfig.createCustomConfig(
                            "default",
                            (String) dsMap.get("url"),
                            (String) dsMap.get("username"),
                            String.valueOf(dsMap.get("password")),
                            (String) dsMap.get("driver-class-name"),
                            extractMapperLocation(springMap, null)
                    );
                    map.put("default", config);
                } else {
                    for (Map.Entry<?, ?> entry : dsMap.entrySet()) {
                        String dsName = String.valueOf(entry.getKey());
                        if (!(entry.getValue() instanceof Map)) {
                            continue;
                        }
                        Map<?, ?> child = (Map<?, ?>) entry.getValue();
                        DatasourceConfig config = DatasourceConfig.createCustomConfig(
                                dsName,
                                (String) child.get("url"),
                                (String) child.get("username"),
                                String.valueOf(child.get("password")),
                                (String) child.get("driver-class-name"),
                                extractMapperLocation(springMap, dsName)
                        );
                        map.put(dsName, config);
                    }
                }
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
        return map;
    }

    private String extractMapperLocation(Map<?, ?> springMap, String dsName) {
        Object mybatisNode = springMap.get("mybatis");
        if (!(mybatisNode instanceof Map<?, ?> mybatisMap)) return null;
        if (dsName == null) return (String) mybatisMap.get("mapper-locations");
        Object child = mybatisMap.get(dsName);
        if (child instanceof Map<?, ?> c) return (String) c.get("mapper-locations");
        return null;
    }

    private String findMapperLocationFromXml(XmlTag root) {
        for (XmlTag tag : root.findSubTags("bean")) {
            String clazz = tag.getAttributeValue("class");
            if (clazz != null && clazz.contains("MapperScannerConfigurer")) {
                for (XmlTag prop : tag.findSubTags("property")) {
                    if ("basePackage".equals(prop.getAttributeValue("name")) ||
                            "mapperLocations".equals(prop.getAttributeValue("name"))) {
                        return prop.getAttributeValue("value");
                    }
                }
            }
        }
        return null;
    }
}
