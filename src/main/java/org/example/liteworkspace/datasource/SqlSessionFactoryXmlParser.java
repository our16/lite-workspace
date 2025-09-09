package org.example.liteworkspace.datasource;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.example.liteworkspace.util.LogUtil;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.util.*;

public class SqlSessionFactoryXmlParser {

    public static List<SqlSessionConfig> parse(Project project) {
        LogUtil.info("SqlSessionFactoryXmlParser.parse");
        Set<SqlSessionConfig> result = new HashSet<>();
        // 1️⃣ 遍历所有模块
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            // 2️⃣ 找到每个模块的 resource 根目录
            for (VirtualFile resourceRoot :
                    ModuleRootManager.getInstance(module).getSourceRoots(JavaResourceRootType.RESOURCE)) {

                // 3️⃣ 扫描 resource 下的 XML 文件
                VfsUtil.iterateChildrenRecursively(resourceRoot, file -> true, file -> {
                    if (!file.isDirectory() && "xml".equalsIgnoreCase(file.getExtension())) {
                        result.addAll(parse(project, file));
                    }
                    return true;
                });
            }
        }
        return new ArrayList<>(result);
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
                        // 兼容 list / array / set
                        for (XmlTag container : prop.getSubTags()) {
                            String tagName = container.getLocalName();
                            if ("list".equals(tagName) || "array".equals(tagName) || "set".equals(tagName)) {
                                for (XmlTag val : container.findSubTags("value")) {
                                    config.getMapperLocations().add(val.getValue().getText());
                                }
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
