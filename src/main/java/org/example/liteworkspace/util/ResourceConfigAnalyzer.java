package org.example.liteworkspace.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.example.liteworkspace.bean.core.DatasourceConfig;
import org.example.liteworkspace.bean.core.ProjectCacheStore;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

public class ResourceConfigAnalyzer {

    private final Project project;
    private final ProjectCacheStore cacheStore;

    public ResourceConfigAnalyzer(Project project, String projectId) {
        this.project = project;
        this.cacheStore = new ProjectCacheStore(projectId);
    }

    public Set<String> scanComponentScanPackages() {
        Set<String> result = new HashSet<>();
        for (XmlFile xml : findRelevantXmlFiles()) {
            scanXmlForComponentScan(xml, result, new HashSet<>());
        }
        return result;
    }

    public Map<String, PsiClass> scanConfigurationClasses() {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Map<String, PsiClass> beanToConfiguration = new HashMap<>();
        for (VirtualFile vf : FilenameIndex.getAllFilesByExt(project, "java", scope)) {
            PsiFile file = PsiManager.getInstance(project).findFile(vf);
            if (!(file instanceof PsiJavaFile javaFile)) {
                continue;
            }
            for (PsiClass clazz : javaFile.getClasses()) {
                if (!clazz.hasAnnotation("org.springframework.context.annotation.Configuration")) {
                    continue;
                }

                for (PsiMethod method : clazz.getMethods()) {
                    if (!method.hasAnnotation("org.springframework.context.annotation.Bean")) {
                        continue;
                    }

                    PsiType returnType = method.getReturnType();
                    if (returnType == null) {
                        continue;
                    }

                    // 获取Bean名称（优先使用@Bean的name/value属性，否则使用方法名）
                    String beanName = getBeanName(method);

                    // 获取返回类型的完全限定名
                    String returnTypeName = returnType.getCanonicalText();

                    // 两种存储方式：
                    // 1. 以Bean方法名为key
                    beanToConfiguration.put(method.getName(), clazz);
                    // 2. 以返回类型为key
                    beanToConfiguration.put(returnTypeName, clazz);
                    // 3. 如果有自定义Bean名称，也存储
                    if (!beanName.equals(method.getName())) {
                        beanToConfiguration.put(beanName, clazz);
                    }
                }
            }
        }

        return beanToConfiguration;
    }

    /**
     * 从@Bean注解中提取Bean名称
     */
    private String getBeanName(PsiMethod method) {
        PsiAnnotation beanAnnotation = method.getAnnotation("org.springframework.context.annotation.Bean");
        if (beanAnnotation == null) {
            return method.getName();
        }

        // 查找@Bean的value或name属性
        PsiAnnotationMemberValue valueAttr = beanAnnotation.findAttributeValue("value");
        if (valueAttr == null) {
            valueAttr = beanAnnotation.findAttributeValue("name");
        }

        if (valueAttr instanceof PsiLiteralExpression literal) {
            String value = literal.getValue() instanceof String ? (String) literal.getValue() : null;
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        // 默认返回方法名
        return method.getName();
    }

    /**
     * 只读取 test/resource/configs/testDatasource.xml 的数据库配置，
     * 如果文件不存在，则返回默认配置
     *
     * @return Map<String, String> 数据源配置
     */
    public DatasourceConfig scanSpringDatasourceConfigs() {
        // 1. 优先检查是否有指定的测试数据源文件
        VirtualFile configFile = findTestDatasourceXml();
        if (configFile != null && configFile.isValid()) {
            // 如果找到指定文件，返回一个特殊标识表示使用导入文件
            return DatasourceConfig.createImportedConfig(configFile.getPath());
        }
        // 2. 如果没有找到文件，返回默认配置
        return DatasourceConfig.createDefaultConfig(
                "jdbc:mysql://localhost:3306/default_db",
                "root",
                "123456",
                "com.mysql.cj.jdbc.Driver"
        );
    }

    /**
     * 查找 test/resources/configs/testDatasource.xml 文件
     */
    private VirtualFile findTestDatasourceXml() {
        // 尝试的多个可能路径（根据常见项目结构）
        String[] possiblePaths = {
                "src/test/resources/configs/testDatasource.xml",
                "test/resources/configs/testDatasource.xml",
                "configs/testDatasource.xml",
        };

        // 1. 首先尝试基于项目根目录查找
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null) {
            for (String path : possiblePaths) {
                VirtualFile file = baseDir.findFileByRelativePath(path);
                if (file != null && file.exists()) {
                    return file;
                }
            }
        }

        // 2. 使用FilenameIndex全局搜索（更可靠的方式）
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
                "testDatasource.xml",
                scope
        );

        for (VirtualFile file : files) {
            if (file.getPath().contains("configs")) {
                return file;
            }
        }

        // 3. 最后尝试通过类加载器查找资源
        try {
            URL resourceUrl = getClass().getClassLoader()
                    .getResource("configs/testDatasource.xml");
            if (resourceUrl != null) {
                return VirtualFileManager.getInstance()
                        .findFileByUrl(VfsUtilCore.urlToPath(resourceUrl.toString()));
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * 扫描 XML 文件中的 <bean class="...DataSource"> 以及 <property name="url" .../> 配置
     */
    private void scanDatasourceXmlConfig(VirtualFile xmlFile, Map<String, String> configs) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(xmlFile);
        if (!(psiFile instanceof XmlFile xml)) {
            return;
        }

        XmlTag root = xml.getRootTag();
        if (root == null) return;

        for (XmlTag beanTag : root.findSubTags("bean")) {
            String className = beanTag.getAttributeValue("class");
            if (className == null || !className.toLowerCase().contains("datasource")) {
                continue;
            }

            for (XmlTag propTag : beanTag.findSubTags("property")) {
                String name = propTag.getAttributeValue("name");
                String value = propTag.getAttributeValue("value");
                if (name != null && value != null) {
                    configs.put("datasource." + name, value); // 统一前缀
                }
            }
        }
    }

    private List<XmlFile> findRelevantXmlFiles() {
        List<XmlFile> xmlFiles = new ArrayList<>();
        for (VirtualFile vf : FilenameIndex.getAllFilesByExt(project, "xml", GlobalSearchScope.projectScope(project))) {
            if (!vf.getPath().contains("/resources/")) continue;

            PsiFile psi = PsiManager.getInstance(project).findFile(vf);
            if (psi instanceof XmlFile xml) {
                XmlTag root = xml.getRootTag();
                if (root != null && ("beans".equals(root.getName()) || "context:component-scan".equals(root.getName()))) {
                    xmlFiles.add(xml);
                }
            }
        }
        return xmlFiles;
    }

    private void scanXmlForComponentScan(XmlFile xml, Set<String> scanPackages, Set<String> visitedPaths) {
        String path = xml.getVirtualFile().getPath();
        if (!visitedPaths.add(path)) return;

        XmlTag root = xml.getRootTag();
        if (root == null) return;

        for (XmlTag tag : root.findSubTags("context:component-scan")) {
            String basePackage = tag.getAttributeValue("base-package");
            if (basePackage != null) scanPackages.add(basePackage);
        }

        for (XmlTag tag : root.findSubTags("import")) {
            String resource = tag.getAttributeValue("resource");
            if (resource != null) {
                VirtualFile imported = resolveResourcePath(resource, xml.getVirtualFile());
                if (imported != null) {
                    PsiFile psi = PsiManager.getInstance(project).findFile(imported);
                    if (psi instanceof XmlFile importedXml) {
                        scanXmlForComponentScan(importedXml, scanPackages, visitedPaths);
                    }
                }
            }
        }
    }

    @Nullable
    private VirtualFile resolveResourcePath(String path, VirtualFile base) {
        VirtualFile baseDir = base.getParent();
        if (path.startsWith("classpath:")) path = path.replace("classpath:", "");
        VirtualFile resolved = baseDir.findFileByRelativePath(path);
        if (resolved == null) {
            resolved = base.getFileSystem().findFileByPath(project.getBasePath() + "/src/main/resources/" + path);
        }
        return resolved;
    }
}
