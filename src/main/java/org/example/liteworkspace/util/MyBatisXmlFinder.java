package org.example.liteworkspace.util;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.project.Project;
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
    public void loadMapperNamespaceMap() {
        this.mybatisNamespaceMap.clear(); // 避免重复加载
        this.mybatisNamespaceMap.putAll(findAllMapperXmlNamespaceToPathMap());
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
    private Map<String, String> findAllMapperXmlNamespaceToPathMap() {
        Map<String, String> namespaceToPathMap = new HashMap<>();

        Collection<VirtualFile> xmlFiles = FileBasedIndex.getInstance()
                .getContainingFiles(FileTypeIndex.NAME, XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project));

        for (VirtualFile file : xmlFiles) {
            if (file == null || !file.isValid() || !file.getExtension().equalsIgnoreCase("xml")) {
                continue;
            }

            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (!(psiFile instanceof XmlFile xmlFile)) {
                continue;
            }

            XmlTag root = xmlFile.getRootTag();
            if (root == null || !"mapper".equals(root.getName())) {
                continue;
            }

            String namespace = root.getAttributeValue("namespace");
            if (namespace == null || namespace.trim().isEmpty()) {
                continue;
            }

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

        // 再查项目内所有 XML 文件
        return FileBasedIndex.getInstance()
                .getContainingFiles(FileTypeIndex.NAME, XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project))
                .stream()
                .filter(file -> file.getName().endsWith(".xml"))
                .anyMatch(file -> xmlMatchesNamespace(file, expectedNamespace));
    }

    /**
     * 判断某个 XML 文件是否是 <mapper> 且 namespace 匹配
     */
    private boolean xmlMatchesNamespace(VirtualFile file, String expectedNamespace) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (!(psiFile instanceof XmlFile xmlFile)) return false;

        XmlTag root = xmlFile.getRootTag();
        if (root == null || !"mapper".equals(root.getName())) {
            return false;
        }

        String namespace = root.getAttributeValue("namespace");
        return expectedNamespace.equals(namespace);
    }
}