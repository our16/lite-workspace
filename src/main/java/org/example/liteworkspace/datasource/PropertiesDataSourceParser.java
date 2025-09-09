package org.example.liteworkspace.datasource;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PropertiesDataSourceParser {

    public static List<SqlSessionConfig> parse(Project project) {
        List<SqlSessionConfig> result = new ArrayList<>();
        
        // 1. 遍历所有模块
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            // 2. 找到每个模块的 resource 根目录
            for (VirtualFile resourceRoot :
                    ModuleRootManager.getInstance(module).getSourceRoots(JavaResourceRootType.RESOURCE)) {

                // 3. 扫描 resource 下的 properties 文件
                VfsUtil.iterateChildrenRecursively(resourceRoot, file -> true, file -> {
                    if (!file.isDirectory()) {
                        String name = file.getName();
                        if (name.startsWith("application") && name.endsWith(".properties")) {
                            result.addAll(parsePropertiesFile(project, file));
                        }
                    }
                    return true;
                });
            }
        }
        return result;
    }

    private static List<SqlSessionConfig> parsePropertiesFile(Project project, VirtualFile propertiesFile) {
        List<SqlSessionConfig> result = new ArrayList<>();
        
        try (InputStream inputStream = propertiesFile.getInputStream()) {
            Properties props = new Properties();
            props.load(inputStream);
            
            // 解析 MyBatis 配置
            parseMyBatisConfig(props, result);
            
            // 解析 MyBatis-Plus 配置
            parseMyBatisPlusConfig(props, result);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return result;
    }

    private static void parseMyBatisConfig(Properties props, List<SqlSessionConfig> result) {
        SqlSessionConfig config = new SqlSessionConfig();
        config.setName("default");
        config.setSqlSessionFactoryBeanId("sqlSessionFactory");
        
        // 解析 mapper-locations
        String mapperLocations = props.getProperty("mybatis.mapper-locations");
        if (mapperLocations != null && !mapperLocations.trim().isEmpty()) {
            // 支持多个位置，使用逗号分隔
            String[] locations = mapperLocations.split(",");
            for (String location : locations) {
                config.getMapperLocations().add(location.trim());
            }
        }
        
        // 解析 type-aliases-package
        String typeAliasesPackage = props.getProperty("mybatis.type-aliases-package");
        if (typeAliasesPackage != null && !typeAliasesPackage.trim().isEmpty()) {
            config.getMapperBasePackages().add(typeAliasesPackage);
        }
        
        if (!config.getMapperLocations().isEmpty() || !config.getMapperBasePackages().isEmpty()) {
            result.add(config);
        }
    }

    private static void parseMyBatisPlusConfig(Properties props, List<SqlSessionConfig> result) {
        SqlSessionConfig config = new SqlSessionConfig();
        config.setName("default");
        config.setSqlSessionFactoryBeanId("sqlSessionFactory");
        
        // 解析 mapper-locations
        String mapperLocations = props.getProperty("mybatis-plus.mapper-locations");
        if (mapperLocations != null && !mapperLocations.trim().isEmpty()) {
            // 支持多个位置，使用逗号分隔
            String[] locations = mapperLocations.split(",");
            for (String location : locations) {
                config.getMapperLocations().add(location.trim());
            }
        }
        
        // 解析 type-aliases-package
        String typeAliasesPackage = props.getProperty("mybatis-plus.type-aliases-package");
        if (typeAliasesPackage != null && !typeAliasesPackage.trim().isEmpty()) {
            config.getMapperBasePackages().add(typeAliasesPackage);
        }
        
        if (!config.getMapperLocations().isEmpty() || !config.getMapperBasePackages().isEmpty()) {
            result.add(config);
        }
    }
}