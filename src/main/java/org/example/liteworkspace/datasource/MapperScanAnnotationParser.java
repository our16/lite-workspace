package org.example.liteworkspace.datasource;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.GlobalSearchScope;
import org.example.liteworkspace.util.LogUtil;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapperScanAnnotationParser {

    public static List<SqlSessionConfig> parse(Project project) {
        LogUtil.info("MapperScanAnnotationParser.parse");
        List<SqlSessionConfig> result = new ArrayList<>();
        
        // 1. 遍历所有模块
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            // 2. 找到每个模块的 Java 源码根目录
            for (VirtualFile sourceRoot :
                    ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.SOURCE)) {

                // 3. 扫描源码下的 Java 类
                VfsUtil.iterateChildrenRecursively(sourceRoot, file -> true, file -> {
                    if (!file.isDirectory() && "java".equalsIgnoreCase(file.getExtension())) {
                        result.addAll(parseJavaFile(project, file));
                    }
                    return true;
                });
            }
        }
        return result;
    }

    private static List<SqlSessionConfig> parseJavaFile(Project project, VirtualFile javaFile) {
        List<SqlSessionConfig> result = new ArrayList<>();
        
        // 获取 PsiClass
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass psiClass = facade.findClass(
                getQualifiedName(javaFile), 
                GlobalSearchScope.allScope(project)
        );
        
        if (psiClass != null) {
            // 检查类是否有 @MapperScan 注解
            PsiAnnotation mapperScanAnnotation = findMapperScanAnnotation(psiClass);
            if (mapperScanAnnotation != null) {
                SqlSessionConfig config = parseMapperScanAnnotation(mapperScanAnnotation);
                if (config != null) {
                    result.add(config);
                }
            }
        }
        
        return result;
    }

    private static String getQualifiedName(VirtualFile javaFile) {
        // 将文件路径转换为全限定类名
        String path = javaFile.getPath();
        if (path.contains("/src/main/java/")) {
            String qualifiedName = path.substring(path.indexOf("/src/main/java/") + 15);
            return qualifiedName.replace(".java", "").replace("/", ".");
        }
        return null;
    }

    private static PsiAnnotation findMapperScanAnnotation(PsiClass psiClass) {
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList != null) {
            // 检查 MyBatis 的 @MapperScan 注解
            PsiAnnotation annotation = modifierList.findAnnotation("org.mybatis.spring.annotation.MapperScan");
            if (annotation != null) {
                return annotation;
            }
            
            // 检查 MyBatis-Plus 的 @MapperScan 注解
            annotation = modifierList.findAnnotation("com.baomidou.mybatisplus.annotation.MapperScan");
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    private static SqlSessionConfig parseMapperScanAnnotation(PsiAnnotation annotation) {
        SqlSessionConfig config = new SqlSessionConfig();
        config.setName("default");
        config.setSqlSessionFactoryBeanId("sqlSessionFactory");
        
        // 解析 basePackages 属性
        String basePackages = getAnnotationAttributeValue(annotation, "basePackages");
        if (basePackages != null && !basePackages.trim().isEmpty()) {
            // 支持多个包路径，使用逗号分隔
            String[] packages = basePackages.split(",");
            for (String pkg : packages) {
                config.getMapperBasePackages().add(pkg.trim());
            }
        }
        
        // 解析 value 属性（与 basePackages 等效）
        String value = getAnnotationAttributeValue(annotation, "value");
        if (value != null && !value.trim().isEmpty()) {
            // 支持多个包路径，使用逗号分隔
            String[] packages = value.split(",");
            for (String pkg : packages) {
                config.getMapperBasePackages().add(pkg.trim());
            }
        }
        
        // 解析 basePackageClasses 属性
        String basePackageClasses = getAnnotationAttributeValue(annotation, "basePackageClasses");
        if (basePackageClasses != null && !basePackageClasses.trim().isEmpty()) {
            // 支持多个类，使用逗号分隔
            String[] classes = basePackageClasses.split(",");
            for (String className : classes) {
                // 从类名中提取包名
                String pkg = extractPackageFromClassName(className.trim());
                if (pkg != null && !pkg.isEmpty()) {
                    config.getMapperBasePackages().add(pkg);
                }
            }
        }
        
        // 解析 sqlSessionFactoryRef 属性
        String sqlSessionFactoryRef = getAnnotationAttributeValue(annotation, "sqlSessionFactoryRef");
        if (sqlSessionFactoryRef != null && !sqlSessionFactoryRef.trim().isEmpty()) {
            config.setSqlSessionFactoryBeanId(sqlSessionFactoryRef);
        }
        
        // 解析 sqlSessionTemplateRef 属性
        String sqlSessionTemplateRef = getAnnotationAttributeValue(annotation, "sqlSessionTemplateRef");
        if (sqlSessionTemplateRef != null && !sqlSessionTemplateRef.trim().isEmpty()) {
            // 可以根据需要设置 sqlSessionTemplateRef
        }
        
        if (!config.getMapperBasePackages().isEmpty()) {
            return config;
        }
        
        return null;
    }

    private static String getAnnotationAttributeValue(PsiAnnotation annotation, String attributeName) {
        PsiAnnotationMemberValue attributeValue = annotation.findDeclaredAttributeValue(attributeName);
        if (attributeValue != null) {
            String text = attributeValue.getText();
            // 去除引号
            if (text.startsWith("\"") && text.endsWith("\"")) {
                return text.substring(1, text.length() - 1);
            }
            return text;
        }
        return null;
    }

    private static String extractPackageFromClassName(String className) {
        // 从类名中提取包名
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return className.substring(0, lastDotIndex);
        }
        return null;
    }
}