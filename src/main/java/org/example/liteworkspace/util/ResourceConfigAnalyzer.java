package org.example.liteworkspace.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.*;

public class ResourceConfigAnalyzer {

    private final Project project;

    public ResourceConfigAnalyzer(Project project) {
        this.project = project;
    }

    /**
     * 分析 resource 下的 XML 配置文件，提取定义方式、扫描包、mapper 路径等
     */
    public Map<String, Object> analyzeSpringConfig() {
        Map<String, Object> result = new HashMap<>();
        Set<String> scanPackages = new HashSet<>();
        Set<String> mapperLocations = new HashSet<>();
        Set<String> processedFiles = new HashSet<>();

        Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, "xml", GlobalSearchScope.projectScope(project));
        for (VirtualFile file : files) {
            if (!file.getPath().contains("/resources/")) {
                continue;
            }
            if (!file.getName().contains("application") && !file.getName().contains("web")) {
                continue;
            }
            resolveXmlRecursive(file, scanPackages, mapperLocations, processedFiles);
        }

        result.put("componentScan", new ArrayList<>(scanPackages));
        result.put("mapperLocations", new ArrayList<>(mapperLocations));
        result.put("sourceFiles", new ArrayList<>(processedFiles));
        return result;
    }

    private void resolveXmlRecursive(VirtualFile file,
                                     Set<String> scanPackages,
                                     Set<String> mapperLocations,
                                     Set<String> visitedPaths) {

        String path = file.getPath();
        if (visitedPaths.contains(path)) {
            return;
        }
        visitedPaths.add(path);

        PsiFile psi = PsiManager.getInstance(project).findFile(file);
        if (!(psi instanceof XmlFile xml)) {
            return;
        }
        XmlTag root = xml.getRootTag();
        if (root == null) {
            return;
        }

        // 1. context:component-scan
        for (XmlTag tag : root.findSubTags("context:component-scan")) {
            String basePackage = tag.getAttributeValue("base-package");
            if (basePackage != null) scanPackages.add(basePackage);
        }

        // 2. mybatis mapperLocations
        for (XmlTag bean : root.findSubTags("bean")) {
            if (!"org.mybatis.spring.SqlSessionFactoryBean".equals(bean.getAttributeValue("class"))) continue;
            for (XmlTag property : bean.findSubTags("property")) {
                if (!"mapperLocations".equals(property.getAttributeValue("name"))) continue;

                XmlTag list = property.findFirstSubTag("list");
                if (list != null) {
                    for (XmlTag value : list.findSubTags("value")) {
                        mapperLocations.add(value.getValue().getText().trim());
                    }
                } else {
                    XmlTag value = property.findFirstSubTag("value");
                    if (value != null) {
                        mapperLocations.add(value.getValue().getText().trim());
                    }
                }
            }
        }

        // 3. 递归 import 标签
        for (XmlTag tag : root.findSubTags("import")) {
            String resource = tag.getAttributeValue("resource");
            if (resource != null) {
                VirtualFile imported = resolveResourcePath(resource);
                if (imported != null) {
                    resolveXmlRecursive(imported, scanPackages, mapperLocations, visitedPaths);
                }
            }
        }
    }

    /**
     * 解析 import resource 路径（相对 /resources/ 目录）
     */
    private VirtualFile resolveResourcePath(String resourcePath) {
        Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, "xml", GlobalSearchScope.projectScope(project));
        for (VirtualFile file : files) {
            if (!file.getPath().contains("/resources/")) continue;
            if (file.getPath().endsWith(resourcePath)) return file;
        }
        return null;
    }
}
