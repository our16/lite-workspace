package org.example.liteworkspace.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.example.liteworkspace.bean.core.ProjectCacheStore;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class ResourceConfigAnalyzer {

    private final Project project;
    private final ProjectCacheStore cacheStore;

    public ResourceConfigAnalyzer(Project project, String projectId) {
        this.project = project;
        this.cacheStore = new ProjectCacheStore(projectId);
    }

    public Set<String> scanComponentScanPackages() {
        Set<String> result = new HashSet<>();
        for (XmlFile xml : findRelevantXmlFiles()) {
            scanXmlForComponentScan(xml, result, new HashSet<>());
        }
        return result;
    }

    public Set<PsiClass> scanConfigurationClasses() {
        Set<PsiClass> result = new HashSet<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<PsiClass> all = PsiShortNamesCache.getInstance(project).getAllClassNames().length == 0
                ? List.of()
                : Arrays.asList(PsiShortNamesCache.getInstance(project).getClassesByName("Configuration", scope)); // 快速空判断

        for (VirtualFile vf : FilenameIndex.getAllFilesByExt(project, "java", scope)) {
            PsiFile file = PsiManager.getInstance(project).findFile(vf);
            if (!(file instanceof PsiJavaFile javaFile)) {
                continue;
            }
            for (PsiClass clazz : javaFile.getClasses()) {
                if (clazz.hasAnnotation("org.springframework.context.annotation.Configuration")) {
                    result.add(clazz);
                }
            }
        }

        return result;
    }

    /**
     * 只读取 test/resource/configs/testDatasource.xml 的数据库配置，
     * 如果文件不存在，则返回默认配置
     *
     * @return Map<String, String> 数据源配置
     */
    public Map<String, String> scanSpringDatasourceConfigs() {
        Map<String, String> datasourceConfigs = new LinkedHashMap<>();

        // 1. 优先读取 test/resources/configs/testDatasource.xml
        VirtualFile configFile = findTestDatasourceXml();
        if (configFile != null && configFile.isValid()) {
            datasourceConfigs.put("import 配置配置，不用解析", "jdbc:mysql://localhost:3306/default_db");
        } else {
            // 2. 如果文件不存在，使用默认配置
            datasourceConfigs.put("datasource.url", "jdbc:mysql://localhost:3306/default_db");
            datasourceConfigs.put("datasource.username", "root");
            datasourceConfigs.put("datasource.password", "123456");
            datasourceConfigs.put("datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        }

        return datasourceConfigs;
    }

    /**
     * 查找 test/resources/configs/testDatasource.xml 文件
     */
    private VirtualFile findTestDatasourceXml() {
        String filePath = "src/test/resources/configs/testDatasource.xml";
        VirtualFile baseDir = project.getProjectFile();
        if (baseDir == null) return null;
        return baseDir.findFileByRelativePath(filePath);
    }

    /**
     * 扫描 XML 文件中的 <bean class="...DataSource"> 以及 <property name="url" .../> 配置
     */
    private void scanDatasourceXmlConfig(VirtualFile xmlFile, Map<String, String> configs) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(xmlFile);
        if (!(psiFile instanceof XmlFile xml)) {
            return;
        }

        XmlTag root = xml.getRootTag();
        if (root == null) return;

        for (XmlTag beanTag : root.findSubTags("bean")) {
            String className = beanTag.getAttributeValue("class");
            if (className == null || !className.toLowerCase().contains("datasource")) {
                continue;
            }

            for (XmlTag propTag : beanTag.findSubTags("property")) {
                String name = propTag.getAttributeValue("name");
                String value = propTag.getAttributeValue("value");
                if (name != null && value != null) {
                    configs.put("datasource." + name, value); // 统一前缀
                }
            }
        }
    }

    private List<XmlFile> findRelevantXmlFiles() {
        List<XmlFile> xmlFiles = new ArrayList<>();
        for (VirtualFile vf : FilenameIndex.getAllFilesByExt(project, "xml", GlobalSearchScope.projectScope(project))) {
            if (!vf.getPath().contains("/resources/")) continue;

            PsiFile psi = PsiManager.getInstance(project).findFile(vf);
            if (psi instanceof XmlFile xml) {
                XmlTag root = xml.getRootTag();
                if (root != null && ("beans".equals(root.getName()) || "context:component-scan".equals(root.getName()))) {
                    xmlFiles.add(xml);
                }
            }
        }
        return xmlFiles;
    }

    private void scanXmlForComponentScan(XmlFile xml, Set<String> scanPackages, Set<String> visitedPaths) {
        String path = xml.getVirtualFile().getPath();
        if (!visitedPaths.add(path)) return;

        XmlTag root = xml.getRootTag();
        if (root == null) return;

        for (XmlTag tag : root.findSubTags("context:component-scan")) {
            String basePackage = tag.getAttributeValue("base-package");
            if (basePackage != null) scanPackages.add(basePackage);
        }

        for (XmlTag tag : root.findSubTags("import")) {
            String resource = tag.getAttributeValue("resource");
            if (resource != null) {
                VirtualFile imported = resolveResourcePath(resource, xml.getVirtualFile());
                if (imported != null) {
                    PsiFile psi = PsiManager.getInstance(project).findFile(imported);
                    if (psi instanceof XmlFile importedXml) {
                        scanXmlForComponentScan(importedXml, scanPackages, visitedPaths);
                    }
                }
            }
        }
    }

    @Nullable
    private VirtualFile resolveResourcePath(String path, VirtualFile base) {
        VirtualFile baseDir = base.getParent();
        if (path.startsWith("classpath:")) path = path.replace("classpath:", "");
        VirtualFile resolved = baseDir.findFileByRelativePath(path);
        if (resolved == null) {
            resolved = base.getFileSystem().findFileByPath(project.getBasePath() + "/src/main/resources/" + path);
        }
        return resolved;
    }
}
