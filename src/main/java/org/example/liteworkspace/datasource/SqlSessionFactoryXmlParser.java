package org.example.liteworkspace.datasource;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.*;

public class SqlSessionFactoryXmlParser {

    public static List<SqlSessionConfig> parse(Project project) {
        List<SqlSessionConfig> result = new ArrayList<>();
        Map<String, SqlSessionConfig> configs = new HashMap<>();

        // 1️⃣ 找到 resources 目录
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) return result;

        VirtualFile resourcesDir = baseDir.findFileByRelativePath("src/main/resources");
        if (resourcesDir == null) return result;

        // 2️⃣ 扫描 resources 下的 XML 文件
        VfsUtil.iterateChildrenRecursively(resourcesDir, file -> true, file -> {
            if (!file.isDirectory() && "xml".equalsIgnoreCase(file.getExtension())) {
                result.addAll(SqlSessionFactoryXmlParser.parse(project, file));
            }
            return true;
        });

        result.addAll(configs.values());
        return result;
    }

    public static List<SqlSessionConfig> parse(Project project, VirtualFile xmlFile) {
        List<SqlSessionConfig> result = new ArrayList<>();
        PsiFile psiFile = PsiManager.getInstance(project).findFile(xmlFile);
        if (!(psiFile instanceof XmlFile xml)) return result;

        XmlTag root = xml.getRootTag();
        if (root == null) return result;

        Map<String, SqlSessionConfig> configMap = new LinkedHashMap<>();
        // 1️⃣ 找到 SqlSessionFactoryBean
        for (XmlTag bean : root.findSubTags("bean")) {
            String clazz = bean.getAttributeValue("class");
            if (clazz != null && clazz.contains("SqlSessionFactoryBean")) {
                String id = bean.getAttributeValue("id");
                SqlSessionConfig config = new SqlSessionConfig();
                config.setSqlSessionFactoryBeanId(id);
                config.setName(id);
                // mapperLocations
                for (XmlTag prop : bean.findSubTags("property")) {
                    if ("mapperLocations".equals(prop.getAttributeValue("name"))) {
                        for (XmlTag list : prop.findSubTags("list")) {
                            for (XmlTag val : list.findSubTags("value")) {
                                config.getMapperLocations().add(val.getValue().getText());
                            }
                        }
                        XmlTag value = prop.findFirstSubTag("value");
                        if (value != null) {
                            config.getMapperLocations().add(value.getValue().getText());
                        }
                    }

                    // dataSource 引用
                    if ("dataSource".equals(prop.getAttributeValue("name"))) {
                        config.setDataSourceBeanId(prop.getAttributeValue("ref"));
                    }
                }

                configMap.put(config.getName(), config);
            }
        }

        // 2️⃣ 找到 MapperScannerConfigurer
        for (XmlTag bean : root.findSubTags("bean")) {
            String clazz = bean.getAttributeValue("class");
            if (clazz != null && clazz.contains("MapperScannerConfigurer")) {
                String sqlSessionFactoryRef = null;
                String basePackage = null;

                for (XmlTag prop : bean.findSubTags("property")) {
                    if ("sqlSessionFactoryBeanName".equals(prop.getAttributeValue("name"))) {
                        sqlSessionFactoryRef = prop.getAttributeValue("value");
                    }
                    if ("basePackage".equals(prop.getAttributeValue("name"))) {
                        basePackage = prop.getAttributeValue("value");
                    }
                }

                if (sqlSessionFactoryRef != null && basePackage != null) {
                    SqlSessionConfig config = configMap.computeIfAbsent(sqlSessionFactoryRef, k -> new SqlSessionConfig());
                    config.getMapperBasePackages().addAll(Arrays.asList(basePackage.split(";")));
                }
            }
        }

        result.addAll(configMap.values());
        return result;
    }
}
