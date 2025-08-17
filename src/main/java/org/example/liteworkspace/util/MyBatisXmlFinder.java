package org.example.liteworkspace.util;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MyBatisXmlFinder {

    private final Project project;

    /**
     * namespace -> mapper XML 相对路径 映射
     * 可以按需加载
     */
    private final Map<String, String> namespaceCache = new HashMap<>();

    public MyBatisXmlFinder(Project project) {
        this.project = project;
    }

    /**
     * 加载所有 Mapper XML 的 namespace -> 相对路径 映射
     * 支持按 miniPackages 过滤
     */
    public void loadMapperNamespaceMap(Set<String> miniPackages) {
        namespaceCache.clear();
        namespaceCache.putAll(scanAllMapperXml(miniPackages));
    }

    /**
     * 获取缓存
     */
    public Map<String, String> getNamespaceMap() {
        return namespaceCache;
    }

    /**
     * 判断给定 Mapper Interface 是否有对应 XML
     */
    public boolean hasMatchingMapperXml(@NotNull PsiClass mapperInterface) {
        String expectedNamespace = mapperInterface.getQualifiedName();
        if (expectedNamespace == null) return false;

        // 先查缓存
        if (namespaceCache.containsKey(expectedNamespace)) {
            return true;
        }

        // 再查索引（全局搜索）
        Collection<VirtualFile> xmlFiles = FileBasedIndex.getInstance()
                .getContainingFiles(FileTypeIndex.NAME, XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project));

        for (VirtualFile vf : xmlFiles) {
            if (!vf.getName().endsWith(".xml")) continue;

            boolean match = ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
                if (!(psiFile instanceof XmlFile xmlFile)) return false;
                XmlTag rootTag = xmlFile.getRootTag();
                if (rootTag == null || !"mapper".equals(rootTag.getName())) return false;

                String namespace = rootTag.getAttributeValue("namespace");
                return expectedNamespace.equals(namespace);
            });

            if (match) return true;
        }

        return false;
    }

    /**
     * 扫描项目和依赖 JAR 中的 Mapper XML
     */
    private Map<String, String> scanAllMapperXml(Set<String> miniPackages) {
        Map<String, String> result = new HashMap<>();
        Collection<VirtualFile> xmlFiles = FileBasedIndex.getInstance()
                .getContainingFiles(FileTypeIndex.NAME, XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project));

        for (VirtualFile vf : xmlFiles) {
            // 快速路径过滤：只保留 mapper 文件
            if (!vf.getName().endsWith(".xml")) continue;
            String path = vf.getPath().replace('\\', '/');
            if (!path.contains("/mapper/") && !path.contains("/mappers/") && !vf.getName().endsWith("Mapper.xml")) {
                continue;
            }

            // PSI 安全读取 rootTag
            String namespace = ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
                if (!(psiFile instanceof XmlFile xmlFile)) return null;
                XmlTag rootTag = xmlFile.getRootTag();
                if (rootTag == null || !"mapper".equals(rootTag.getName())) return null;
                return rootTag.getAttributeValue("namespace");
            });

            if (namespace != null && !namespace.isEmpty()) {
                String relative = getRelativePath(vf);
                if (relative != null) {
                    result.put(namespace, relative);
                }
            }
        }

        // 扫描依赖 JAR（仅 LibraryOrderEntry）
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
                if (!(entry instanceof LibraryOrderEntry libEntry)) continue;
                for (VirtualFile root : libEntry.getRootFiles(OrderRootType.CLASSES)) {
                    if (!root.isValid()) continue;

                    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<>() {
                        @Override
                        public boolean visitFile(@NotNull VirtualFile file) {
                            if (!file.isValid() || file.isDirectory()) return true;
                            String fName = file.getName();
                            if (!fName.endsWith(".xml")) return true;
                            String fPath = file.getPath().replace('\\', '/');
                            if (!fPath.contains("/mapper/") && !fPath.contains("/mappers/") && !fName.endsWith("Mapper.xml"))
                                return true;

                            // PSI 安全读取
                            String ns = ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
                                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                                if (!(psiFile instanceof XmlFile xmlFile)) return null;
                                XmlTag rootTag = xmlFile.getRootTag();
                                if (rootTag == null || !"mapper".equals(rootTag.getName())) return null;
                                return rootTag.getAttributeValue("namespace");
                            });

                            if (ns != null && !ns.isEmpty()) {
                                String rel = file.getPath(); // jar 内直接使用绝对路径
                                result.put(ns, rel);
                            }

                            return true;
                        }
                    });
                }
            }
        }

        return result;
    }

    /**
     * 获取项目内 XML 相对路径
     */
    private String getRelativePath(VirtualFile file) {
        String base = project.getBasePath();
        if (base == null) return null;
        String full = file.getPath().replace('\\', '/');
        if (full.startsWith(base.replace('\\', '/'))) {
            return full.substring(base.length() + 1);
        }
        return full;
    }
}
