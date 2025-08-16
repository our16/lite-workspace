package org.example.liteworkspace.util;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MyBatisXmlFinder {

    private final Project project;

    // 存储 namespace -> mapper XML 相对路径 的映射
    private final Map<String, String> mybatisNamespaceMap = new HashMap<>();

    // 移除 static finder，每个 Project 单独 new 一个实例
    public MyBatisXmlFinder(Project project) {
        this.project = project;
        // 不再在构造函数中自动加载，改为按需或由外部调用加载方法
    }

    /**
     * 扫描并加载所有 Mapper XML 的 namespace -> 相对路径 映射
     * 可由外部调用以初始化 mybatisNamespaceMap
     */
    public void loadMapperNamespaceMap(Set<String> miniPackages) {
        this.mybatisNamespaceMap.clear(); // 避免重复加载
        this.mybatisNamespaceMap.putAll(findAllMapperXmlNamespaceToPathMap(miniPackages));
    }

    /**
     * 获取当前已加载的 namespace -> mapper XML 路径 映射
     */
    public Map<String, String> getMybatisNamespaceMap() {
        return mybatisNamespaceMap;
    }

    /**
     * 扫描项目中所有 <mapper> 类型的 XML 文件，
     * 返回 namespace -> mapper XML 相对路径 的映射
     */
    private Map<String, String> findAllMapperXmlNamespaceToPathMap(Set<String> miniPackages) {
        Map<String, String> namespaceToPathMap = new HashMap<>();
        if (miniPackages.isEmpty()) {
            return namespaceToPathMap;
        }

        // ------------------- 1️⃣ 扫描项目范围 -------------------
        Collection<VirtualFile> xmlFiles = FileBasedIndex.getInstance()
                .getContainingFiles(FileTypeIndex.NAME, XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project));

        // ------------------- 2️⃣ 扫描依赖库 JAR -------------------
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            GlobalSearchScope libScope = GlobalSearchScope.moduleWithLibrariesScope(module);
            Collection<VirtualFile> libXmlFiles = FileBasedIndex.getInstance()
                    .getContainingFiles(FileTypeIndex.NAME, XmlFileType.INSTANCE, libScope);
            xmlFiles.addAll(libXmlFiles);
        }

        // ------------------- 3️⃣ 遍历所有 XML 文件 -------------------
        for (VirtualFile file : xmlFiles) {
            if (file == null || !file.isValid() || !"xml".equalsIgnoreCase(file.getExtension())) continue;

            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (!(psiFile instanceof XmlFile xmlFile)) continue;

            XmlTag root = xmlFile.getRootTag();
            if (root == null || !"mapper".equals(root.getName())) continue;

            String namespace = root.getAttributeValue("namespace");
            if (namespace == null || namespace.trim().isEmpty()) continue;

            // ------------------- 4️⃣ 严格过滤 miniPackages -------------------
            boolean matchesPackage = miniPackages.stream().anyMatch(namespace::startsWith);
            if (!matchesPackage) continue;

            String relativePath = getRelativePathFromFile(file);
            if (relativePath != null) {
                namespaceToPathMap.put(namespace, relativePath);
            }
        }

        return namespaceToPathMap;
    }


    /**
     * 获取 XML 文件相对于项目根目录的路径
     */
    private String getRelativePathFromFile(VirtualFile file) {
        String projectBasePath = project.getBasePath();
        if (projectBasePath == null || file == null) {
            return null;
        }

        String fullPath = file.getPath();
        if (fullPath.startsWith(projectBasePath)) {
            String relative = fullPath.substring(projectBasePath.length() + 1); // 去掉项目根
            return relative.replace('\\', '/'); // 统一为 Unix 风格
        }
        return null;
    }

    /**
     * 判断给定 Mapper 接口是否有对应的 XML 文件且 namespace 匹配
     */
    public boolean hasMatchingMapperXml(@NotNull PsiClass mapperInterface) {
        String expectedNamespace = mapperInterface.getQualifiedName();
        if (expectedNamespace == null) {
            return false;
        }

        // 先查本地缓存
        if (mybatisNamespaceMap.containsKey(expectedNamespace)) {
            return true;
        }

        // 查项目内所有 XML 文件，并在 read action 内做 PSI 解析
        return FileBasedIndex.getInstance()
                .getContainingFiles(FileTypeIndex.NAME, XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project))
                .stream()
                .filter(file -> file.getName().endsWith(".xml"))
                .anyMatch(file -> xmlMatchesNamespaceSafe(file, expectedNamespace));
    }

    /**
     * 线程安全版本，确保在后台线程读取 PSI 时使用 read action
     */
    private boolean xmlMatchesNamespaceSafe(VirtualFile file, String expectedNamespace) {
        return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (!(psiFile instanceof XmlFile xmlFile)) {
                return false;
            }
            XmlTag rootTag = xmlFile.getRootTag();
            if (rootTag == null) {
                return false;
            }
            String namespace = rootTag.getAttributeValue("namespace");
            return expectedNamespace.equals(namespace);
        });
    }
}