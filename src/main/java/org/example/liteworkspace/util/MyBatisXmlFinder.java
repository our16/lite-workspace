package org.example.liteworkspace.util;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class MyBatisXmlFinder {

    private final Project project;

    public MyBatisXmlFinder(Project project) {
        this.project = project;
    }

    /**
     * 并行扫描所有模块和依赖库中的 Mapper XML
     */
    public Map<String, String> scanAllMapperXml() {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, modules.length));
        List<CompletableFuture<Map<String, String>>> futures = new ArrayList<>();

        for (Module module : modules) {
            futures.add(CompletableFuture.supplyAsync(() -> scanModule(module), pool));
        }

        // 等待并合并结果
        Map<String, String> result = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

        pool.shutdown();
        return result;
    }

    /**
     * 扫描单个 Module（源码 + 依赖库）
     */
    private Map<String, String> scanModule(Module module) {
        Map<String, String> result = new HashMap<>();
        // ------------------- 1️⃣ 扫描源码 -------------------
        GlobalSearchScope scope = GlobalSearchScope.moduleScope(module);
        // 当前项目的源码
        Collection<VirtualFile> xmlFiles = FileBasedIndex.getInstance()
                .getContainingFiles(FileTypeIndex.NAME, XmlFileType.INSTANCE, scope);

        for (VirtualFile file : xmlFiles) {
            processMapperFile(file, result, true);
        }

        // ------------------- 2️⃣ 扫描依赖库 JAR -------------------
        for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
            if (!(entry instanceof LibraryOrderEntry libEntry)) continue;
            for (VirtualFile root : libEntry.getRootFiles(OrderRootType.CLASSES)) {
                if (!root.isValid()) continue;

                VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<>() {
                    @Override
                    public boolean visitFile(@NotNull VirtualFile file) {
                        if (!file.isValid() || file.isDirectory()) {
                            return true;
                        }
                        String fName = file.getName();
                        if (!fName.endsWith(".xml")) {
                            return true;
                        }
                        processMapperFile(file, result, false);
                        return true;
                    }
                });
            }
        }

        return result;
    }

    /**
     * 解析并存储 Mapper XML 文件
     */
    private void processMapperFile(VirtualFile file, Map<String, String> result, boolean inModule) {
        String ns = ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (!(psiFile instanceof XmlFile xmlFile)) {
                return null;
            }
            XmlTag rootTag = xmlFile.getRootTag();
            if (rootTag == null || !"mapper".equals(rootTag.getName())) {
                return null;
            }
            return rootTag.getAttributeValue("namespace");
        });

        if (ns != null && !ns.isEmpty()) {
            String relPath = computeClassPath(file, inModule);
            result.put(ns, relPath);
        }
    }

    /**
     * 转换为 classpath 相对路径
     */
    private String computeClassPath(VirtualFile file, boolean inModule) {
        String path = file.getPath().replace('\\', '/');
        if (inModule) {
            int idx = path.indexOf("/resources/");
            if (idx != -1) {
                return path.substring(idx + "/resources/".length());
            }
        } else {
            // jar 内部用 jar! 路径
            String jarSep = ".jar!/";
            int idx = path.indexOf(jarSep);
            if (idx != -1) {
                return path.substring(idx + jarSep.length());
            }
        }
        return path; // fallback
    }
}
