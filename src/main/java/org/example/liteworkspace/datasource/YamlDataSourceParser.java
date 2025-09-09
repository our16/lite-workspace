package org.example.liteworkspace.datasource;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class YamlDataSourceParser {

    public static List<SqlSessionConfig> parse(Project project) {
        List<SqlSessionConfig> result = new ArrayList<>();
        
        // 1. 遍历所有模块
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            // 2. 找到每个模块的 resource 根目录
            for (VirtualFile resourceRoot :
                    ModuleRootManager.getInstance(module).getSourceRoots(JavaResourceRootType.RESOURCE)) {

                // 3. 扫描 resource 下的 YAML 文件
                VfsUtil.iterateChildrenRecursively(resourceRoot, file -> true, file -> {
                    if (!file.isDirectory()) {
                        String name = file.getName();
                        if (name.startsWith("application") &&
                            (name.endsWith(".yml") || name.endsWith(".yaml"))) {
                            result.addAll(parseYamlFile(project, file));
                        }
                    }
                    return true;
                });
            }
        }
        return result;
    }

    private static List<SqlSessionConfig> parseYamlFile(Project project, VirtualFile yamlFile) {
        List<SqlSessionConfig> result = new ArrayList<>();
        
        try (InputStream inputStream = yamlFile.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> obj = yaml.load(inputStream);
            
            // 解析 MyBatis 配置
            parseMyBatisConfig(obj, result);
            
            // 解析 MyBatis-Plus 配置
            parseMyBatisPlusConfig(obj, result);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return result;
    }

    private static void parseMyBatisConfig(Map<String, Object> obj, List<SqlSessionConfig> result) {
        // 解析 mybatis 配置
        Map<String, Object> mybatis = (Map<String, Object>) obj.get("mybatis");
        if (mybatis != null) {
            SqlSessionConfig config = new SqlSessionConfig();
            config.setName("default");
            config.setSqlSessionFactoryBeanId("sqlSessionFactory");
            
            // 解析 mapper-locations
            Object mapperLocations = mybatis.get("mapper-locations");
            if (mapperLocations instanceof String) {
                config.getMapperLocations().add((String) mapperLocations);
            } else if (mapperLocations instanceof List) {
                for (Object location : (List<?>) mapperLocations) {
                    if (location instanceof String) {
                        config.getMapperLocations().add((String) location);
                    }
                }
            }
            
            // 解析 type-aliases-package
            Object typeAliasesPackage = mybatis.get("type-aliases-package");
            if (typeAliasesPackage instanceof String) {
                config.getMapperBasePackages().add((String) typeAliasesPackage);
            }
            
            if (!config.getMapperLocations().isEmpty() || !config.getMapperBasePackages().isEmpty()) {
                result.add(config);
            }
        }
    }

    private static void parseMyBatisPlusConfig(Map<String, Object> obj, List<SqlSessionConfig> result) {
        // 解析 mybatis-plus 配置
        Map<String, Object> mybatisPlus = (Map<String, Object>) obj.get("mybatis-plus");
        if (mybatisPlus != null) {
            SqlSessionConfig config = new SqlSessionConfig();
            config.setName("default");
            config.setSqlSessionFactoryBeanId("sqlSessionFactory");
            
            // 解析 mapper-locations
            Object mapperLocations = mybatisPlus.get("mapper-locations");
            if (mapperLocations instanceof String) {
                config.getMapperLocations().add((String) mapperLocations);
            } else if (mapperLocations instanceof List) {
                for (Object location : (List<?>) mapperLocations) {
                    if (location instanceof String) {
                        config.getMapperLocations().add((String) location);
                    }
                }
            }
            
            // 解析 type-aliases-package
            Object typeAliasesPackage = mybatisPlus.get("type-aliases-package");
            if (typeAliasesPackage instanceof String) {
                config.getMapperBasePackages().add((String) typeAliasesPackage);
            }
            
            if (!config.getMapperLocations().isEmpty() || !config.getMapperBasePackages().isEmpty()) {
                result.add(config);
            }
        }
    }
}
