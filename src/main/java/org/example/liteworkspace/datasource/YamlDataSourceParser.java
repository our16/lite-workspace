package org.example.liteworkspace.datasource;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.example.liteworkspace.util.LogUtil;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class YamlDataSourceParser {

    public static List<SqlSessionConfig> parse(Project project) {
        LogUtil.info("YamlDataSourceParser.parse");
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
            
            // 获取文件名，判断是否为多环境配置文件
            String fileName = yamlFile.getName();
            String env = extractEnvironmentFromFileName(fileName);
            
            // 解析 MyBatis 配置
            parseMyBatisConfig(obj, result, env);
            
            // 解析 MyBatis-Plus 配置
            parseMyBatisPlusConfig(obj, result, env);
            
            // 解析多环境特定配置
            if (env != null && !env.isEmpty()) {
                parseEnvironmentSpecificConfig(obj, result, env);
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return result;
    }

    private static void parseMyBatisConfig(Map<String, Object> obj, List<SqlSessionConfig> result, String env) {
        // 解析 mybatis 配置
        Map<String, Object> mybatis = (Map<String, Object>) obj.get("mybatis");
        if (mybatis != null) {
            SqlSessionConfig config = new SqlSessionConfig();
            config.setName(env != null && !env.isEmpty() ? env : "default");
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

    private static void parseMyBatisPlusConfig(Map<String, Object> obj, List<SqlSessionConfig> result, String env) {
        // 解析 mybatis-plus 配置
        Map<String, Object> mybatisPlus = (Map<String, Object>) obj.get("mybatis-plus");
        if (mybatisPlus != null) {
            SqlSessionConfig config = new SqlSessionConfig();
            config.setName(env != null && !env.isEmpty() ? env : "default");
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

    /**
     * 从文件名中提取环境名称
     * 例如：application-dev.yml -> dev
     *       application-prod.yaml -> prod
     *       application.yml -> null
     */
    private static String extractEnvironmentFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        
        // 去掉扩展名
        String nameWithoutExt = fileName;
        if (fileName.endsWith(".yml")) {
            nameWithoutExt = fileName.substring(0, fileName.length() - 4);
        } else if (fileName.endsWith(".yaml")) {
            nameWithoutExt = fileName.substring(0, fileName.length() - 5);
        }
        
        // 检查是否为 application-<env> 格式
        if (nameWithoutExt.startsWith("application-")) {
            return nameWithoutExt.substring("application-".length());
        }
        
        return null;
    }

    /**
     * 解析多环境特定配置
     * 例如：
     * spring:
     *   profiles:
     *     active: dev
     *   config:
     *     activate:
     *       on-profile: dev
     * ---
     * spring:
     *   profiles: dev
     * mybatis:
     *   mapper-locations: classpath:mapper/dev/*.xml
     */
    private static void parseEnvironmentSpecificConfig(Map<String, Object> obj, List<SqlSessionConfig> result, String env) {
        // 解析 spring.profiles 配置
        Map<String, Object> spring = (Map<String, Object>) obj.get("spring");
        if (spring != null) {
            // 检查是否有 profiles 配置
            Object profiles = spring.get("profiles");
            if (profiles instanceof String && env.equals(profiles)) {
                // 解析当前环境下的 MyBatis 配置
                parseMyBatisConfig(obj, result, env);
                parseMyBatisPlusConfig(obj, result, env);
            } else if (profiles instanceof List) {
                // 检查环境是否在 profiles 列表中
                for (Object profile : (List<?>) profiles) {
                    if (env.equals(profile)) {
                        // 解析当前环境下的 MyBatis 配置
                        parseMyBatisConfig(obj, result, env);
                        parseMyBatisPlusConfig(obj, result, env);
                        break;
                    }
                }
            }
            
            // 检查是否有 config.activate.on-profile 配置（Spring Boot 2.4+）
            Map<String, Object> config = (Map<String, Object>) spring.get("config");
            if (config != null) {
                Map<String, Object> activate = (Map<String, Object>) config.get("activate");
                if (activate != null) {
                    Object onProfile = activate.get("on-profile");
                    if (env.equals(onProfile)) {
                        // 解析当前环境下的 MyBatis 配置
                        parseMyBatisConfig(obj, result, env);
                        parseMyBatisPlusConfig(obj, result, env);
                    }
                }
            }
        }
    }
}
