package org.example.liteworkspace.cache;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.liteworkspace.bean.core.BeanDefinition;
import org.example.liteworkspace.bean.core.DatasourceConfig;
import org.example.liteworkspace.datasource.SqlSessionConfig;
import org.example.liteworkspace.util.MybatisBeanDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    // ===================== Spring Configuration 类缓存 =====================

    /**
     * 保存 Spring @Configuration 类：类全名 -> 文件路径
     */
    public void saveConfigurationClasses(Map<String, PsiClass> configClasses) {
        Map<String, String> classToPath = configClasses.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, // FQCN
                        e -> e.getValue().getContainingFile().getVirtualFile().getPath() // 文件路径
                ));
        saveJson("configuration_classes.json", classToPath);
    }

    /**
     * 加载 Spring @Configuration 类映射（类名 -> 文件路径）
     */
    public Map<String, String> loadConfigurationClasses() {
        return loadJson("configuration_classes.json", Map.class);
    }

    // ===================== MyBatis Mapper XML 缓存 =====================

    /**
     * 保存 Mapper namespace -> Mapper XML 文件路径
     */
    public void saveMapperXmlPaths(Map<String, MybatisBeanDto> namespaceToPath) {
        saveJson("mapper_xml_paths.json", namespaceToPath);
    }

    /**
     * 加载 Mapper namespace -> 文件路径
     */
    public Map<String, String> loadMapperXmlPaths() {
        return loadJson("mapper_xml_paths.json", Map.class);
    }

    // ===================== 通用 JSON 存取方法 =====================

    private <T> void saveJson(String filename, T data) {
        try {
            Path filePath = cacheDir.resolve(filename);
            String json = GsonProvider.gson.toJson(data);
            Files.writeString(filePath, json);
        } catch (IOException e) {
            throw new RuntimeException("保存缓存失败: " + filename, e);
        }
    }

    private <T> T loadJson(String filename, Class<T> type) {
        Path filePath = cacheDir.resolve(filename);
        if (!Files.exists(filePath)) {
            return null;
        }
        try {
            String json = Files.readString(filePath);
            return GsonProvider.gson.fromJson(json, type);
        } catch (IOException e) {
            throw new RuntimeException("加载缓存失败: " + filename, e);
        }
    }

    // ===================== 可选：保存其他配置 =====================

    public void saveDatasourceConfig(DatasourceConfig config) {
        saveJson("datasource_config.json", config);
    }

    public SqlSessionConfig loadDatasourceConfig() {
        return loadJson("datasource_config.json", SqlSessionConfig.class);
    }

    public void saveSpringScanPackages(Set<String> packages) {
        saveJson("spring_scan_packages.json", packages);
    }

    public Set<String> loadSpringScanPackages() {
        Set<String> result = loadJson("spring_scan_packages.json", Set.class);
        return result != null ? result : Set.of();
    }

    public void saveBeanList(Collection<BeanDefinition> beans) {
        Set<String> beanList = beans.stream().map(BeanDefinition::getSource)
                .map(item -> item.getContainingFile().getVirtualFile().getPath()).collect(Collectors.toSet());
        saveJson("bean_classes.json", beanList);
    }

    public Set<String> loadJavaPaths() {
        Set<String> result = loadJson("bean_classes.json", Set.class);
        return result != null ? result : Set.of();
    }

}