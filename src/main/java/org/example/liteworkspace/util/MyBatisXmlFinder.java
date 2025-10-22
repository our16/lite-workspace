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
import java.util.*;
import java.util.concurrent.*;

public class MyBatisXmlFinder {

    private final Project project;

    public MyBatisXmlFinder(Project project) {
        this.project = project;
    }

    /**
     * 按 SqlSession 配置扫描源码 + 依赖库，返回 Map<namespace, mapper相对路径>
     */
    public Map<String, MybatisBeanDto> scanAllMapperXml(List<SqlSessionConfig> configs) {
        try {
            Map<String, MybatisBeanDto> result = new HashMap<>();

            Module[] modules = ModuleManager.getInstance(project).getModules();
            int cpuNums = Runtime.getRuntime().availableProcessors();
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(1, cpuNums));
            LogUtil.info("modules :{}", modules.length);
            LogUtil.info("list:{}", JSONUtil.toJsonStr(modules));
            List<CompletableFuture<Map<String, MybatisBeanDto>>> futures = new ArrayList<>();
            for (Module module : modules) {
                for (SqlSessionConfig cfg : configs) {
                    futures.add(CompletableFuture.supplyAsync(() -> scanModuleForConfig(module, cfg), pool));
                }
            }

            for (CompletableFuture<Map<String, MybatisBeanDto>> future : futures) {
                result.putAll(future.join());
            }
            LogUtil.info("mybatis sqlSession collect result:{}", JSONUtil.toJsonStr(result));
            pool.shutdown();
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
            if (!root.isValid()) {
                continue;
            }
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
                        result.put(ns, new MybatisBeanDto(ns, relativePath, cfg.getSqlSessionFactoryBeanId()));
                    }
                }
                return true;
            }
        });
    }


    /**
     * 判断一个resource目录下的相对路径是否匹配mapperLocations
     * 支持：
     * - classpath:mapper/*.xml
     * - classpath*:mapper/**\/*.xml
     */
    public static boolean matchesMapperLocation(String relativePath, List<String> mapperLocations) {
        // 统一分隔符
        String normalizedPath = relativePath.replace("\\", "/");
        normalizedPath = normalizedPath.replaceFirst("^[a-zA-Z]:/", ""); // 去掉盘符

        for (String loc : mapperLocations) {
            // 去掉 classpath 前缀
            String clean = loc.replace("classpath*:", "")
                    .replace("classpath:", "")
                    .replace("\\", "/")
                    .replaceFirst("^/", "");

            // 转换 glob 到 regex
            String regex = globToRegex(clean);

            if (normalizedPath.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 简单 glob -> regex 转换
     * - ** -> .*      (跨目录)
     * - *  -> [^/]*   (单个目录或文件名)
     */
    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        char[] chars = glob.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '*') {
                // 双星号
                if (i + 1 < chars.length && chars[i + 1] == '*') {
                    sb.append(".*");
                    i++; // 跳过下一个 *
                } else {
                    sb.append("[^/]*");
                }
            } else if (c == '.') {
                sb.append("\\."); // 转义 .
            } else {
                sb.append(c);
            }
        }
        return "^.*" + sb.toString() + "$"; // 前缀允许任意路径
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
