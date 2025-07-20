package org.example.liteworkspace.util;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
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

import java.util.Collection;
import java.util.Objects;

public class MyBatisXmlFinder {

    private final Project project;

    private static MyBatisXmlFinder finder;

    private MyBatisXmlFinder(Project project) {
        this.project = project;
    }

    public static MyBatisXmlFinder build(Project project) {
        if (finder != null) {
            return finder;
        }
        if (project == null) {
            throw new RuntimeException("Project上下文为空");
        }
        finder = new MyBatisXmlFinder(project);
        return finder;
    }

    /**
     * 判断给定接口是否有同名的 Mapper XML 文件，且 namespace 匹配
     */
    public boolean hasMatchingMapperXml(@NotNull PsiClass mapperInterface) {
        String expectedNamespace = mapperInterface.getQualifiedName();
        if (expectedNamespace == null) {
            return false;
        }

        return FileBasedIndex.getInstance()
                .getContainingFiles(FileTypeIndex.NAME, com.intellij.ide.highlighter.XmlFileType.INSTANCE,
                        GlobalSearchScope.projectScope(project))
                .stream()
                .filter(file -> file.getName().endsWith(".xml"))
                .anyMatch(file -> xmlMatchesNamespace(file, expectedNamespace));
    }

    /**
     * 判断 XML 文件是否 namespace 与类名匹配
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

    public String findRelativePathByNamespaces(@NotNull String expectedNamespace) {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
            for (VirtualFile root : roots) {
                String path = root.getPath().replace("\\", "/");
                if (!path.contains("/resources")) continue;

                GlobalSearchScope resourceScope = GlobalSearchScope.fileScope(project, root);

                Collection<VirtualFile> xmlFiles = FileBasedIndex.getInstance()
                        .getContainingFiles(FileTypeIndex.NAME, XmlFileType.INSTANCE, resourceScope);

                for (VirtualFile file : xmlFiles) {
                    if (!file.getName().endsWith(".xml")) continue;

                    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                    if (!(psiFile instanceof XmlFile xmlFile)) continue;

                    XmlTag rootTag = xmlFile.getRootTag();
                    if (rootTag == null || !"mapper".equals(rootTag.getName())) continue;

                    String namespace = rootTag.getAttributeValue("namespace");
                    if (expectedNamespace.equals(namespace)) {
                        String fullPath = file.getPath().replace("\\", "/");
                        int index = fullPath.indexOf("/resources/");
                        if (index >= 0) {
                            return fullPath.substring(index + "/resources/".length());
                        }
                    }
                }
            }
        }
        return null;
    }


    public String findRelativePathByNamespace(@NotNull String expectedNamespace) {
        String relativePathByNamespaces = findRelativePathByNamespaces(expectedNamespace);
        if (relativePathByNamespaces != null) {
            return relativePathByNamespaces;
        }
        return FileBasedIndex.getInstance()
                .getContainingFiles(FileTypeIndex.NAME, XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project))
                .stream()
                .filter(file -> file.getName().endsWith(".xml"))
                .filter(file -> {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                    if (!(psiFile instanceof XmlFile xmlFile)) return false;
                    XmlTag root = xmlFile.getRootTag();
                    if (root == null || !"mapper".equals(root.getName())) return false;
                    String namespace = root.getAttributeValue("namespace");
                    return expectedNamespace.equals(namespace);
                })
                .map(file -> {
                    String path = file.getPath();
                    int index = path.indexOf("/resources/");
                    if (index >= 0) {
                        return path.substring(index + "/resources/".length());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

}
