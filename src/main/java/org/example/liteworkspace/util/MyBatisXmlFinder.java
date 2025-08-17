package org.example.liteworkspace.util;

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
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
     * 直接读取文件前几行提取 namespace，绕过 PSI
     */
    public Map<String, String> scanAllMapperXml() {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        int cpuNums = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(3, cpuNums));
        List<CompletableFuture<Map<String, String>>> futures = new ArrayList<>();
        for (Module module : modules) {
            futures.add(CompletableFuture.supplyAsync(() -> scanModule(module), pool));
        }

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

        // 1️⃣ 扫描源码目录
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
        for (VirtualFile root : roots) {
            if (!root.isValid()) {
                continue;
            }
            collectXmlFiles(root, result, true);
        }

        // 2️⃣ 扫描依赖库 JAR
        for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
            if (!(entry instanceof LibraryOrderEntry libEntry)) continue;
            // 遍历 JAR 内容
            for (VirtualFile root : libEntry.getRootFiles(OrderRootType.CLASSES)) {
                if (!root.isValid()) {
                    continue;
                }
                collectXmlFiles(root, result, false);
            }
        }


        return result;
    }

    /**
     * 遍历目录 / Jar 根，收集 Mapper XML 并提取 namespace
     */
    private void collectXmlFiles(VirtualFile root, Map<String, String> result, boolean inModule) {
        List<VirtualFile> xmlFiles = new ArrayList<>();
        VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (!file.isValid() || file.isDirectory()) {
                    return true;
                }
                String name = file.getName();
                if (name.endsWith(".xml")) {
                    xmlFiles.add(file);
                }
                return true;
            }
        });

        for (VirtualFile file : xmlFiles) {
            String ns = extractMapperNamespace(file);
            if (ns != null) {
                String relPath = computeClassPath(file, inModule);
                result.put(ns, relPath);
            }
        }
    }

    /**
     * 从文件前几行提取 <mapper namespace="...">
     */
    private String extractMapperNamespace(VirtualFile file) {
        try (InputStream is = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            StringBuilder sb = new StringBuilder();
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 20) {
                sb.append(line.trim()).append(" ");
                lineCount++;
                // 一旦拼凑出的字符串包含 <mapper
                if (sb.toString().contains("<mapper")) {
                    String mapperTag = sb.toString();
                    int idx = mapperTag.indexOf("namespace=");
                    if (idx != -1) {
                        int start = mapperTag.indexOf('"', idx);
                        int end = mapperTag.indexOf('"', start + 1);
                        if (start != -1 && end != -1) {
                            return mapperTag.substring(start + 1, end);
                        }
                    }
                    break; // 找到 <mapper> 就停止
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }


    /**
     * 转换为 classpath 相对路径
     */
    private String computeClassPath(VirtualFile file, boolean inModule) {
        String path = file.getPath().replace('\\', '/');
        if (inModule) {
            int idx = path.indexOf("/resources/");
            if (idx != -1) return path.substring(idx + "/resources/".length());
        } else {
            String jarSep = ".jar!/";
            int idx = path.indexOf(jarSep);
            if (idx != -1) return path.substring(idx + jarSep.length());
        }
        return path;
    }
}
