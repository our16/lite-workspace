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
import org.example.liteworkspace.datasource.SqlSessionConfig;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class MyBatisXmlFinder {

    private final Project project;

    public MyBatisXmlFinder(Project project) {
        this.project = project;
    }

    /**
     * 按 SqlSession 配置扫描源码 + 依赖库，返回 Map<namespace, mapper相对路径>
     */
    public Map<String, MybatisBeanDto> scanAllMapperXml(List<SqlSessionConfig> configs) {
        Map<String, MybatisBeanDto> result = new HashMap<>();

        Module[] modules = ModuleManager.getInstance(project).getModules();
        int cpuNums = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(1, cpuNums));

        List<CompletableFuture<Map<String, MybatisBeanDto>>> futures = new ArrayList<>();

        for (Module module : modules) {
            for (SqlSessionConfig cfg : configs) {
                futures.add(CompletableFuture.supplyAsync(() -> scanModuleForConfig(module, cfg), pool));
            }
        }

        for (CompletableFuture<Map<String, MybatisBeanDto>> future : futures) {
            result.putAll(future.join());
        }

        pool.shutdown();
        return result;
    }

    /**
     * 扫描模块对应 SqlSessionConfig
     */
    private Map<String, MybatisBeanDto> scanModuleForConfig(Module module, SqlSessionConfig cfg) {
        Map<String, MybatisBeanDto> result = new HashMap<>();
        // xml 文件

        // 1️⃣ 扫描源码
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
        for (VirtualFile root : roots) {
            if (!root.isValid()) continue;
            collectXmlFiles(root, cfg, result, true);
        }

        // 2️⃣ 扫描依赖库
        for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
            if (!(entry instanceof LibraryOrderEntry libEntry)) continue;
            for (VirtualFile root : libEntry.getRootFiles(OrderRootType.CLASSES)) {
                if (!root.isValid()) continue;
                collectXmlFiles(root, cfg, result, false);
            }
        }

        return result;
    }

    /**
     * 遍历目录 / Jar 根，收集符合 basePackages 或 mapperLocations 的 Mapper XML
     */
    private void collectXmlFiles(VirtualFile root, SqlSessionConfig cfg,
                                 Map<String, MybatisBeanDto> result, boolean inModule) {
        List<String> mapperLocations = cfg.getMapperLocations();
        VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (!file.isValid() || file.isDirectory()) {
                    return true;
                }
                String relativePath = computeClassPath(file, inModule);
                // 只处理 XML 文件
                if (!file.getName().endsWith(".xml")) {
                    return true;
                }

                if (matchesMapperLocation(relativePath, mapperLocations)) {
                    String ns = extractMapperNamespace(file);
                    if (ns != null) {
                        result.put(ns,  new MybatisBeanDto(ns, relativePath, cfg.getSqlSessionFactoryBeanId()));
                    }
                }
                return true;
            }
        });
    }


    private boolean matchesMapperLocation(String absolutePath, List<String> mapperLocations) {
        // 统一分隔符
        String normalizedPath = absolutePath.replace("\\", "/");

        for (String loc : mapperLocations) {
            // 去掉 classpath 前缀
            String clean = loc.replace("classpath*:", "")
                    .replace("classpath:", "")
                    .replace("\\", "/");

            // 确保以 / 开头，方便 glob 匹配
            if (!clean.startsWith("/")) {
                clean = "/" + clean;
            }

            // 处理 glob 通配符
            String globPattern = "glob:" + clean;

            PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globPattern);

            // 用 Paths.get 转换 normalizedPath 便于匹配
            String pathForMatch = normalizedPath.startsWith("/") ? normalizedPath : "/" + normalizedPath;

            if (matcher.matches(Paths.get(pathForMatch))) {
                return true;
            }
        }
        return false;
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
                    break;
                }
            }
        } catch (Exception ignored) {
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
