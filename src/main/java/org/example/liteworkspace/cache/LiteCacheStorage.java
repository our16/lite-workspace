package org.example.liteworkspace.cache;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class LiteCacheStorage {

    private final Path cacheDir;

    public LiteCacheStorage(Project project) {
        String projectId = DigestUtils.md5Hex(project.getBasePath());
        this.cacheDir = Paths.get(System.getProperty("user.home"), ".liteworkspace_cache", projectId);
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            throw new RuntimeException("创建缓存目录失败", e);
        }
    }

    public Map<String, PsiClass> loadConfigurationClasses() {
        // TODO: 读取 JSON/SQLite 文件转为 PsiClass 映射（可使用类名映射，实际使用时用 PSI 查询还原）
        return new HashMap<>();
    }

    public void saveConfigurationClasses(Map<String, PsiClass> cache) {
        // TODO: 序列化类名 + 文件路径
    }

    public Map<String, VirtualFile> loadMapperXmlPaths() {
        // TODO: 同上，缓存 namespace 到路径映射
        return new HashMap<>();
    }

    public void saveMapperXmlPaths(Map<String, VirtualFile> cache) {
        // TODO: 序列化 mapper namespace -> file path
    }
}

